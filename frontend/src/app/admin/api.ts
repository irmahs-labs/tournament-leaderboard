/** The shapes of the admin API: `/api/orgs/{slug}/admin/…`. */

import type { MemberRole } from "../shared/session";

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
  /** A Google email typed in advance: whoever signs in with it first is linked. Admins only. */
  expectedEmail: string | null;
  /** Linked to someone's account: their Riot ID and Discord are then theirs to change. */
  linked: boolean;
}

/** What `POST …/admin/players` and `PUT …/admin/players/{id}` take. */
export interface PlayerInput {
  riotId: string;
  discordUsername: string | null;
  rankId: number | null;
  aliases: Alias[];
  expectedEmail: string | null;
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
  /** Whether to use the rules saved for the next season, which are then used up. */
  useNextSeasonRules: boolean;
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

/** Someone who belongs to the organisation, from `GET …/admin/members`. */
export interface TeamMember {
  userId: string;
  /** Their name from Google; nothing when Google gave none. */
  name: string | null;
  email: string;
  role: MemberRole;
  owner: boolean;
  joinedAt: string;
}

/** An admin invite that can still be used. */
export interface WaitingInvite {
  id: string;
  email: string;
  /** The admin who made it; nothing when they're gone. */
  invitedBy: string | null;
  createdAt: string;
  expiresAt: string;
}

/** `GET …/admin/members`. */
export interface Team {
  members: TeamMember[];
  invites: WaitingInvite[];
}

/** The answer to `POST …/admin/invites`: the code, shown only this once. */
export interface NewInvite {
  id: string;
  code: string;
  email: string;
  expiresAt: string;
}

/** How long an invite lasts, in minutes: 30 minutes, 1, 12, 24 or 48 hours. */
export const INVITE_MINUTES = [30, 60, 720, 1440, 2880] as const;
