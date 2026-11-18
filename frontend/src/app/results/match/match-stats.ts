import type { GameView, Stat, Standing } from "../api";

/** The leader tiles of a match, in order: the most of each, and the fewest deaths. */
const LEADER_STATS: readonly { stat: Stat; fewest: boolean }[] = [
  { fewest: false, stat: "KILLS" },
  { fewest: true, stat: "DEATHS" },
  { fewest: false, stat: "ASSISTS" },
  { fewest: false, stat: "FIRST_BLOODS" },
  { fewest: false, stat: "PLANTS" },
  { fewest: false, stat: "DEFUSES" },
];

/** A leader tile: who did best on a stat, all of them when level, and the value. */
export interface Leader {
  stat: Stat;
  fewest: boolean;
  riotIds: string[];
  value: number;
}

/** Players level on points share a rank, as on the leaderboard. */
const ranked = (rows: Standing[]): Standing[] =>
  rows.map((row) => ({
    ...row,
    rank: 1 + rows.filter((other) => other.points > row.points).length,
  }));

/**
 * One game's lines as standings, in the API's order (best first), so the match's table can show them: each stat is the
 * game's own, and the player's current rank comes from the match's combined table.
 */
export const gameStandings = (
  game: GameView,
  combined: readonly Standing[]
): Standing[] => {
  const ranks = new Map(
    combined.map((standing) => [standing.playerId, standing.currentRank])
  );

  const rows = game.lines.map((line): Standing => ({
    averages: line.stats,
    bonuses: Object.fromEntries(line.bonuses.map((code) => [code, 1])),
    currentRank: ranks.get(line.playerId) ?? null,
    games: 1,
    kda:
      ((line.stats.KILLS ?? 0) + (line.stats.ASSISTS ?? 0)) /
      Math.max(line.stats.DEATHS ?? 0, 1),
    playerId: line.playerId,
    points: line.points,
    rank: 0,
    riotId: line.riotId,
    totals: line.stats,
    wins: line.win ? 1 : 0,
  }));

  return ranked(rows);
};

/**
 * Who leads each of the match's headline stats, over the rows shown: only the stats the games
 * recorded, and not a "most" that nobody scored.
 */
export const leadersOf = (
  rows: readonly Standing[],
  columns: readonly Stat[]
): Leader[] => {
  const leaders: Leader[] = [];

  for (const { stat, fewest } of LEADER_STATS) {
    const scored = columns.includes(stat)
      ? rows.filter((row) => row.totals[stat] !== undefined)
      : [];

    const values = scored.map((row) => row.totals[stat] ?? 0);
    const value = fewest ? Math.min(...values) : Math.max(...values);

    if (scored.length > 0 && (fewest || value > 0)) {
      leaders.push({
        fewest,
        riotIds: scored
          .filter((row) => row.totals[stat] === value)
          .map((row) => row.riotId),
        stat,
        value,
      });
    }
  }

  return leaders;
};
