import { describe, expect, it } from "vitest";

import { LEADERBOARD, RULES } from "../testing";
import {
  columnsFor,
  decimalsOf,
  formulaOf,
  valueOf,
  winnableBonuses,
} from "./stats";

/** A stand-in for Transloco: the key's last part, then the points. */
const t = (key: string, params?: Record<string, string>) =>
  `${key.split(".").at(-1)}${params ? ` ${params["points"]}` : ""}`;

describe("stats", () => {
  it("folds kills, deaths and assists into one KDA column where kills come", () => {
    expect(
      columnsFor(["ACS", "KILLS", "DEATHS", "ASSISTS", "ECON_RATING"])
    ).toStrictEqual(["ACS", "KDA", "ECON_RATING"]);
    expect(columnsFor(["DEATHS", "PLANTS"])).toStrictEqual(["KDA", "PLANTS"]);
  });

  it("writes the formula, one pill per stat that counts, bonus and win", () => {
    const [first, second] = RULES.bonuses;

    if (!first || !second) {
      throw new Error("The fixture has two bonuses");
    }

    expect(formulaOf(RULES, t, String).map((term) => term.text)).toStrictEqual([
      "PERFORMANCE_SCORE",
      "+ KILLS",
      "− DEATHS",
      "+ perBonus 10",
      "+ perWin 50",
    ]);
    expect(
      formulaOf(
        {
          ...RULES,
          bonuses: [first, { ...second, points: 20 }],
          pointsPerWin: 0,
        },
        t,
        String
      ).map((term) => term.text)
    ).toStrictEqual([
      "PERFORMANCE_SCORE",
      "+ KILLS",
      "− DEATHS",
      "+ 10 Most first bloods",
      "+ 20 Fewest deaths",
    ]);
  });
});

describe("a standing's value", () => {
  const [kestrel] = LEADERBOARD.standings;

  it("is the average, or the total when asked and the stat adds up", () => {
    expect(
      kestrel
        ? [
            valueOf(kestrel, "KILLS"),
            valueOf(kestrel, "KILLS", true),
            valueOf(kestrel, "KDA", true),
          ]
        : []
    ).toStrictEqual([16.5, 33, 1.58]);
  });

  it("keeps per-round stats as averages, and totals whole", () => {
    expect([
      decimalsOf("PLANTS"),
      decimalsOf("PLANTS", true),
      decimalsOf("ADR", true),
    ]).toStrictEqual([2, 0, 0]);
  });
});

describe(winnableBonuses, () => {
  it("leaves out a bonus worked out from a stat that isn't recorded", () => {
    const rules = {
      ...RULES,
      bonuses: [
        ...RULES.bonuses,
        {
          award: "AT_LEAST" as const,
          code: "HON",
          label: "Most honourable",
          points: 10,
          stat: "CLUTCHES" as const,
          threshold: 3,
        },
        {
          award: "MANUAL" as const,
          code: "ACE",
          label: "Ace",
          points: 5,
          stat: null,
          threshold: null,
        },
      ],
    };

    expect(winnableBonuses(rules).map((bonus) => bonus.code)).toStrictEqual([
      "FB",
      "LD",
      "ACE",
    ]);
  });
});
