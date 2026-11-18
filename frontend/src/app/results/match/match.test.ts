import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import type { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  ALSACE,
  answer,
  MATCH,
  openAlsace,
  rendered,
  SEASONS,
  testProviders,
  text,
  texts,
} from "../../testing";

const API = `/api/orgs/${ALSACE.slug}/seasons/3`;

describe("a match's page", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(() => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  const open = async (): Promise<HTMLElement> => {
    harness = await openAlsace("/o/alsace-arena/matches/1?season=3", SEASONS);
    await answer(harness, `${API}/matches/1`, MATCH);
    harness.detectChanges();

    return rendered(harness);
  };

  const pick = (page: HTMLElement, label: string): void => {
    const button = [
      ...page.querySelectorAll<HTMLButtonElement>(".switch button"),
    ].find((each) => text(each) === label);

    button?.click();
    harness.detectChanges();
  };

  it("shows the match's numbers and its MVP", async () => {
    const page = await open();

    expect(text(page.querySelector("h1"))).toBe("Match 1");
    expect(text(page.querySelector(".eyebrow"))).toBe("Season 3 · Nov 13");
    expect(text(page.querySelector(".lede"))).toBe("2 games · 2 players");
    expect(text(page.querySelector(".mvp-name"))).toBe("Kestrel");
    expect(texts(page, ".mvp-stats > span")).toStrictEqual([
      "5,749 pts",
      "33 / 26 / 8",
    ]);
  });

  it("leads back to the season's matches", async () => {
    const page = await open();
    const crumbs = page.querySelector("app-match nav");

    expect(crumbs?.getAttribute("aria-label")).toBe("Breadcrumb");
    expect(crumbs?.querySelector("a")?.getAttribute("href")).toBe(
      "/o/alsace-arena/matches?season=3"
    );
    expect(text(crumbs?.querySelector("[aria-current=page]"))).toBe("Match 1");
  });

  it("names the leader of each stat the games recorded", async () => {
    const page = await open();

    expect(texts(page, ".leader .label")).toStrictEqual([
      "Most kills",
      "Fewest deaths",
      "Most assists",
      "Most plants",
    ]);
    expect(texts(page, ".leader-name")).toStrictEqual([
      "Kestrel",
      "Wren",
      "Kestrel",
      "Kestrel",
    ]);
  });

  it("says who won each game", async () => {
    const page = await open();

    expect(texts(page, ".game-name")).toStrictEqual(["Game 1", "Game 2"]);
    expect(texts(page, ".game .pill")).toStrictEqual([
      "MVP Kestrel",
      "MVP Wren",
    ]);
    expect(texts(page, ".game .small")).toStrictEqual([
      "Won by Kestrel",
      "Won by Wren",
    ]);
  });

  it("switches the table to one game", async () => {
    const page = await open();

    expect(text(page.querySelector(".block-head .small"))).toBe(
      "All 2 games combined"
    );
    expect(texts(page, "thead th")).toContain("Games");

    pick(page, "Game 2");

    expect(text(page.querySelector(".switch [aria-pressed=true]"))).toBe(
      "Game 2"
    );
    expect(text(page.querySelector(".block-head .small"))).toBe("Game 2");
    expect(texts(page, "thead th")).not.toContain("Games");
  });

  it("shows the picked game's MVP and lines, until Overall is picked", async () => {
    const page = await open();

    pick(page, "Game 2");

    expect(text(page.querySelector(".mvp .label"))).toBe("Game MVP");
    expect(text(page.querySelector(".mvp-name"))).toBe("Wren");
    expect(
      [...page.querySelectorAll("tbody tr")].map((tr) => texts(tr, "td"))
    ).toStrictEqual([
      ["1", "", "Wren#EUW", "180", "1.33", "–", "Yes", "2,900"],
      ["2", "Radiant", "Kestrel#EUW", "200", "1.14", "–", "No", "2,549"],
    ]);

    pick(page, "Overall");

    expect(text(page.querySelector(".mvp-name"))).toBe("Kestrel");
  });

  it("shows averages, or totals for the stats that add up", async () => {
    const page = await open();

    const firstRow = (): (string | undefined)[] =>
      texts(page.querySelector("tbody tr") ?? page, "td");

    const toggle = page.querySelector<HTMLInputElement>("input[role=switch]");

    expect(firstRow()).toStrictEqual([
      "1",
      "Radiant",
      "Kestrel#EUW",
      "2",
      "220",
      "1.58",
      "1.50",
      "1",
      "5,749",
    ]);

    toggle?.click();
    harness.detectChanges();

    expect(toggle?.checked).toBeTruthy();
    expect(firstRow()).toStrictEqual([
      "1",
      "Radiant",
      "Kestrel#EUW",
      "2",
      "440",
      "1.58",
      "3",
      "1",
      "5,749",
    ]);
    expect(texts(page, "thead abbr")).toStrictEqual([
      "Perf.",
      "Avg KDA",
      "Plants",
    ]);
  });

  it("offers totals only over the whole match", async () => {
    const page = await open();

    pick(page, "Game 1");

    expect(page.querySelector("input[role=switch]")).toBeNull();
  });

  it("says when the season has no such match", async () => {
    harness = await openAlsace("/o/alsace-arena/matches/9?season=3", SEASONS);
    await vi.waitFor(() => {
      harness.detectChanges();
      http
        .expectOne(`${API}/matches/9`)
        .flush(null, { status: 404, statusText: "Not Found" });
    });
    harness.detectChanges();

    expect(text(rendered(harness).querySelector(".notice"))).toBe(
      "This season has no match with this number."
    );
  });

  it("asks for nothing when the address names no match", async () => {
    harness = await openAlsace("/o/alsace-arena/matches/abc", SEASONS);

    expect(text(rendered(harness).querySelector(".notice"))).toBe(
      "This season has no match with this number."
    );
  });
});
