import { describe, expect, it } from "vitest";

import { isRiotId, splitRiotId } from "./riot-id";

describe(splitRiotId, () => {
  it("makes everything from the first # the tag", () => {
    expect(splitRiotId("Kestrel#EUW")).toStrictEqual({
      name: "Kestrel",
      tag: "#EUW",
    });
    expect(splitRiotId("Jett Main#FR#1")).toStrictEqual({
      name: "Jett Main",
      tag: "#FR#1",
    });
  });

  it("leaves a name without a # whole", () => {
    expect(splitRiotId("Drrako")).toStrictEqual({ name: "Drrako", tag: "" });
  });
});

describe(isRiotId, () => {
  it("accepts a name and a tag as Riot makes them", () => {
    for (const riotId of [
      "Kestrel#EUW",
      "Abe#ONE",
      " Huzéguto#1234 ",
      "Jett Main#FR1",
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
      "A#B#CDE",
      "",
    ]) {
      expect(isRiotId(riotId)).toBeFalsy();
    }
  });
});
