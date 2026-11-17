import { describe, expect, it } from "vitest";

import { RULES } from "../testing";
import { columnsFor, formatNumber, formulaOf, splitRiotId } from "./stats";

describe("stats", () => {
  it("folds kills, deaths and assists into one KDA column where kills come", () => {
    expect(
      columnsFor(["ACS", "KILLS", "DEATHS", "ASSISTS", "ECON_RATING"])
    ).toStrictEqual(["ACS", "KDA", "ECON_RATING"]);
    expect(columnsFor(["DEATHS", "PLANTS"])).toStrictEqual(["KDA", "PLANTS"]);
  });

  it("formats numbers for the language, with a dash for nothing", () => {
    expect(formatNumber(5749, "en")).toBe("5,749");
    expect(formatNumber(1.5, "en", 2)).toBe("1.50");
    expect(formatNumber(5749, "fr")).toBe("5 749");
    expect(formatNumber(undefined, "en")).toBe("–");
  });

  it("lists each bonus on its own when they're worth different points", () => {
    const [first, second] = RULES.bonuses;

    if (!first || !second) {
      throw new Error("The fixture has two bonuses");
    }

    const terms = formulaOf({
      ...RULES,
      bonuses: [first, { ...second, points: 20 }],
      pointsPerWin: 0,
    });

    expect(terms.map((term) => [term.kind, term.points])).toStrictEqual([
      ["stat", 1],
      ["stat", 1],
      ["stat", -1],
      ["bonus", 10],
      ["bonus", 20],
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
