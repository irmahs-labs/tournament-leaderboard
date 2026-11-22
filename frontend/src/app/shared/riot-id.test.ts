import { describe, expect, it } from "vitest";

import { isRiotId, splitRiotId } from "./riot-id";

describe(isRiotId, () => {
  it("accepts a name and a tag as Riot makes them", () => {
    for (const riotId of [
      "Kestrel#EUW",
      "Abe#ONE",
      " Huzéguto#1234 ",
      "Jett Main 2026#FR1",
    ]) {
      expect(isRiotId(riotId)).toBeTruthy();
    }
  });

  it("refuses anything else", () => {
    for (const riotId of [
      "Drrako",
      "Ab#EUW",
      "Kestrel#EU",
      "Kestrel#EUROPE",
      "Kestrel#EU-W",
      "A#B#CDE",
      "",
    ]) {
      expect(isRiotId(riotId)).toBeFalsy();
    }
  });
});

describe(splitRiotId, () => {
  it("splits the name from its tag, and keeps an old name without one", () => {
    expect(splitRiotId("Kestrel#EUW")).toStrictEqual({
      name: "Kestrel",
      tag: "#EUW",
    });
    expect(splitRiotId("Drrako")).toStrictEqual({ name: "Drrako", tag: "" });
  });
});
