import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import type { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  ALSACE,
  answer,
  LEADERBOARD,
  openAlsace,
  rendered,
  SEASONS,
  testProviders,
  texts,
} from "../../testing";
import type { PublicPlayer } from "../api";
import type { PlayerCard } from "./players-list";
import { cardsOf, shownCards, tierOf } from "./players-list";

const PLAYERS: PublicPlayer[] = [
  { aliases: ["kes"], id: "kestrel", riotId: "Kestrel#EUW" },
  { aliases: [], id: "wren", riotId: "Wren#EUW" },
];

const CARDS: PlayerCard[] = cardsOf(LEADERBOARD.standings, PLAYERS);

/** The Riot IDs a search finds, in the leaderboard's order. */
const names = (search: string): string[] =>
  shownCards(CARDS, { search, sort: "points", tier: "" }).map(
    (card) => card.standing.riotId
  );

describe("the Players list", () => {
  it("adds each player's aliases to their standing", () => {
    expect(CARDS[0]).toMatchObject({ aliases: ["kes"] });
    expect(CARDS[1]?.aliases).toStrictEqual([]);
  });

  it("finds players by Riot ID or alias, never by Discord", () => {
    expect(names("")).toStrictEqual(["Kestrel#EUW", "Wren#EUW"]);
    expect(names("WREN")).toStrictEqual(["Wren#EUW"]);
    expect(names("kestrel.gg")).toStrictEqual([]);
    expect(names("kes")).toStrictEqual(["Kestrel#EUW"]);
  });

  it("sorts and filters by rank", () => {
    expect(
      shownCards(CARDS, { search: "", sort: "kda", tier: "" }).map(
        (card) => card.standing.riotId
      )
    ).toStrictEqual(["Wren#EUW", "Kestrel#EUW"]);
    expect(
      shownCards(CARDS, { search: "", sort: "points", tier: "Radiant" })
    ).toHaveLength(1);
    expect(tierOf("Diamond 3")).toBe("Diamond");
    expect(tierOf(null)).toBeNull();
  });
});

describe("the Players page", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  beforeEach(async () => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    harness = await openAlsace("/org/alsace-arena/players", SEASONS);
    await answer(
      harness,
      `/api/orgs/${ALSACE.slug}/seasons/3/leaderboard`,
      LEADERBOARD
    );
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(`/api/orgs/${ALSACE.slug}/players`).flush(PLAYERS);
    });
    harness.detectChanges();
    page = rendered(harness);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it("shows a card per player, linking to their profile", () => {
    expect(texts(page, ".card .name")).toStrictEqual([
      "Kestrel#EUW",
      "Wren#EUW",
    ]);
    expect(texts(page, ".card .names")).toStrictEqual(["also kes"]);
    expect(page.querySelector(".card a")?.getAttribute("href")).toBe(
      "/org/alsace-arena/players/kestrel"
    );
  });
});
