import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { Router } from "@angular/router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  ALSACE,
  answer,
  MATCH_CARDS,
  openAlsace,
  rendered,
  SEASONS,
  testProviders,
  text,
  texts,
} from "../../testing";
import { statusesOf } from "./matches";

const API = `/api/orgs/${ALSACE.slug}`;

const open = async (url = "/o/alsace-arena/matches") => {
  const harness = await openAlsace(url, SEASONS);
  await answer(harness, `${API}/seasons/3/matches`, MATCH_CARDS);
  harness.detectChanges();

  return { harness, page: rendered(harness) };
};

describe("the matches page", () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it("shows a card per match of the newest season", async () => {
    const { page } = await open();

    expect(text(page.querySelector("h1"))).toBe("Matches");
    expect(text(page.querySelector(".lede"))).toBe(
      "8 matches this season. Each match is 1 to 5 games."
    );
    expect(texts(page, ".card h2")).toStrictEqual([
      "Match 1",
      "Match 2",
      "Match 3",
    ]);
    expect(texts(page, ".status")).toStrictEqual([
      "Played",
      "Next up",
      "Scheduled",
    ]);
    expect(texts(page, ".card .label")).toStrictEqual([
      "Nov 13",
      "Nov 20",
      "Date to be set",
    ]);
  });

  it("names the MVP of a played match, and has no link before it's played", async () => {
    const { page } = await open();
    const [played, next] = page.querySelectorAll(".card");

    expect(texts(played ?? page, ".facts > span")).toStrictEqual([
      "2 games",
      "MVP Kestrel · 5,749 pts",
    ]);
    expect(text(next?.querySelector(".facts"))).toBe("No games recorded yet.");
    expect(next?.querySelector("a")).toBeNull();
  });

  it("links a played match to its page", async () => {
    const { harness, page } = await open();
    const link = page.querySelector<HTMLAnchorElement>(".card a");

    expect(text(link)).toBe("View match 1");
    expect(link?.getAttribute("href")).toBe(
      "/o/alsace-arena/matches/1?season=3"
    );

    link?.click();
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(`${API}/seasons/3/matches/1`);
    });
    expect(TestBed.inject(Router).url).toBe(
      "/o/alsace-arena/matches/1?season=3"
    );
  });

  it("shows the season picked in the header", async () => {
    const harness = await openAlsace(
      "/o/alsace-arena/matches?season=1",
      SEASONS
    );

    await answer(harness, `${API}/seasons/1/matches`, MATCH_CARDS);
    harness.detectChanges();

    expect(text(rendered(harness).querySelector(".eyebrow"))).toBe(
      "Season 1 · Spike Tournament"
    );
  });

  it("says when the season plans no match", async () => {
    const harness = await openAlsace("/o/alsace-arena/matches", SEASONS);
    await answer(harness, `${API}/seasons/3/matches`, []);
    harness.detectChanges();

    expect(text(rendered(harness).querySelector(".notice"))).toBe(
      "No match is planned for this season yet."
    );
  });

  it("speaks the organisation's language, French", async () => {
    localStorage.clear();
    const { page } = await open();

    expect(text(page.querySelector(".lede"))).toBe(
      "8 matchs cette saison. Chaque match se joue en 1 à 5 parties."
    );
    expect(texts(page, ".status")).toStrictEqual(["Joué", "Prochain", "Prévu"]);
  });
});

describe("a match's status", () => {
  it("is next for the first match without scores, unless the season is over", () => {
    expect(statusesOf(MATCH_CARDS, false)).toStrictEqual([
      "played",
      "next",
      "scheduled",
    ]);
    expect(statusesOf(MATCH_CARDS, true)).toStrictEqual([
      "played",
      "scheduled",
      "scheduled",
    ]);
  });
});
