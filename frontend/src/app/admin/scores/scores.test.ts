import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { Router } from "@angular/router";
import { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { MatchCard, Rules, Season } from "../../results/api";
import type { Me } from "../../shared/session";
import {
  ALSACE,
  answer,
  MATCH_CARDS,
  ME,
  rendered,
  SEASONS,
  testProviders,
  text,
  texts,
} from "../../testing";
import type { AdminPlayer } from "../api";
import { emptyRow, linesOf, problemOf } from "./scores-form";
import type { GameScores, PreviousRoster } from "./scores-form";

const ORG = `/api/orgs/${ALSACE.slug}`;
const GAMES = `${ORG}/admin/seasons/3/matches`;

const RULES: Rules = {
  bonuses: [
    {
      award: "HIGHEST",
      code: "FB",
      label: "Most first bloods",
      points: 10,
      stat: "FIRST_BLOODS",
      threshold: null,
    },
    {
      award: "MANUAL",
      code: "ACE",
      label: "Ace",
      points: 20,
      stat: null,
      threshold: null,
    },
  ],
  kdaPoints: 0,
  pointsPerWin: 50,
  stats: [
    { countable: true, points: 1, recorded: true, stat: "KILLS" },
    { countable: false, points: 0, recorded: true, stat: "ADR" },
    { countable: true, points: 1, recorded: true, stat: "FIRST_BLOODS" },
    { countable: true, points: 1, recorded: false, stat: "ECON_RATING" },
  ],
};

const PLAYERS: AdminPlayer[] = [
  {
    aliases: [],
    discordUsername: null,
    expectedEmail: null,
    id: "kestrel",
    linked: false,
    played: false,
    rank: null,
    riotId: "Kestrel#EUW",
  },
  {
    aliases: [],
    discordUsername: null,
    expectedEmail: null,
    id: "wren",
    linked: false,
    played: false,
    rank: null,
    riotId: "Wren#EUW",
  },
];

const ROSTER: PreviousRoster = {
  gameNumber: 2,
  matchNumber: 1,
  players: [
    { playerId: "kestrel", riotId: "Kestrel#EUW" },
    { playerId: "wren", riotId: "Wren#EUW" },
  ],
};

const NOT_SAVED: GameScores = {
  gameNumber: 1,
  lines: [],
  matchDate: "2026-11-20",
  matchNumber: 2,
  saved: false,
  season: 3,
};

const SAVED: GameScores = {
  gameNumber: 1,
  lines: [
    {
      bonuses: ["FB", "ACE"],
      playerId: "kestrel",
      points: 92,
      riotId: "Kestrel#EUW",
      stats: { ADR: 150.5, FIRST_BLOODS: 3, KILLS: 20 },
      win: true,
    },
  ],
  matchDate: "2026-11-13",
  matchNumber: 1,
  saved: true,
  season: 3,
};

let http: HttpTestingController;
let harness: RouterTestingHarness;
let page: HTMLElement;

/** Answers one request once the page makes it. */
/** What the stand-in back end answers. */
type Answer =
  | Me
  | Season[]
  | Rules
  | MatchCard[]
  | AdminPlayer[]
  | GameScores
  | PreviousRoster;

const reply = async (
  url: string,
  body: Answer,
  method = "GET"
): Promise<void> => {
  await vi.waitFor(() => {
    harness.detectChanges();
    http
      .expectOne((request) => request.url === url && request.method === method)
      .flush(body);
  });
  harness.detectChanges();
};

/** Opens Enter scores as an admin and answers everything it asks for. */
const open = async (
  url = "/org/alsace-arena/admin",
  game: GameScores = NOT_SAVED
): Promise<void> => {
  harness = await RouterTestingHarness.create();
  const navigation = harness.navigateByUrl(url);

  await reply("/api/me", ME);
  await answer(harness, ORG, ALSACE);
  await navigation;
  await answer(harness, `${ORG}/seasons`, SEASONS);
  await reply(`${ORG}/seasons/3/rules`, RULES);
  await reply(`${ORG}/seasons/3/matches`, MATCH_CARDS);
  await reply(`${ORG}/admin/players`, PLAYERS);
  await reply(`${GAMES}/${game.matchNumber}/games/${game.gameNumber}`, game);

  if (!game.saved) {
    await reply(
      `${GAMES}/${game.matchNumber}/games/${game.gameNumber}/previous-roster`,
      ROSTER
    );
  }

  page = rendered(harness);
};

/** Types into a line's stat box. */
const type = (line: number, label: string, value: string): void => {
  const rows = page.querySelectorAll("tbody tr");

  const box = rows[line]?.querySelector<HTMLInputElement>(
    `input[aria-label^="${label}"]`
  );

  if (box) {
    box.value = value;
    box.dispatchEvent(new Event("input"));
  }

  harness.detectChanges();
};

const button = (label: string): HTMLButtonElement | undefined =>
  [...page.querySelectorAll<HTMLButtonElement>("button")].find(
    (each) => text(each) === label
  );

describe("Enter scores", () => {
  beforeEach(() => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it("opens on the next match to enter, with the previous game's players", async () => {
    await open();

    expect(text(page.querySelector("h1"))).toBe("Enter scores");
    expect(text(page.querySelector("#game-title"))).toBe("Game 1 · Match 2");
    expect(text(page.querySelector(".state"))).toBe("Not entered yet");
    expect(
      [...page.querySelectorAll<HTMLSelectElement>("tbody select")]
        .slice(0, 3)
        .map((select) => select.value)
    ).toStrictEqual(["kestrel", "wren", ""]);
    expect(page.querySelectorAll("tbody tr")).toHaveLength(
      ALSACE.playersPerMatch
    );
  });

  it("asks only for the stats the season records, and says which it doesn't", async () => {
    await open();

    expect(texts(page, "thead th")).toStrictEqual([
      "#",
      "Player",
      "K",
      "ADR",
      "FB",
      "Won",
      "FB",
      "ACE",
      "Points",
    ]);
    expect(text(page.querySelector(".note"))).toBe(
      "Not recorded this season: Economy rating."
    );
  });

  it("saves the game, with only the manual bonuses ticked", async () => {
    await open();
    type(0, "Kills", "20");
    type(0, "Average damage", "150,5");
    page
      .querySelector<HTMLInputElement>(
        'tbody tr input[aria-label="Ace, Kestrel"]'
      )
      ?.click();
    page
      .querySelector<HTMLInputElement>(
        'tbody tr input[aria-label="Kestrel won this game"]'
      )
      ?.click();
    harness.detectChanges();

    expect(text(page.querySelector(".state"))).toBe("Unsaved changes");

    button("Save game 1")?.click();
    const request = http.expectOne((each) => each.method === "PUT");

    expect(request.request.body).toStrictEqual({
      lines: [
        {
          manualBonuses: ["ACE"],
          playerId: "kestrel",
          riotId: null,
          stats: { ADR: 150.5, KILLS: 20 },
          win: true,
        },
        {
          manualBonuses: [],
          playerId: "wren",
          riotId: null,
          stats: {},
          win: false,
        },
      ],
      matchDate: "2026-11-20",
    });

    request.flush({ ...SAVED, gameNumber: 1, matchNumber: 2 });
    await reply(`${ORG}/seasons/3/matches`, MATCH_CARDS);
    await reply(`${ORG}/admin/players`, PLAYERS);

    expect(text(page.querySelector("[role=status]"))).toBe(
      "Game 1 of match 2 saved. The leaderboard is up to date."
    );
    expect(TestBed.inject(Router).url).toBe(
      "/org/alsace-arena/admin/scores?game=1&match=2"
    );
  });

  it("refuses a number that isn't one, before sending anything", async () => {
    await open();
    type(1, "First bloods", "2.5");

    expect(
      page.querySelectorAll("tbody tr")[1]?.querySelector("[aria-invalid=true]")
    ).not.toBeNull();

    button("Save game 1")?.click();
    harness.detectChanges();

    expect(text(page.querySelector("[role=status]"))).toBe(
      "Line 2: First bloods is a whole number, such as 3."
    );
  });

  it("checks the automatic bonuses without saving", async () => {
    await open();
    type(0, "First bloods", "3");
    button("Check bonuses")?.click();
    const request = http.expectOne((each) => each.url.endsWith("/bonus-check"));

    expect(request.request.body).toStrictEqual([
      { stats: { FIRST_BLOODS: 3 }, win: false },
      { stats: {}, win: false },
    ]);

    request.flush([["FB"], []]);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(
        page.querySelector<HTMLInputElement>(
          'input[aria-label^="Most first bloods, Kestrel"]'
        )?.checked
      ).toBeTruthy();
    });
  });

  it("adds a player who isn't on the list by Riot ID", async () => {
    await open();
    button("New player")?.click();
    harness.detectChanges();

    const riotId = page.querySelector<HTMLInputElement>(
      'input[aria-label^="New player"]'
    );

    if (riotId) {
      riotId.value = "Moth#MOTH";
      riotId.dispatchEvent(new Event("input"));
    }

    harness.detectChanges();
    button("Save game 1")?.click();
    const request = http.expectOne((each) => each.method === "PUT");

    expect(request.request.body.lines[2]).toStrictEqual({
      manualBonuses: [],
      playerId: null,
      riotId: "Moth#MOTH",
      stats: {},
      win: false,
    });

    request.flush({ ...SAVED, matchNumber: 2 });
    await reply(`${ORG}/seasons/3/matches`, MATCH_CARDS);
    await reply(`${ORG}/admin/players`, PLAYERS);
  });

  it("shows a saved game, and clears it after asking", async () => {
    await open("/org/alsace-arena/admin/scores?match=1&game=1", SAVED);
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(true);

    expect(text(page.querySelector(".state"))).toBe("Saved");
    expect(
      page.querySelector<HTMLInputElement>(
        'input[aria-label^="Kills, Kestrel"]'
      )?.value
    ).toBe("20");
    expect(texts(page, "tbody tr:first-child .points")).toStrictEqual(["92"]);

    button("Clear game")?.click();
    http
      .expectOne(
        (each) => each.method === "DELETE" && each.url === `${GAMES}/1/games/1`
      )
      .flush(null);
    await reply(`${GAMES}/1/games/1`, { ...SAVED, lines: [], saved: false });
    await reply(`${ORG}/seasons/3/matches`, MATCH_CARDS);
    await reply(`${GAMES}/1/games/1/previous-roster`, ROSTER);

    expect(confirm).toHaveBeenCalledOnce();
  });
});

describe("a line's place", () => {
  it("is the line's number unless one is typed", () => {
    const first = emptyRow({ kind: "new", riotId: "Kestrel#EUW" });

    const second = {
      ...emptyRow({ kind: "new", riotId: "Wren#EUW" }),
      stats: { PLACE: "1" },
    };

    const third = emptyRow({ kind: "new", riotId: "Heron#EUW" });

    expect(
      linesOf(
        [first, emptyRow(), second, third],
        ["PLACE", "KILLS"],
        new Set()
      ).map((line) => line.stats)
    ).toStrictEqual([{ PLACE: 1 }, { PLACE: 1 }, { PLACE: 4 }]);
  });
});

describe("a new player on the Scores form", () => {
  it("needs Riot's format; a known player doesn't", () => {
    expect(
      problemOf([emptyRow({ kind: "new", riotId: "Newcomer" })], [])
    ).toStrictEqual({ kind: "riotId", row: 1 });
    expect(
      problemOf([emptyRow({ kind: "new", riotId: "Newcomer#EUW" })], [])
    ).toBeNull();
    expect(
      problemOf([emptyRow({ kind: "known", playerId: "drrako" })], [])
    ).toBeNull();
  });
});
