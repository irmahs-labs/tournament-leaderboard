import { beforeEach, describe, expect, it } from "vitest";

import { formatNumber, rankIn, startingLanguage } from "./language";

describe(startingLanguage, () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("starts in the visitor's remembered language", () => {
    localStorage.setItem("lang", "fr");

    expect(startingLanguage()).toBe("fr");
  });

  it("starts in English otherwise", () => {
    localStorage.setItem("lang", "de");

    expect(startingLanguage()).toBe("en");
  });
});

describe(formatNumber, () => {
  it("formats numbers for the language, with a dash for nothing", () => {
    expect(formatNumber(5749, "en")).toBe("5,749");
    expect(formatNumber(1.5, "en", 2)).toBe("1.50");
    expect(formatNumber(5749, "fr")).toBe("5\u202F749");
    expect(formatNumber(undefined, "en")).toBe("–");
  });
});

describe(rankIn, () => {
  it("names ranks as the French game does, and leaves English alone", () => {
    expect(rankIn("Immortal 1", "fr")).toBe("Immortel 1");
    expect(rankIn("Platinum 3", "fr")).toBe("Platine 3");
    expect(rankIn("Ascendant 2", "fr")).toBe("Ascendant 2");
    expect(rankIn("Radiant", "fr")).toBe("Radiant");
    expect(rankIn("Immortal 1", "en")).toBe("Immortal 1");
  });
});
