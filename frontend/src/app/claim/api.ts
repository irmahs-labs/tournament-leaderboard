/** A player record, as a member picks theirs. */
export interface PlayerSummary {
  id: string;
  riotId: string;
  discordUsername: string | null;
  aliases: string[];
  /** Their Valorant rank's name, such as "Diamond 3". */
  rank: string | null;
  games: number;
}

export type ClaimStatus = "PENDING" | "ACCEPTED" | "DECLINED" | "CANCELLED";

/** A claim, as its member or an admin sees it. */
export interface ClaimView {
  id: string;
  playerId: string;
  riotId: string | null;
  note: string | null;
  sentAt: string;
  status: ClaimStatus;
  /** Why it was declined, shown to the player. */
  reason: string | null;
  selfApproved: boolean;
  decidedAt: string | null;
}

/** `GET /api/orgs/{slug}/claim`: where someone stands with the player records. */
export interface ClaimState {
  member: boolean;
  joinedAt: string | null;
  /** The player they're linked to; nothing until a claim is accepted. */
  linked: PlayerSummary | null;
  /** Their latest claim not cancelled: the one waiting, or the last answered. */
  claim: ClaimView | null;
}

/** A claim in the admins' queue. */
export interface ClaimRow {
  id: string;
  name: string | null;
  email: string | null;
  memberSince: string | null;
  player: PlayerSummary;
  /** How many claims wait for the same player, this one included. */
  competing: number;
  /** Whether it's the admin's own claim. */
  own: boolean;
  claim: ClaimView;
}

/** The claims a search finds: by Riot ID, Discord or alias, ignoring case. */
export const matchingPlayers = (
  players: readonly PlayerSummary[],
  search: string
): PlayerSummary[] => {
  const wanted = search.trim().toLowerCase();

  return wanted === ""
    ? [...players]
    : players.filter((player) =>
        [player.riotId, player.discordUsername ?? "", ...player.aliases].some(
          (name) => name.toLowerCase().includes(wanted)
        )
      );
};

/** The longest note or reason the API takes. */
export const MAX_TEXT = 200;

/** The longest Riot ID or Discord username the API takes. */
export const MAX_NAME = 100;
