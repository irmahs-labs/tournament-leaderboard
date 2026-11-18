import { beforeEach, describe, expect, it } from "vitest";

import { formatNumber, startingLanguage } from "./language";

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
