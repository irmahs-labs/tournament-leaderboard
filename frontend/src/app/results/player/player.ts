import { httpResource } from "@angular/common/http";
import { Component, computed, inject, input } from "@angular/core";
import { RouterLink } from "@angular/router";
import { TranslocoDirective } from "@jsverse/transloco";

import { LanguageService } from "../../shared/language";
import { RankPill } from "../../shared/rank-pill";
import type { PlayerMatch, PlayerSeason, Stat } from "../api";
import { CurrentSeason } from "../current-season";
import { splitRiotId } from "../stats";

/** The season averages a profile shows, when the season records them. */
const AVERAGES: readonly Stat[] = [
  "PERFORMANCE_SCORE",
  "ECON_RATING",
  "FIRST_BLOODS",
];

/** Each match's bar, as a share of the best match's points. */
export const barsOf = (
  matches: readonly PlayerMatch[]
): { match: PlayerMatch; height: number; best: boolean }[] => {
  const best = Math.max(0, ...matches.map((match) => match.standing.points));

  return matches.map((match) => ({
    best: best > 0 && match.standing.points === best,
    height: best > 0 ? Math.max(0, match.standing.points) / best : 0,
    match,
  }));
};

/** Kills, deaths and assists over a match, such as "44 / 43 / 15"; a dash when not recorded. */
export const kdaOf = (match: PlayerMatch): string => {
  const { totals } = match.standing;

  return totals.KILLS === undefined
    ? "–"
    : `${totals.KILLS} / ${totals.DEATHS ?? 0} / ${totals.ASSISTS ?? 0}`;
};

/**
 * A player's profile for the season picked in the header: who they are, where they stand, their
 * averages, their points match by match and each match's line.
 */
@Component({
  imports: [RankPill, RouterLink, TranslocoDirective],
  selector: "app-player",
  styleUrl: "./player.css",
  templateUrl: "./player.html",
})
export class PlayerPage {
  /** From the address: /org/:slug/players/:player. */
  readonly player = input.required<string>();

  protected readonly current = inject(CurrentSeason);
  protected readonly language = inject(LanguageService);
  protected readonly splitRiotId = splitRiotId;

  protected readonly profile = httpResource<PlayerSeason>(() => {
    const api = this.current.api();

    return api
      ? `${api}/players/${encodeURIComponent(this.player())}`
      : undefined;
  });

  protected readonly bars = computed(() =>
    this.profile.hasValue() ? barsOf(this.profile.value().matches) : []
  );

  /** The averages to show: the ones the player's games recorded. */
  protected readonly averages = computed(() => {
    const columns = this.profile.hasValue() ? this.profile.value().columns : [];

    return AVERAGES.filter((stat) => columns.includes(stat));
  });

  protected readonly kda = kdaOf;

  protected winRate(wins: number, games: number): string {
    return games === 0
      ? "–"
      : `${this.language.number((wins / games) * 100)} %`;
  }
}
