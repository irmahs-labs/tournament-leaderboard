import { httpResource } from "@angular/common/http";
import { Component, computed, inject } from "@angular/core";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";

import { LanguageService } from "../../shared/language";
import { RankPill } from "../../shared/rank-pill";
import type { Leaderboard, Rules, Summary } from "../api";
import { CurrentSeason } from "../current-season";
import { StandingsTable } from "../standings-table/standings-table";
import type { FormulaTerm } from "../stats";
import { formulaOf, splitRiotId, winnableBonuses } from "../stats";

/**
 * The season leaderboard: the top three, the season's numbers, every player's standing, and how
 * points are counted. Shows the season picked in the header.
 */
@Component({
  imports: [RankPill, StandingsTable, TranslocoDirective],
  selector: "app-leaderboard",
  styleUrl: "./leaderboard.css",
  templateUrl: "./leaderboard.html",
})
export class LeaderboardPage {
  protected readonly current = inject(CurrentSeason);
  protected readonly language = inject(LanguageService);

  protected readonly board = httpResource<Leaderboard>(() =>
    this.#api("leaderboard")
  );
  protected readonly summary = httpResource<Summary>(() =>
    this.#api("summary")
  );
  protected readonly rules = httpResource<Rules>(() => this.#api("rules"));

  protected readonly podium = computed(() =>
    this.board.hasValue() ? this.board.value().standings.slice(0, 3) : []
  );

  /** "Most first bloods (FB), Fewest deaths (LD)". */
  protected readonly bonusList = computed(() =>
    this.rules.hasValue()
      ? winnableBonuses(this.rules.value())
          .map((bonus) => `${bonus.label} (${bonus.code})`)
          .join(", ")
      : ""
  );

  protected readonly splitRiotId = splitRiotId;

  readonly #transloco = inject(TranslocoService);

  /** The formula's pills, in the language on screen (re-read when it changes). */
  protected formula(rules: Rules): FormulaTerm[] {
    return formulaOf(
      rules,
      (key, params) => this.#transloco.translate(key, params),
      (value) => this.language.points(value, 2)
    );
  }

  #api(part: string): string | undefined {
    const api = this.current.api();

    return api ? `${api}/${part}` : undefined;
  }
}
