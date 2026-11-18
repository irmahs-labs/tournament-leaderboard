import { describe, expect, it } from "vitest";

import {
  accentOrDefault,
  contrast,
  DEFAULT_ACCENT,
  mix,
  onAccent,
  readableAccent,
} from "./colour";

describe("colour", () => {
  it("measures contrast as WCAG does", () => {
    expect(contrast("#ffffff", "#000000")).toBeCloseTo(21, 5);
    expect(contrast("#777777", "#777777")).toBe(1);
  });

  it("mixes two colours", () => {
    expect(mix("#000000", "#ffffff", 0.5)).toBe("#808080");
    expect(mix("#F0484E", "#161D27", 0)).toBe("#f0484e");
  });

  it("falls back to the template's red", () => {
    expect(accentOrDefault(null)).toBe(DEFAULT_ACCENT);
    expect(accentOrDefault("red")).toBe(DEFAULT_ACCENT);
    expect(accentOrDefault("#5c8f48")).toBe("#5c8f48");
  });

  it("puts white or near-black on the accent, whichever reads better", () => {
    expect(onAccent("#2F5BFF")).toBe("#ffffff");
    expect(onAccent("#F2D84B")).toBe("#0F141B");
  });

  it("makes accent text readable on the surface", () => {
    const light = { surface: "#F7F5F0", text: "#0F141B" };
    const text = readableAccent("#F0484E", light.surface, light.text);
    expect(contrast(text, light.surface)).toBeGreaterThanOrEqual(4.5);
    expect(readableAccent("#F0484E", "#161D27", "#ECE8E1")).toBe("#F0484E");
  });
});
