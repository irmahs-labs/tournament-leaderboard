import { describe, expect, it } from "vitest";

import { MATCH } from "../../testing";
import type { Standing } from "../api";
import { gameStandings, leadersOf } from "./match-stats";

const [, second] = MATCH.games;

describe("a game's standings", () => {
  it("keep the API's order, rank by points, and take the current rank from the match", () => {
    const rows = second ? gameStandings(second, MATCH.combined) : [];

    expect(
      rows.map(({ rank, riotId, currentRank, wins, games }) => ({
        currentRank: currentRank?.name ?? null,
        games,
        rank,
        riotId,
        wins,
      }))
    ).toStrictEqual([
      { currentRank: null, games: 1, rank: 1, riotId: "Wren#EUW", wins: 1 },
      {
        currentRank: "Radiant",
        games: 1,
        rank: 2,
        riotId: "Kestrel#EUW",
        wins: 0,
      },
    ]);
    expect(rows[1]?.kda).toBeCloseTo(16 / 14);
    expect(rows[1]?.bonuses).toStrictEqual({ FB: 1 });
  });

  it("share a rank when level on points, and count no deaths as one", () => {
    const rows = gameStandings(
      {
        gameNumber: 1,
        lines: [
          {
            bonuses: [],
            playerId: "a",
            points: 10,
            riotId: "A#1",
            stats: { ASSISTS: 1, DEATHS: 0, KILLS: 4 },
            win: true,
          },
          {
            bonuses: [],
            playerId: "b",
            points: 10,
            riotId: "B#1",
            stats: {},
            win: false,
          },
        ],
      },
      []
    );

    expect(rows.map((row) => row.rank)).toStrictEqual([1, 1]);
    expect(rows.map((row) => row.kda)).toStrictEqual([5, 0]);
  });
});

const row = (riotId: string, totals: Standing["totals"]): Standing => ({
  averages: {},
  bonuses: {},
  currentRank: null,
  games: 1,
  kda: 0,
  playerId: riotId,
  points: 0,
  rank: 1,
  riotId,
  totals,
  wins: 0,
});

describe("a match's leaders", () => {
  it("lists every player level on the best value", () => {
    expect(
      leadersOf(
        [
          row("A#1", { KILLS: 9 }),
          row("B#1", { KILLS: 9 }),
          row("C#1", { KILLS: 3 }),
        ],
        ["KILLS"]
      )
    ).toStrictEqual([
      { fewest: false, riotIds: ["A#1", "B#1"], stat: "KILLS", value: 9 },
    ]);
  });

  it("skips stats the games didn't record, and a most that nobody scored", () => {
    expect(
      leadersOf(
        [
          row("A#1", { DEATHS: 4, DEFUSES: 0 }),
          row("B#1", { DEATHS: 2, DEFUSES: 0 }),
        ],
        ["DEATHS", "DEFUSES"]
      )
    ).toStrictEqual([
      { fewest: true, riotIds: ["B#1"], stat: "DEATHS", value: 2 },
    ]);
  });
});
