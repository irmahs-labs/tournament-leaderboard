import { DOCUMENT } from "@angular/common";
import { HttpClient, httpResource } from "@angular/common/http";
import {
  afterNextRender,
  Component,
  computed,
  Injector,
  inject,
  input,
  linkedSignal,
} from "@angular/core";
import { Router } from "@angular/router";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import { firstValueFrom } from "rxjs";

import type { MatchCard, Rules, Stat } from "../../results/api";
import { CurrentSeason } from "../../results/current-season";
import { LanguageService } from "../../shared/language";
import { splitRiotId } from "../../shared/riot-id";
import { AdminAction } from "../action";
import type { AdminPlayer } from "../api";
import type { GameScores, PreviousRoster, Problem, Row } from "./scores-form";
import {
  emptyRow,
  isWhole,
  linesOf,
  parseStat,
  problemOf,
  rowFrom,
  statsOf,
} from "./scores-form";

/** A positive whole number from the address, or nothing. */
const positive = (value: string | undefined): number | null => {
  const number = Number(value);

  return Number.isInteger(number) && number > 0 ? number : null;
};

/** The stats typed with decimals. */
const DECIMAL: ReadonlySet<Stat> = new Set([
  "ADR",
  "DAMAGE_DELTA",
  "KAST_PCT",
  "HEADSHOT_PCT",
]);

/** Whether a stat box holds something that isn't a valid value. */
const isInvalid = (row: Row, stat: Stat): boolean =>
  parseStat(stat, row.stats[stat]) === "invalid";

const hasDecimals = (stat: Stat): boolean => DECIMAL.has(stat);

/** The numbers 1 to `count`, for a picker. */
const upTo = (count: number): number[] =>
  Array.from({ length: Math.max(count, 0) }, (_, index) => index + 1);

/** The picks kept in the address: `?season=&match=&game=`; nothing removes one. */
interface Selection {
  season?: string;
  match: string | number | null;
  game: string | null;
}

/**
 * Enter scores: one game at a time. Pick the season, match and game (kept in the address), type
 * each player's stats, tick the wins and manual bonuses, check the automatic ones, and save. A new
 * game starts with the previous game's players. The leaderboard follows as soon as it's saved.
 */
@Component({
  imports: [TranslocoDirective],
  selector: "app-scores-admin",
  styleUrl: "./scores.css",
  templateUrl: "./scores.html",
})
export class ScoresAdminPage {
  /** `?match=` in the address; the next match to enter otherwise. */
  readonly match = input<string>();

  /** `?game=` in the address; the match's next game otherwise. */
  readonly game = input<string>();

  protected readonly current = inject(CurrentSeason);
  protected readonly language = inject(LanguageService);

