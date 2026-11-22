import {
  booleanAttribute,
  Component,
  computed,
  inject,
  input,
} from "@angular/core";
import { TranslocoDirective } from "@jsverse/transloco";

import { LanguageService } from "../../shared/language";
import { RankPill } from "../../shared/rank-pill";
import { RiotId } from "../../shared/riot-id";
import type { Stat, Standing } from "../api";
import type { Column } from "../stats";
import { addsUp, columnsFor, decimalsOf, splitRiotId, valueOf } from "../stats";

/**
 * Players' standings: position, rank, player, games, one average per recorded stat, wins and
 * total. Used by the season leaderboard, and by a match's tables: its combined one, and one per
 * game (`single`), where each stat is the game's own and Wins says whether the player won. With
 * `totals`, the stats that add up show their totals over the games instead of their averages.
 */
@Component({
  imports: [RankPill, RiotId, TranslocoDirective],
  selector: "app-standings-table",
  styleUrl: "./standings-table.css",
  templateUrl: "./standings-table.html",
})
export class StandingsTable {
  readonly standings = input.required<Standing[]>();

  /** The stats recorded in these games, as the API lists them. */
  readonly stats = input.required<Stat[]>();

  /** One game's lines rather than totals over several games. */
  readonly single = input(false, { transform: booleanAttribute });

  /** Totals over the games rather than averages, for the stats that add up. */
  readonly totals = input(false, { transform: booleanAttribute });

  protected readonly columns = computed(() => columnsFor(this.stats()));
  protected readonly language = inject(LanguageService);
  protected readonly splitRiotId = splitRiotId;
  protected readonly valueOf = valueOf;
  protected readonly decimalsOf = decimalsOf;

  /** Whether a column shows the players' totals rather than their averages. */
  protected total(column: Column): boolean {
    return this.totals() && !this.single() && addsUp(column);
  }
}
