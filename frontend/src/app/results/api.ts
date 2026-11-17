/** The shapes of the public results API: seasons, leaderboard, summary and rules. */

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