  protected readonly rules = httpResource<Rules>(() => this.#season("rules"));
  protected readonly cards = httpResource<MatchCard[]>(() =>
    this.#season("matches")
  );
  readonly #players = httpResource<AdminPlayer[]>(() => {
    const admin = this.#admin();

    return admin ? `${admin}/players` : undefined;
  });

  /** The stats the season records, as columns. */
  protected readonly stats = computed<Stat[]>(() =>
    this.rules.hasValue()
      ? this.rules
          .value()
          .stats.filter((rule) => rule.recorded)
          .map((rule) => rule.stat)
      : []
  );

  /** The stats the season doesn't record, named under the table. */
  protected readonly notRecorded = computed<Stat[]>(() =>
    this.rules.hasValue()
      ? this.rules
          .value()
          .stats.filter((rule) => !rule.recorded)
          .map((rule) => rule.stat)
      : []
  );

  protected readonly bonuses = computed(() =>
    this.rules.hasValue() ? this.rules.value().bonuses : []
  );

  protected readonly manualCodes = computed(
    () =>
      new Set(
        this.bonuses()
          .filter((bonus) => bonus.award === "MANUAL")
          .map((bonus) => bonus.code)
      )
  );

  /** Everyone who can be picked for a line, by Riot ID as the API sorts them. */
  protected readonly players = computed(() =>
    this.#players.hasValue() ? this.#players.value() : []
  );

  protected readonly matchNumbers = computed(() =>
    upTo(this.current.season()?.matchCount ?? 0)
  );

  protected readonly gameNumbers = computed(() =>
    upTo(this.current.season()?.maxGames ?? 0)
  );

  /** The match on screen: the address's, else the first one without scores, else the last. */
  protected readonly matchNumber = computed<number | null>(() => {
    const asked = positive(this.match());
    const count = this.current.season()?.matchCount ?? 0;

    if (asked && asked <= count) {
      return asked;
    }

    if (!this.cards.hasValue()) {
      return null;
    }

    const cards = this.cards.value();

    return (
      cards.find((card) => card.games === 0)?.matchNumber ??
      cards.at(-1)?.matchNumber ??
      (count > 0 ? 1 : null)
    );
  });

  /** The game on screen: the address's, else the match's next one. */
  protected readonly gameNumber = computed<number | null>(() => {
    const max = this.current.season()?.maxGames ?? 0;
    const asked = positive(this.game());

    if (asked && asked <= max) {
      return asked;
    }

    const match = this.matchNumber();

    if (match === null || max === 0) {
      return null;
    }

    const played = this.cards.hasValue()
      ? (this.cards.value().find((card) => card.matchNumber === match)?.games ??
        0)
      : 0;

    return Math.min(played + 1, max);
  });

  readonly #gameApi = computed(() => {
    const admin = this.#admin();
    const season = this.current.season();
    const match = this.matchNumber();
    const game = this.gameNumber();

    return admin && season && match && game
      ? `${admin}/seasons/${season.number}/matches/${match}/games/${game}`
      : undefined;
  });

  protected readonly loaded = httpResource<GameScores>(() => this.#gameApi());

  /** For a game not saved yet: the players of the game before it. */
  protected readonly roster = httpResource<PreviousRoster>(() => {
    const api = this.#gameApi();

    return api && this.loaded.hasValue() && !this.loaded.value().saved
      ? `${api}/previous-roster`
      : undefined;
  });

  /** The form's lines: the saved ones, or the previous game's players with empty stats. */
  protected readonly rows = linkedSignal<Row[]>(() => this.#startingRows());

  /** The match's day, as YYYY-MM-DD. */
  protected readonly matchDate = linkedSignal(() =>
    this.loaded.hasValue() ? (this.loaded.value().matchDate ?? "") : ""
  );

  /** Whether the form has changes that aren't saved. Clears whenever a game loads. */
  protected readonly dirty = linkedSignal(() => {
    // Read so that each game loaded starts clean; value() would throw on an error.
    this.loaded.status();

    return false;
  });

  protected readonly action = new AdminAction((status) =>
    this.#transloco.translate(
      status === 400 ? "adminScores.refused" : "adminScores.failed"
    )
  );

  protected readonly busy = this.action.busy;
  protected readonly outcome = this.action.outcome;

  /** Where the form stands, for the status pill. */
  protected readonly status = computed(() => {
    if (this.dirty()) {
      return "unsaved";
    }

    return this.loaded.hasValue() && this.loaded.value().saved
      ? "saved"
      : "notEntered";
  });

  /** Riot IDs by player, to name the lines. */
  readonly #riotIds = computed(
    () => new Map(this.players().map((player) => [player.id, player.riotId]))
  );

  /** A line's player, for the labels of its boxes: "Kills, Kestrel". */
  protected nameOf(row: Row, index: number): string {
    if (row.player.kind === "known") {
      const riotId = this.#riotIds().get(row.player.playerId);

      if (riotId) {
        return splitRiotId(riotId).name;
      }
    }

    if (row.player.kind === "new" && row.player.riotId.trim()) {
      return splitRiotId(row.player.riotId.trim()).name;
    }

    return this.#transloco.translate("adminScores.line", { line: index + 1 });
  }

  protected readonly invalid = isInvalid;

  /** Stats with decimals get a keyboard with a decimal point on phones. */
  protected readonly decimals = hasDecimals;

  readonly #http = inject(HttpClient);
  readonly #router = inject(Router);
  readonly #transloco = inject(TranslocoService);
  readonly #injector = inject(Injector);
  readonly #window = inject(DOCUMENT).defaultView;

  readonly #admin = this.current.adminApi;

  /** "Performance and Eco", in the language on screen; read again when it changes. */
  protected notRecordedNames(): string {
    return this.notRecorded()
      .map((stat) => this.#transloco.translate(`stats.long.${stat}`))
      .join(", ");
  }

  /** "FB (Most first bloods), LD (Fewest deaths)". */
  protected bonusLegend(): string {
    return this.bonuses()
      .map((bonus) => `${bonus.code} (${bonus.label})`)
      .join(", ");
  }

  /** Shows another season, match or game, after asking before unsaved changes are lost. */
  protected pick(
    what: "season" | "match" | "game",
    value: string,
    picker: HTMLSelectElement
  ): void {
    if (!this.#mayLeave()) {
      // Puts the picker back on what's still on screen.
      picker.value = String(
        {
          game: this.gameNumber(),
          match: this.matchNumber(),
          season: this.current.season()?.number,
        }[what] ?? ""
      );

      return;
    }

    this.outcome.set(null);

    void this.#router.navigate([], {
      queryParams: this.#selection(what, value),
      queryParamsHandling: "merge",
    });
  }

  /** Changes one line, marking the form as changed. */
  protected edit(key: number, change: (row: Row) => Row): void {
    this.rows.update((rows) =>
      rows.map((row) =>
        row.key === key ? { ...change(row), points: null } : row
      )
    );
    this.dirty.set(true);
  }

  protected pickPlayer(key: number, playerId: string): void {
    this.edit(key, (row) => ({
      ...row,
      player: playerId ? { kind: "known", playerId } : { kind: "none" },
    }));
  }

  protected typeRiotId(key: number, riotId: string): void {
    this.edit(key, (row) => ({ ...row, player: { kind: "new", riotId } }));
  }

  /** A typed stat. The automatic bonuses may no longer hold, so they go until checked again. */
  protected typeStat(key: number, stat: Stat, value: string): void {
    const manual = this.manualCodes();
    this.rows.update((rows) =>
      rows.map((row) => ({
        ...row,
        bonuses: row.bonuses.filter((code) => manual.has(code)),
        points: null,
        stats: row.key === key ? { ...row.stats, [stat]: value } : row.stats,
      }))
    );
    this.dirty.set(true);
  }

  protected toggleWin(key: number, win: boolean): void {
    this.edit(key, (row) => ({ ...row, win }));
  }

  protected toggleBonus(key: number, code: string, won: boolean): void {
    this.edit(key, (row) => ({
      ...row,
      bonuses: won
        ? [...row.bonuses, code]
        : row.bonuses.filter((each) => each !== code),
    }));
  }

  protected setDate(value: string): void {
    this.matchDate.set(value);
    this.dirty.set(true);
  }

  /** Adds a line for a player who isn't on the list yet, and puts the cursor in it. */
  protected addNewPlayer(): void {
    const row = emptyRow({ kind: "new", riotId: "" });
    this.rows.update((rows) => [...rows, row]);
    this.dirty.set(true);
    afterNextRender(
      () => {
        this.#window?.document
          .querySelector<HTMLInputElement>(`#riot-${row.key}`)
          ?.focus();
      },
      { injector: this.#injector }
    );
  }

  /** Asks the server which automatic bonuses each line would win, without saving. */
  protected async checkBonuses(): Promise<void> {
    const admin = this.#admin();
    const season = this.current.season();

    const used = this.rows().flatMap((row, index) =>
      row.player.kind === "none" ? [] : [{ line: index + 1, row }]
    );

    if (!admin || !season || used.length === 0) {
      return;
    }

    const stats = this.stats();

    await this.action.run(async () => {
      const won = await firstValueFrom(
        this.#http.post<string[][]>(
          `${admin}/seasons/${season.number}/bonus-check`,
          used.map(({ line, row }) => ({
            stats: statsOf(row, stats, line),
            win: row.win,
          }))
        )
      );

      const byKey = new Map(
        used.map(({ row }, index) => [row.key, won[index] ?? []])
      );

      const manual = this.manualCodes();
      this.rows.update((rows) =>
        rows.map((row) => ({
          ...row,
          bonuses: [
            ...row.bonuses.filter((code) => manual.has(code)),
            ...(byKey.get(row.key) ?? []),
          ],
        }))
      );

      return this.#transloco.translate("adminScores.checked");
    });
  }

  /** Saves the game: all lines or none. */
  protected async save(): Promise<void> {
    const api = this.#gameApi();
    const problem = problemOf(this.rows(), this.stats());

    if (problem) {
      this.action.refuse(this.#explain(problem));

      return;
    }

    if (!api) {
      return;
    }

    await this.action.run(async () => {
      const saved = await firstValueFrom(
        this.#http.put<GameScores>(api, {
          lines: linesOf(this.rows(), this.stats(), this.manualCodes()),
          matchDate: this.matchDate() || null,
        })
      );

      this.loaded.set(saved);
      // Keeps this game on screen: without it, the reloaded matches would move on to the next one.
      await this.#router.navigate([], {
        queryParams: { game: saved.gameNumber, match: saved.matchNumber },
        queryParamsHandling: "merge",
        replaceUrl: true,
      });
      this.cards.reload();
      this.#players.reload();

      return this.#transloco.translate("adminScores.saved", {
        game: saved.gameNumber,
        match: saved.matchNumber,
      });
    });
  }

  /** Removes the game's scores, after asking. The match stays, with its date. */
  protected async clear(): Promise<void> {
    const api = this.#gameApi();

    if (
      !api ||
      !this.#window?.confirm(this.#transloco.translate("adminScores.clearAsk"))
    ) {
      return;
    }

    await this.action.run(async () => {
      await firstValueFrom(this.#http.delete(api));
      this.loaded.reload();
      this.cards.reload();

      return this.#transloco.translate("adminScores.cleared");
    });
  }

  #explain(problem: Problem): string {
    switch (problem.kind) {
      case "empty": {
        return this.#transloco.translate("adminScores.empty");
      }

      case "riotId": {
        return this.#transloco.translate("adminScores.riotIdShape", {
          row: problem.row,
        });
      }

      case "twice": {
        return this.#transloco.translate("adminScores.twice", {
          row: problem.row,
        });
      }

      default: {
        return this.#transloco.translate(
          isWhole(problem.stat)
            ? "adminScores.notWhole"
            : "adminScores.invalid",
          {
            row: problem.row,
            stat: this.#transloco.translate(`stats.long.${problem.stat}`),
          }
        );
      }
    }
  }

  /** The address's picks after a change: a new season or match starts on its own next game. */
  #selection(what: "season" | "match" | "game", value: string): Selection {
    if (what === "season") {
      return { game: null, match: null, season: value };
    }

    if (what === "match") {
      return { game: null, match: value };
    }

    return { game: value, match: this.matchNumber() };
  }

  #mayLeave(): boolean {
    return (
      !this.dirty() ||
      (this.#window?.confirm(
        this.#transloco.translate("adminScores.discard")
      ) ??
        true)
    );
  }

  #season(part: string): string | undefined {
    const api = this.current.api();

    return api ? `${api}/${part}` : undefined;
  }

  /** The saved lines, or the previous game's players; as many lines as a match has players. */
  #startingRows(): Row[] {
    if (!this.loaded.hasValue()) {
      return [];
    }

    const game = this.loaded.value();
    const places = this.current.organization()?.playersPerMatch ?? 10;

    const rows = game.saved
      ? game.lines.map((line) => rowFrom(line))
      : (this.roster.hasValue() ? this.roster.value().players : []).map(
          (player) => emptyRow({ kind: "known", playerId: player.playerId })
        );

    while (rows.length < places) {
      rows.push(emptyRow());
    }

    return rows;
  }
}
