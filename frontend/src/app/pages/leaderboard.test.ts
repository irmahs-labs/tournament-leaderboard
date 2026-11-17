import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { Router } from "@angular/router";
import { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { Organization } from "../core/organization";
import type { Leaderboard, Rules, Season, Summary } from "../core/seasons";
import {
  ALSACE,
  LEADERBOARD,
  RULES,
  SEASONS,
  SUMMARY,
  testProviders,
} from "../testing";

const text = (element: Element | null | undefined) =>
  element?.textContent?.replaceAll(/\s+/gu, " ").trim();

const texts = (page: HTMLElement, selector: string): (string | undefined)[] =>
  [...page.querySelectorAll(selector)].map((element) => text(element));

/** What the fake back end answers. */
type Answer = Organization | Season[] | Leaderboard | Summary | Rules;

const API = `/api/orgs/${ALSACE.slug}`;

describe("the season leaderboard", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(() => {
    // English, whatever the organisation's default.
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  const answer = async (url: string, body: Answer): Promise<void> => {
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(url).flush(body);
    });
  };

  /** Opens the page and answers the season's three requests. */
  const open = async (
    url = "/o/alsace-arena",
    seasons: Season[] = SEASONS,
    season = 3
  ): Promise<HTMLElement> => {
    harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl(url);
    await answer(API, ALSACE);
    await navigation;
    await answer(`${API}/seasons`, seasons);

    if (seasons.length > 0) {
      await answer(`${API}/seasons/${season}/leaderboard`, LEADERBOARD);
      await answer(`${API}/seasons/${season}/summary`, SUMMARY);
      await answer(`${API}/seasons/${season}/rules`, RULES);
    }

    harness.detectChanges();

    const page = harness.routeNativeElement;

    if (!page) {
      throw new Error("The route rendered nothing");
    }

    return page;
  };

  it("shows the most recent season by default", async () => {
    const page = await open();

    expect(text(page.querySelector(".eyebrow"))).toBe(
      "Season 3 · Spike Tournament"
    );
    expect(text(page.querySelector(".played"))).toBe("1 of 8 matches played");
  });

  it("puts the top three first, the best as the season's MVP", async () => {
    const page = await open();

    expect(texts(page, ".podium-card .label")).toStrictEqual([
      "Season MVP",
      "Second",
    ]);
    expect(text(page.querySelector(".mvp .podium-name"))).toBe("Kestrel");
    expect(texts(page, ".mvp .podium-stats span")).toStrictEqual([
      "5,749 pts",
      "33 kills",
      "1 win",
    ]);
    expect(text(page.querySelector(".mvp .rank"))).toBe("Radiant");
  });

  it("shows the season's numbers", async () => {
    const page = await open();

    expect(texts(page, ".tile-value")).toStrictEqual(["1", "2", "2", "Nov 20"]);
    expect(texts(page, ".tile-note")).toStrictEqual([
      "of 8 this season",
      "2 per match",
      "Match 2",
    ]);
  });

  it("lists every player with an average per recorded stat, K, D and A as one KDA", async () => {
    const page = await open();

    expect(texts(page, "thead th")).toStrictEqual([
      "#",
      "Rank",
      "Player",
      "Games",
      "Avg Perf.",
      "Avg KDA",
      "Avg Plants",
      "Wins",
      "Total",
    ]);
    expect(texts(page, "tbody tr:first-child td")).toStrictEqual([
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
    // Wren recorded no plants.
    expect(texts(page, "tbody tr:nth-child(2) td")[6]).toBe("–");
  });

  it("explains how the season counts points", async () => {
    const page = await open();

    expect(texts(page, ".formula li")).toStrictEqual([
      "Performance",
      "+ Kills",
      "− Deaths",
      "+ 10 per bonus",
      "+ 50 per win",
    ]);
    expect(texts(page, ".note")[0]).toBe(
      "Bonuses: Most first bloods (FB), Fewest deaths (LD)."
    );
  });

  it("shows the season picked in the header", async () => {
    const page = await open("/o/alsace-arena?season=1", SEASONS, 1);

    expect(text(page.querySelector(".eyebrow"))).toBe(
      "Season 1 · Spike Tournament"
    );
    expect(texts(page, "header select option")).toStrictEqual([
      "Season 3",
      "Season 1",
    ]);
  });

  it("goes to another season from the header", async () => {
    const page = await open();
    const picker = page.querySelector<HTMLSelectElement>("header select");

    if (!picker) {
      throw new Error("No season picker");
    }

    picker.value = "1";
    picker.dispatchEvent(new Event("change"));

    await vi.waitFor(() => {
      expect(TestBed.inject(Router).url).toBe("/o/alsace-arena?season=1");
    });
    await answer(`${API}/seasons/1/leaderboard`, LEADERBOARD);
    await answer(`${API}/seasons/1/summary`, SUMMARY);
    await answer(`${API}/seasons/1/rules`, RULES);
  });

  it("says when there's no season yet", async () => {
    const page = await open("/o/alsace-arena", []);

    expect(text(page.querySelector(".notice"))).toBe(
      "No season has started yet."
    );
    expect(page.querySelector("header select")).toBeNull();
  });
});
