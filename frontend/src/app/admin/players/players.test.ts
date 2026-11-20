import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { Router } from "@angular/router";
import { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { Season } from "../../results/api";
import type { Me } from "../../shared/session";
import {
  ALSACE,
  ME,
  openAs,
  rendered,
  SEASONS,
  testProviders,
  text,
  texts,
} from "../../testing";
import type { AdminPlayer, RankOption } from "../api";
import { aliasesFrom, inputFrom } from "./players";

const API = `/api/orgs/${ALSACE.slug}/admin/players`;

const RANKS: RankOption[] = [
  { color: "#D6C07E", id: 25, name: "Radiant" },
  { color: "#9367BE", id: 18, name: "Diamond 3" },
];

const PLAYERS: AdminPlayer[] = [
  {
    aliases: [
      { alias: "Bram", kind: "NICKNAME" },
      { alias: "Bramble#OLD", kind: "PREVIOUS_RIOT_ID" },
    ],
    discordUsername: "bramble.gg",
    expectedEmail: null,
    id: "bramble",
    rank: RANKS[1] ?? null,
    riotId: "Bramble#EUW",
  },
  {
    aliases: [],
    discordUsername: null,
    expectedEmail: null,
    id: "wren",
    rank: null,
    riotId: "Wren#EUW",
  },
];

const PLAYER_ONLY: Me = {
  ...ME,
  memberships: [
    {
      name: "Alsace Arena",
      owner: false,
      playerId: null,
      role: "PLAYER",
      slug: ALSACE.slug,
    },
  ],
};

let http: HttpTestingController;

/** Opens the Players page as an admin, with its players and the ranks. */
const openPlayers = async (
  seasons: Season[] = []
): Promise<RouterTestingHarness> => {
  const harness = await openAs("/org/alsace-arena/admin/players", ME, seasons);
  await vi.waitFor(() => {
    harness.detectChanges();
    http.expectOne((request) => request.url === API).flush(PLAYERS);
    http.expectOne("/api/ranks").flush(RANKS);
  });
  harness.detectChanges();

  return harness;
};

describe("the admin pages", () => {
  beforeEach(() => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it("send someone signed out to sign in, and back", async () => {
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl("/org/alsace-arena/admin/players");

    await vi.waitFor(() => {
      http
        .expectOne("/api/me")
        .flush(null, { status: 401, statusText: "Unauthorized" });
    });
    await navigation;

    expect(TestBed.inject(Router).url).toBe(
      "/sign-in?from=%2Forg%2Falsace-arena%2Fadmin%2Fplayers"
    );
  });

  it("send a player who isn't an admin to the public pages", async () => {
    const harness = await openAs("/org/alsace-arena/admin", PLAYER_ONLY);

    expect(TestBed.inject(Router).url).toBe("/org/alsace-arena");
    expect(rendered(harness).querySelector("header .mode")).toBeNull();
  });

  it("show the admin links in the header, without the season picker", async () => {
    const harness = await openPlayers(SEASONS);
    const page = rendered(harness);

    expect(text(page.querySelector("h1"))).toBe("Players");
    expect(texts(page, "header nav.nav a")).toStrictEqual([
      "Scores",
      "Players",
      "Seasons",
      "Scoring",
      "Members",
      "Claims",
      "Branding",
    ]);
    expect(page.querySelector("header select")).toBeNull();
  });
});

describe("the Site | Admin switch", () => {
  beforeEach(() => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it("shows admins which side of the organisation is on screen", async () => {
    const harness = await openAs("/org/alsace-arena/admin/players", ME);
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne((request) => request.url === API).flush(PLAYERS);
      http.expectOne("/api/ranks").flush(RANKS);
    });
    harness.detectChanges();
    const page = rendered(harness);

    expect(texts(page, "header .mode a")).toStrictEqual(["Site", "Admin"]);
    expect(text(page.querySelector("header .mode [aria-current]"))).toBe(
      "Admin"
    );

    await harness.navigateByUrl("/org/alsace-arena");
    harness.detectChanges();

    expect(text(page.querySelector("header .mode [aria-current]"))).toBe(
      "Site"
    );
    expect(texts(page, "header nav.nav a")).toStrictEqual([
      "Leaderboard",
      "Matches",
      "Players",
    ]);
  });
});

describe("the Players page", () => {
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  beforeEach(async () => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    harness = await openPlayers();
    page = rendered(harness);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  const rows = (): (string | undefined)[][] =>
    [...page.querySelectorAll("tbody tr")].map((row) => texts(row, "td"));

  it("lists the players with their Discord, aliases and rank", () => {
    expect(text(page.querySelector("#all-title"))).toBe("All players · 2");
    expect(rows()).toStrictEqual([
      [
        "Bramble#EUW",
        "bramble.gg",
        "Bram, Bramble#OLD",
        "Diamond 3",
        "Edit Bramble#EUW",
      ],
      ["Wren#EUW", "–", "–", "", "Edit Wren#EUW"],
    ]);
  });

  it("searches as you type", async () => {
    const search = page.querySelector<HTMLInputElement>("input[type=search]");

    if (search) {
      search.value = "wren";
      search.dispatchEvent(new Event("input"));
    }

    await vi.waitFor(() => {
      harness.detectChanges();
      http
        .expectOne(
          (request) => request.url === API && request.params.get("q") === "wren"
        )
        .flush([]);
    });
    harness.detectChanges();

    expect(page.querySelector("tbody")).toBeNull();
    expect(text(page.querySelector("section:last-of-type p"))).toBe(
      "No player matches “wren”."
    );
  });

  it("adds a player, then lists them", async () => {
    const form = page.querySelector<HTMLFormElement>("form.add");

    const set = (name: string, value: string): void => {
      const field = form?.querySelector<HTMLInputElement | HTMLSelectElement>(
        `[name=${name}]`
      );

      if (field) {
        field.value = value;
      }
    };

    set("riotId", " Moth#MOTH ");
    set("aliases", "Mo, mo");
    set("rank", "25");
    form?.requestSubmit();

    const request = http.expectOne(
      (each) => each.url === API && each.method === "POST"
    );

    expect(request.request.body).toStrictEqual({
      aliases: [{ alias: "Mo", kind: "NICKNAME" }],
      discordUsername: null,
      expectedEmail: null,
      rankId: 25,
      riotId: "Moth#MOTH",
    });

    request.flush({ ...PLAYERS[1], id: "moth", riotId: "Moth#MOTH" });
    await vi.waitFor(() => {
      harness.detectChanges();
      http
        .expectOne((each) => each.url === API && each.method === "GET")
        .flush(PLAYERS);
    });
    harness.detectChanges();

    expect(text(page.querySelector("[role=status]"))).toBe("Moth was added.");
  });

  it("edits a row, keeping a previous Riot ID's kind", async () => {
    page.querySelector<HTMLButtonElement>("tbody tr button")?.click();
    harness.detectChanges();

    const row = page.querySelector("tr.editing");

    const aliases = row?.querySelector<HTMLInputElement>(
      "[aria-label=Aliases]"
    );

    expect(aliases?.value).toBe("Bram, Bramble#OLD");

    if (aliases) {
      aliases.value = "Bramble#OLD, Thorn";
    }

    [...(row?.querySelectorAll("button") ?? [])]
      .find((button) => text(button) === "Save")
      ?.click();

    const request = http.expectOne((each) => each.method === "PUT");

    expect(request.request.url).toBe(`${API}/bramble`);
    expect(request.request.body).toStrictEqual({
      aliases: [
        { alias: "Bramble#OLD", kind: "PREVIOUS_RIOT_ID" },
        { alias: "Thorn", kind: "NICKNAME" },
      ],
      discordUsername: "bramble.gg",
      expectedEmail: null,
      rankId: 18,
      riotId: "Bramble#EUW",
    });
    request.flush(PLAYERS[0]);
    await vi.waitFor(() => {
      harness.detectChanges();
      http
        .expectOne((each) => each.url === API && each.method === "GET")
        .flush(PLAYERS);
    });
    harness.detectChanges();

    expect(page.querySelector("tr.editing")).toBeNull();
  });

  it("says when the Riot ID is taken", async () => {
    const form = page.querySelector<HTMLFormElement>("form.add");
    const riotId = form?.querySelector<HTMLInputElement>("[name=riotId]");

    if (riotId) {
      riotId.value = "Wren#EUW";
    }

    form?.requestSubmit();
    http
      .expectOne((each) => each.method === "POST")
      .flush(null, { status: 409, statusText: "Conflict" });
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "Another player already has this Riot ID."
      );
    });
  });
});

describe("a player's aliases", () => {
  it("are read from a line, each once, never the Riot ID", () => {
    expect(
      aliasesFrom(
        " Bram , bram, ,Kestrel#EUW, Old#1",
        [{ alias: "old#1", kind: "PREVIOUS_RIOT_ID" }],
        "Kestrel#EUW"
      )
    ).toStrictEqual([
      { alias: "Bram", kind: "NICKNAME" },
      { alias: "Old#1", kind: "PREVIOUS_RIOT_ID" },
    ]);
  });

  it("make a request with no rank and no Discord when left empty", () => {
    expect(
      inputFrom(
        { aliases: "", discord: "  ", email: " ", rank: "", riotId: "A#1" },
        null
      )
    ).toStrictEqual({
      aliases: [],
      discordUsername: null,
      expectedEmail: null,
      rankId: null,
      riotId: "A#1",
    });
  });
});
