import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import type { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SessionService } from "../../shared/session";
import {
  ALSACE,
  answer,
  LEADERBOARD,
  ME,
  openAlsace,
  rendered,
  SEASONS,
  testProviders,
  text,
  texts,
} from "../../testing";
import type { PlayerSeason, Standing } from "../api";
import { barsOf, kdaOf } from "./player";

/** The leaderboard's standing at this place; the fixture has two. */
const standingAt = (index: number): Standing => {
  const standing = LEADERBOARD.standings[index];

  if (!standing) {
    throw new Error(`The leaderboard has no standing ${index}`);
  }

  return standing;
};

const KESTREL = standingAt(0);
const WREN = standingAt(1);

const PROFILE: PlayerSeason = {
  ahead: { points: 5486, riotId: "Kestrel#EUW" },
  columns: LEADERBOARD.columns,
  matches: [
    {
      matchDate: "2026-11-13",
      matchNumber: 1,
      standing: { ...WREN, points: 100 },
    },
    {
      matchDate: null,
      matchNumber: 2,
      standing: { ...WREN, points: 163, rank: 1 },
    },
  ],
  player: {
    aliases: ["wrenny"],
    currentRank: null,
    discordUsername: "wren.gg",
    id: "wren",
    riotId: "Wren#EUW",
  },
  players: 2,
  season: { name: "Season 3", number: 3 },
  standing: WREN,
};

describe("a player's matches", () => {
  it("draw each bar against the best match", () => {
    expect(
      barsOf(PROFILE.matches).map((bar) => [bar.height, bar.best])
    ).toStrictEqual([
      [100 / 163, false],
      [1, true],
    ]);
    expect(barsOf([])).toStrictEqual([]);
  });

  it("write kills, deaths and assists, or a dash", () => {
    expect(kdaOf({ matchDate: null, matchNumber: 1, standing: KESTREL })).toBe(
      "33 / 26 / 8"
    );
    expect(
      kdaOf({
        matchDate: null,
        matchNumber: 1,
        standing: { ...KESTREL, totals: {} },
      })
    ).toBe("–");
  });
});

describe("the player page", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  beforeEach(async () => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    harness = await openAlsace("/org/alsace-arena/players/wren", SEASONS);
    await answer(
      harness,
      `/api/orgs/${ALSACE.slug}/seasons/3/players/wren`,
      PROFILE
    );
    harness.detectChanges();
    page = rendered(harness);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it("shows who the player is and where they stand", () => {
    expect(text(page.querySelector("h1"))).toBe("Wren");
    expect(text(page.querySelector(".who-text p"))).toBe(
      "Wren#EUW · Discord wren.gg · also wrenny"
    );
    expect(text(page.querySelector(".tiles div"))).toBe(
      "Position #2 of 2 players"
    );
    expect(text(page.querySelectorAll(".tiles div")[1])).toContain(
      "5,486 behind Kestrel"
    );
  });

  it("links back to every player", () => {
    expect(page.querySelector(".back")?.getAttribute("href")).toBe(
      "/org/alsace-arena/players"
    );
  });

  it("lists the matches with their place and points, linking to each", () => {
    expect(texts(page, "tbody th")).toStrictEqual(["Match 1", "Match 2"]);
    expect(page.querySelector("tbody a")?.getAttribute("href")).toBe(
      "/org/alsace-arena/matches/1"
    );
    expect(texts(page, "tbody tr:last-child td").at(-2)).toBe("#1");
  });

  it("shows no edit form on someone else's profile", () => {
    expect(page.querySelector(".you")).toBeNull();
    expect(page.querySelector(".edit-open")).toBeNull();
  });
});

describe("the signed-in player's own profile", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  beforeEach(async () => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    const loading = TestBed.inject(SessionService).load();

    http.expectOne("/api/me").flush({
      ...ME,
      memberships: [
        {
          name: "Alsace Arena",
          owner: false,
          playerId: "wren",
          role: "PLAYER",
          slug: "alsace-arena",
        },
      ],
    });
    await loading;
    harness = await openAlsace("/org/alsace-arena/players/wren", SEASONS);
    await answer(
      harness,
      `/api/orgs/${ALSACE.slug}/seasons/3/players/wren`,
      PROFILE
    );
    harness.detectChanges();
    page = rendered(harness);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it("marks it as theirs and saves their rank, sending the rest to My account", async () => {
    expect(text(page.querySelector(".you"))).toBe("You");

    page.querySelector<HTMLButtonElement>(".edit-open")?.click();
    await vi.waitFor(() => {
      harness.detectChanges();
      http
        .expectOne("/api/ranks")
        .flush([{ color: null, id: 18, name: "Gold 2" }]);
    });
    harness.detectChanges();
    const rank = page.querySelector<HTMLSelectElement>(".edit select");

    if (rank) {
      rank.value = "18";
    }

    expect(page.querySelector(".edit a")?.getAttribute("href")).toBe(
      "/account"
    );

    page.querySelector<HTMLFormElement>("form.edit")?.requestSubmit();

    const save = await vi.waitFor(() =>
      http.expectOne(`/api/orgs/${ALSACE.slug}/claim/player`)
    );

    expect(save.request.body).toStrictEqual({ rankId: 18 });
    save.flush({});
    await vi.waitFor(() => {
      harness.detectChanges();
      http
        .expectOne(`/api/orgs/${ALSACE.slug}/seasons/3/players/wren`)
        .flush(PROFILE);
    });
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "Your rank is saved."
      );
    });
  });
});
