import { httpResource } from "@angular/common/http";
import { Injectable, computed, signal } from "@angular/core";

import type { Organization } from "./organization";

/** A stat a game can record for a player, in the order the pages show them. */
export type Stat =
  | "PERFORMANCE_SCORE"
  | "ACS"
  | "ADR"
  | "DAMAGE_DELTA"
  | "KAST_PCT"
  | "KILLS"
  | "DEATHS"
  | "ASSISTS"
  | "ECON_RATING"
  | "FIRST_BLOODS"
  | "PLANTS"
  | "DEFUSES"
  | "HEADSHOT_PCT"
  | "CLUTCHES";

/** One entry of `GET /api/orgs/{slug}/seasons`. */
export interface Season {
  number: number;
  name: string;
  startsOn: string | null;
  endsOn: string | null;
  matchCount: number;
  minGames: number;
  maxGames: number;
  finished: boolean;
}

export interface Bonus {
  code: string;
  label: string;
  award: "HIGHEST" | "LOWEST" | "MANUAL";
  stat: Stat | null;
  points: number;
}

export interface RankRef {
  name: string;
  /** #RRGGBB, or nothing. */
  color: string | null;
}

/** One player's row of the leaderboard. Players level on points share a rank. */
export interface Standing {
  rank: number;
  playerId: string;
  riotId: string;
  currentRank: RankRef | null;
  games: number;
  wins: number;
  points: number;
  totals: Partial<Record<Stat, number>>;
  averages: Partial<Record<Stat, number>>;
  kda: number;
  bonuses: Record<string, number>;
}

/** `GET …/seasons/{n}/leaderboard`. */
export interface Leaderboard {
  season: { number: number; name: string };
  /** The stats with a value in at least one game. */
  columns: Stat[];
  bonuses: Bonus[];
  standings: Standing[];
}

/** `GET …/seasons/{n}/summary`. */
export interface Summary {
  matchCount: number;
  matchesPlayed: number;
  gamesPlayed: number;
  players: number;
  nextMatch: { matchNumber: number; matchDate: string } | null;
}

export interface StatRule {
  stat: Stat;
  recorded: boolean;
  /** Points per unit: 1 adds, -1 subtracts, 0 only shows the stat. */
  effect: number;
  countable: boolean;
}

/** `GET …/seasons/{n}/rules`: "How points are counted". */
export interface Rules {
  pointsPerWin: number;
  stats: StatRule[];
  bonuses: Bonus[];
}

/** The season shown by default: the most recent one. */
const latest = (seasons: readonly Season[]): Season | null => {
  let best: Season | null = null;

  for (const season of seasons) {
    if (!best || season.number > best.number) {
      best = season;
    }
  }

  return best;
};

/**
 * The organisation on screen, its seasons, and the one picked in the header (`?season=` in the
 * address; the most recent one otherwise).
 */
@Injectable({ providedIn: "root" })
export class CurrentSeason {
  readonly #organization = signal<Organization | null>(null);
  readonly #requested = signal<number | null>(null);
  readonly #list = httpResource<Season[]>(() => {
    const org = this.#organization();

    return org
      ? `/api/orgs/${encodeURIComponent(org.slug)}/seasons`
      : undefined;
  });

  readonly organization = this.#organization.asReadonly();

  /** Newest first, as the header's picker lists them. */
  readonly seasons = computed<Season[]>(() => {
    const newestFirst: Season[] = [];

    // The API lists them oldest first.
    for (const season of this.#list.hasValue() ? this.#list.value() : []) {
      newestFirst.unshift(season);
    }

    return newestFirst;
  });

  /** Whether the list has arrived (or failed), so "no season yet" can be told apart from loading. */
  readonly settled = computed(
    () => this.#list.status() === "resolved" || this.#list.status() === "error"
  );

  readonly failed = computed(() => this.#list.status() === "error");

  readonly season = computed<Season | null>(() => {
    const seasons = this.seasons();

    return (
      seasons.find((season) => season.number === this.#requested()) ??
      latest(seasons)
    );
  });

  /** The address of the picked season's API, such as `/api/orgs/alsace-arena/seasons/3`. */
  readonly api = computed(() => {
    const org = this.#organization();
    const season = this.season();

    return org && season
      ? `/api/orgs/${encodeURIComponent(org.slug)}/seasons/${season.number}`
      : undefined;
  });

  /** Called by the organisation's shell whenever the organisation or `?season=` changes. */
  use(organization: Organization, requested: number | null): void {
    if (this.#organization()?.slug !== organization.slug) {
      this.#organization.set(organization);
    }

    this.#requested.set(requested);
  }
}
