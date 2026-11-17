import { beforeEach, describe, expect, it } from "vitest";

import { startingLanguage } from "./language";

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
