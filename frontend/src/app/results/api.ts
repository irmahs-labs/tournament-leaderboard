/** The shapes of the public results API: seasons, leaderboard, summary, rules and matches. */

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
  | "CLUTCHES"
  /** The player's place on the in-game scoreboard, 1 at the top. */
  | "PLACE"
  /** Honours received from the other players of the game. */
  | "HONOURS";

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

/**
 * How a bonus is won: the highest or lowest value of its stat; the best place on the winning
 * (MVP) or losing (SVP) team; its stat reaching a threshold; or ticked by an admin.
 */
export type BonusAward =
  | "HIGHEST"
  | "LOWEST"
  | "MANUAL"
  | "TOP_WINNER"
  | "TOP_LOSER"
  | "AT_LEAST";

export interface Bonus {
  code: string;
  label: string;
  award: BonusAward;
  stat: Stat | null;
  points: number;
  /** The value to reach, for an AT_LEAST bonus; nothing otherwise. */
  threshold: number | null;
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
  /** Asked for on the Scores form; a stat that isn't recorded doesn't count in any game. */
  recorded: boolean;
  /** Points per unit, negative to subtract; 0 only shows the stat. */
  points: number;
  /** False for averages, percentages and the place, which can never count. */
  countable: boolean;
}

/** `GET …/seasons/{n}/rules`: "How points are counted". */
export interface Rules {
  pointsPerWin: number;
  /** Points for each game's KDA ratio, (kills + assists) / max(deaths, 1). */
  kdaPoints: number;
  stats: StatRule[];
  bonuses: Bonus[];
}

/** The match's best player: the most points over its games. */
export interface TopPlayer {
  playerId: string;
  riotId: string;
  points: number;
}

/** One entry of `GET …/seasons/{n}/matches`: a match, played or only planned. */
export interface MatchCard {
  matchNumber: number;
  /** YYYY-MM-DD, or nothing while it isn't set. */
  matchDate: string | null;
  /** How many games have scores; 0 before the match is played. */
  games: number;
  players: number;
  topPlayer: TopPlayer | null;
}

/** One player's line of a game. */
export interface GameLine {
  playerId: string;
  riotId: string;
  stats: Partial<Record<Stat, number>>;
  win: boolean;
  /** The codes of the bonuses the player won in the game. */
  bonuses: string[];
  points: number;
}

/** A game's lines, best first. */
export interface GameView {
  gameNumber: number;
  lines: GameLine[];
}

/** `GET …/seasons/{n}/matches/{m}`. */
export interface MatchView {
  season: { number: number; name: string };
  matchNumber: number;
  matchDate: string | null;
  /** The stats with a value in at least one of the match's games. */
  columns: Stat[];
  bonuses: Bonus[];
  games: GameView[];
  /** The players' totals over the whole match. */
  combined: Standing[];
}

/** A player as the public Players page lists them, from `GET /api/orgs/{slug}/players`. */
export interface PublicPlayer {
  id: string;
  riotId: string;
  /** Their other names: previous Riot IDs and nicknames. Never their Discord: only admins see it. */
  aliases: string[];
}

/** One match of a player's season; the standing's rank is their place in it. */
export interface PlayerMatch {
  matchNumber: number;
  matchDate: string | null;
  standing: Standing;
}

/** `GET …/seasons/{n}/players/{id}`: one player's season. */
export interface PlayerSeason {
  season: { number: number; name: string };
  player: PublicPlayer & { currentRank: RankRef | null };
  /** Nothing when they haven't played this season. */
  standing: Standing | null;
  /** How many players have played this season. */
  players: number;
  /** The player just ahead, and how many points they lead by; nothing when they lead. */
  ahead: { riotId: string; points: number } | null;
  columns: Stat[];
  matches: PlayerMatch[];
}
