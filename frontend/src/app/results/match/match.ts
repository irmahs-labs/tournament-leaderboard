import { HttpErrorResponse, httpResource } from "@angular/common/http";
import {
  Component,
  computed,
  inject,
  input,
  linkedSignal,
} from "@angular/core";
import { RouterLink } from "@angular/router";
import { TranslocoDirective } from "@jsverse/transloco";

import { LanguageService } from "../../shared/language";
import type { MatchView, Standing } from "../api";
import { CurrentSeason } from "../current-season";
import { StandingsTable } from "../standings-table/standings-table";
import { splitRiotId } from "../stats";
import { gameStandings, leadersOf } from "./match-stats";

/** A positive whole number from the address, or nothing. */
const matchNumber = (value: string): number | null => {
  const number = Number(value);

  return Number.isInteger(number) && number > 0 ? number : null;
};

/** Several players' names as one: "Kestrel, Wren". */
const names = (riotIds: readonly string[]): string =>
  riotIds.map((riotId) => splitRiotId(riotId).name).join(", ");

/**
 * One match of the season picked in the header: its MVP, the leader of each headline stat, its
 * games and who won them, and every player's line, over the whole match or one game.
 */
@Component({
  imports: [RouterLink, StandingsTable, TranslocoDirective],
  selector: "app-match",
  styleUrl: "./match.css",
  templateUrl: "./match.html",
})
export class MatchPage {
  /** `:match` in the address. */
  readonly match = input.required<string>();

  protected readonly current = inject(CurrentSeason);
  protected readonly language = inject(LanguageService);

  protected readonly number = computed(() => matchNumber(this.match()));

  protected readonly view = httpResource<MatchView>(() => {
    const api = this.current.api();
    const number = this.number();

    return api && number ? `${api}/matches/${number}` : undefined;
  });

  /** The season has no such match, or the address names none. */
  protected readonly missing = computed(() => {
    const error = this.view.error();

    return (
      this.number() === null ||
      (error instanceof HttpErrorResponse && error.status === 404)
    );
  });

  /** The game picked above the table; nothing for the whole match. Resets with the match. */
  protected readonly game = linkedSignal<string, number | null>({
    computation: () => null,
    source: this.match,
  });

  protected readonly shownGame = computed(() => {
    const picked = this.game();

    return this.view.hasValue() && picked !== null
      ? (this.view.value().games.find((game) => game.gameNumber === picked) ??
          null)
      : null;
  });

  /** The table's rows: the match's totals, or the picked game's lines. */
  protected readonly rows = computed<Standing[]>(() => {
    if (!this.view.hasValue()) {
      return [];
    }

    const game = this.shownGame();

    return game
      ? gameStandings(game, this.view.value().combined)
      : this.view.value().combined;
  });

  protected readonly mvp = computed(() => this.rows()[0] ?? null);

  protected readonly leaders = computed(() =>
    this.view.hasValue()
      ? leadersOf(this.rows(), this.view.value().columns)
      : []
  );

  /** Each game's best player and its winners. */
  protected readonly games = computed(() =>
    this.view.hasValue()
      ? this.view.value().games.map((game) => ({
          mvp: game.lines[0] ?? null,
          number: game.gameNumber,
          winners: names(
            game.lines.filter((line) => line.win).map((line) => line.riotId)
          ),
        }))
      : []
  );

  protected readonly splitRiotId = splitRiotId;
  protected readonly names = names;
}
