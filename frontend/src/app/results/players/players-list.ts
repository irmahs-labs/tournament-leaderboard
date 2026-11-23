import type { PublicPlayer, Standing } from "../api";

/** A player of the season: their standing, with their other names. */
export interface PlayerCard {
  standing: Standing;
  aliases: string[];
}

export type PlayerSort = "points" | "performance" | "kda" | "wins" | "rank";

export const PLAYER_SORTS: readonly PlayerSort[] = [
  "points",
  "performance",
  "kda",
  "wins",
  "rank",
];

/** Valorant's tiers, best first: a rank's name starts with one of them. */
export const TIERS = [
  "Radiant",
  "Immortal",
  "Ascendant",
  "Diamond",
  "Platinum",
  "Gold",
  "Silver",
  "Bronze",
  "Iron",
] as const;

export type Tier = (typeof TIERS)[number];

/** The tier of a rank's name, such as Diamond for "Diamond 3"; nothing when it has no tier. */
export const tierOf = (rank: string | null | undefined): Tier | null =>
  TIERS.find((tier) => rank?.startsWith(tier)) ?? null;

/** How high a rank is, for sorting: Radiant first, then Immortal 3, 2, 1, and so on. */
const rankOrder = (rank: string | null | undefined): number => {
  const tier = tierOf(rank);

  if (!tier) {
    return Number.MAX_SAFE_INTEGER;
  }

  const step = Number(rank?.slice(tier.length).trim() || "0");

  return TIERS.indexOf(tier) * 10 - step;
};

/** The season's players with what the Players page shows of them, in the leaderboard's order. */
export const cardsOf = (
  standings: readonly Standing[],
  players: readonly PublicPlayer[]
): PlayerCard[] => {
  const byId = new Map(players.map((player) => [player.id, player]));

  return standings.map((standing) => ({
    aliases: byId.get(standing.playerId)?.aliases ?? [],
    standing,
  }));
};

/** Whether the search is in their Riot ID or an alias, ignoring case. */
const matches = (card: PlayerCard, search: string): boolean =>
  [card.standing.riotId, ...card.aliases].some((name) =>
    name.toLowerCase().includes(search)
  );

const SORTS: Record<PlayerSort, (a: PlayerCard, b: PlayerCard) => number> = {
  kda: (a, b) => b.standing.kda - a.standing.kda,
  performance: (a, b) =>
    (b.standing.averages.PERFORMANCE_SCORE ?? -1) -
    (a.standing.averages.PERFORMANCE_SCORE ?? -1),
  points: (a, b) => a.standing.rank - b.standing.rank,
  rank: (a, b) =>
    rankOrder(a.standing.currentRank?.name) -
    rankOrder(b.standing.currentRank?.name),
  wins: (a, b) => b.standing.wins - a.standing.wins,
};

/** The cards the search and the tier keep, sorted; ties keep the leaderboard's order. */
export const shownCards = (
  cards: readonly PlayerCard[],
  options: { search: string; sort: PlayerSort; tier: string }
): PlayerCard[] => {
  const search = options.search.trim().toLowerCase();

  return cards
    .filter((card) => search === "" || matches(card, search))
    .filter(
      (card) =>
        options.tier === "" ||
        tierOf(card.standing.currentRank?.name) === options.tier
    )
    .toSorted(
      (a, b) => SORTS[options.sort](a, b) || a.standing.rank - b.standing.rank
    );
};
