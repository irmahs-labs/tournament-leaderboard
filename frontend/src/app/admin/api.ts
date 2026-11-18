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
