import { describe, expect, it } from "vitest";

import { RULES } from "../testing";
import { columnsFor, formulaOf, splitRiotId } from "./stats";

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

  it("splits a Riot ID at its last #", () => {
    expect(splitRiotId("Kestrel#EUW")).toStrictEqual({
      name: "Kestrel",
      tag: "#EUW",
    });
    expect(splitRiotId("nyxx")).toStrictEqual({ name: "nyxx", tag: "" });
  });
});
