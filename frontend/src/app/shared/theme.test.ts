import { TestBed } from "@angular/core/testing";
import { beforeEach, describe, expect, it } from "vitest";

import { ThemeService } from "./theme";

describe(ThemeService, () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it("starts from a remembered pick", () => {
    localStorage.setItem("theme", "light");
    const theme = TestBed.inject(ThemeService);
    TestBed.tick();
    expect(theme.theme()).toBe("light");
    expect(document.documentElement.dataset["theme"]).toBe("light");
  });

  it("ignores a remembered value that isn't a theme", () => {
    localStorage.setItem("theme", "sepia");
    const theme = TestBed.inject(ThemeService);
    expect(["dark", "light"]).toContain(theme.theme());
  });

  it("goes back to the template's red without an accent", () => {
    const theme = TestBed.inject(ThemeService);
    theme.setAccent("#5c8f48");
    TestBed.tick();
    theme.setAccent(null);
    TestBed.tick();
    expect(document.documentElement.style.getPropertyValue("--accent")).toBe(
      "#F0484E"
    );
  });
});
