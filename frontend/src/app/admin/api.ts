/** The shapes of the admin API: `/api/orgs/{slug}/admin/…`. */

/** One of the Valorant ranks, from `GET /api/ranks`. */
export interface RankOption {
  id: number;
  name: string;
  /** #RRGGBB, or nothing. */
  color: string | null;
}

export type AliasKind = "PREVIOUS_RIOT_ID" | "NICKNAME" | "OTHER";

export interface Alias {
  alias: string;
  kind: AliasKind;
}

/** A player, from `GET …/admin/players` and the answers to adding or editing one. */
export interface AdminPlayer {
  id: string;
  riotId: string;
  discordUsername: string | null;
  rank: RankOption | null;
  aliases: Alias[];
}

/** What `POST …/admin/players` and `PUT …/admin/players/{id}` take. */
export interface PlayerInput {
  riotId: string;
  discordUsername: string | null;
  rankId: number | null;
  aliases: Alias[];
}

/** What `POST …/admin/seasons` takes: whatever is left out comes from the copied rules. */
export interface NewSeason {
  name: string | null;
  startsOn: string | null;
  endsOn: string | null;
  matchCount: number;
  minGames: number;
  maxGames: number;
  /** The season whose rules to copy; the platform defaults when nothing. */
  copyRulesFrom: number | null;
}

/** What `PUT …/admin/seasons/{n}` takes. */
export interface SeasonInput {
  name: string;
  startsOn: string | null;
  endsOn: string | null;
  matchCount: number;
  minGames: number;
  maxGames: number;
  /** True closes the season, false reopens it. */
  finished: boolean;
}

/** The limits the API checks: a season's name, matches and games per match. */
export const SEASON_LIMITS = { maxGames: 9, maxMatches: 20, maxName: 40 };
