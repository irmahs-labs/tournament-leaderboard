import { Component, computed, inject, input } from "@angular/core";
import { TranslocoDirective } from "@jsverse/transloco";

import { LanguageService } from "../../shared/language";
import { RankPill } from "../../shared/rank-pill";
import type { Stat, Standing } from "../api";
import { columnsFor, decimalsOf, splitRiotId, valueOf } from "../stats";

/**
 * Players' standings: position, rank, player, games, one average per recorded stat, wins and
 * total. Used by the season leaderboard, and by a match's combined table.
 */
@Component({
  imports: [RankPill, TranslocoDirective],
  selector: "app-standings-table",
  styleUrl: "./standings-table.css",
  templateUrl: "./standings-table.html",
})
export class StandingsTable {
  readonly standings = input.required<Standing[]>();

  /** The stats recorded in these games, as the API lists them. */
  readonly stats = input.required<Stat[]>();

  protected readonly columns = computed(() => columnsFor(this.stats()));
  protected readonly language = inject(LanguageService);
  protected readonly splitRiotId = splitRiotId;
  protected readonly valueOf = valueOf;
  protected readonly decimalsOf = decimalsOf;
}
