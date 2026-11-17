import { httpResource } from "@angular/common/http";
import { Component, computed, inject } from "@angular/core";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";

import { rankText } from "../core/colour";
import { LanguageService } from "../core/language";
import type {
  Leaderboard,
  RankRef,
  Rules,
  Standing,
  Summary,
} from "../core/seasons";
import { CurrentSeason } from "../core/seasons";
import type { Column, FormulaTerm } from "../core/stats";
import {
  columnsFor,
  decimalsOf,
  formatNumber,
  formulaOf,
  splitRiotId,
  valueOf,
} from "../core/stats";

/** The rank's colour for the pill, readable in each theme. */
const rankStyle = (rank: RankRef | null): Record<string, string> => {
  const colours = rankText(rank?.color);

  return colours
    ? { "--rank-dark": colours.dark, "--rank-light": colours.light }
    : {};
};

/**
 * The season leaderboard: the top three, the season's numbers, every player's standing, and how
 * points are counted. Shows the season picked in the header.
 */
@Component({
  imports: [TranslocoDirective],
  selector: "app-leaderboard",
  styleUrl: "./leaderboard.css",
  templateUrl: "./leaderboard.html",
})
export class LeaderboardPage {
  protected readonly current = inject(CurrentSeason);

  protected readonly board = httpResource<Leaderboard>(() =>
    this.#api("leaderboard")
  );
  protected readonly summary = httpResource<Summary>(() =>
    this.#api("summary")
  );
  protected readonly rules = httpResource<Rules>(() => this.#api("rules"));

  protected readonly standings = computed<Standing[]>(() =>
    this.board.hasValue() ? this.board.value().standings : []
  );
  protected readonly podium = computed(() => this.standings().slice(0, 3));
  protected readonly columns = computed<Column[]>(() =>
    this.board.hasValue() ? columnsFor(this.board.value().columns) : []
  );
  protected readonly formula = computed<FormulaTerm[]>(() =>
    this.rules.hasValue() ? formulaOf(this.rules.value()) : []
  );

  protected readonly rankStyle = rankStyle;
  protected readonly splitRiotId = splitRiotId;
  protected readonly valueOf = valueOf;
  protected readonly decimalsOf = decimalsOf;

  readonly #language = inject(LanguageService);
  readonly #transloco = inject(TranslocoService);

  protected number(value: number | null | undefined, decimals = 0): string {
    return formatNumber(value, this.#language.active, decimals);
  }

  /** The key for a count in the language's plural form, such as `leaderboard.wins.one`. */
  protected plural(key: string, count: number): string {
    const form = new Intl.PluralRules(this.#language.active).select(count);

    return `${key}.${form === "one" ? "one" : "other"}`;
  }

  /** A match date as a short day and month, such as "14 Nov". */
  protected day(date: string): string {
    return new Intl.DateTimeFormat(this.#language.active, {
      day: "numeric",
      month: "short",
      timeZone: "UTC",
    }).format(new Date(`${date}T00:00:00Z`));
  }

  /** One pill of the formula, such as "+ Kills", "− Deaths" or "+ 50 per win". */
  protected termText(term: FormulaTerm, first: boolean): string {
    const t = (key: string, params?: Record<string, string>): string =>
      this.#transloco.translate(key, params);

    const size = this.number(Math.abs(term.points));
    const sign = term.points < 0 ? "− " : "+ ";
    let text = "";

    switch (term.kind) {
      case "stat": {
        const name = t(`stats.long.${term.stat}`);
        text = Math.abs(term.points) === 1 ? name : `${size} × ${name}`;
        break;
      }

      case "bonuses": {
        text = t("leaderboard.perBonus", { points: size });
        break;
      }

      case "bonus": {
        text = `${size} ${term.bonus?.label ?? ""}`;
        break;
      }

      default: {
        text = t("leaderboard.perWin", { points: size });
      }
    }

    return first && term.points > 0 ? text : sign + text;
  }

  #api(part: string): string | undefined {
    const api = this.current.api();

    return api ? `${api}/${part}` : undefined;
  }
}
