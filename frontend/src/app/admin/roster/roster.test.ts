import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import type { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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
import type { Roster, RosterLine } from "../api";
import { availableMatching } from "./roster";

const ADMIN = `/api/orgs/${ALSACE.slug}/admin`;
const API = `${ADMIN}/matches/33`;

const line = (
  playerId: string,
  riotId: string,
  extra: Partial<RosterLine> = {}
): RosterLine => ({
  account: true,
  answeredAt: "2026-11-21T18:00:00Z",
  answeredBy: "Kestrel",
  playerId,
  riotId,
  source: "APP",
  ...extra,
});

const ROSTER: Roster = {
  autoPromote: true,
  confirmed: [line("kestrel", "Kestrel#EUW")],
  date: "2026-12-05",
  declined: [],
  id: 33,
  location: "Discord #matchs",
  locked: false,
  noAnswer: [
    line("wren", "Wren#EUW", {
      account: false,
      answeredAt: null,
      answeredBy: null,
      source: null,
    }),
  ],
  number: 3,
  places: 2,
  played: false,
  replyBy: "2026-12-03",
  seasonName: "Season 3",
  seasonNumber: 3,
  startTime: "20:30:00",
  status: "PLANNED",
  waiting: [
    line("heron", "Heron#EUW", { answeredBy: "Camille", source: "ADMIN" }),
  ],
};

describe("the available players", () => {
  it("are everyone who hasn't answered, matching the search", () => {
    const roster: Roster = {
      ...ROSTER,
      noAnswer: [...ROSTER.noAnswer, line("moth", "Moth#EUW")],
    };

    expect(
      availableMatching(roster, "").map((each) => each.playerId)
    ).toStrictEqual(["wren", "moth"]);
    expect(availableMatching(roster, " MOTH ")).toHaveLength(1);
    expect(availableMatching(roster, "kes")).toStrictEqual([]);
  });
});

describe("the Match roster page", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  const button = (label: string): HTMLButtonElement | undefined =>
    [...page.querySelectorAll<HTMLButtonElement>("button")].find((each) =>
      text(each)?.startsWith(label)
    );

  beforeEach(async () => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    harness = await openAs(
      "/org/alsace-arena/admin/matches/33/roster",
      ME,
      SEASONS
    );
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(`${API}/roster`).flush(ROSTER);
    });
    harness.detectChanges();
    page = rendered(harness);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it("shows each list, and how each answer came in", () => {
    expect(text(page.querySelector("h1"))).toBe("Match 3 roster · Season 3");
    expect(texts(page, "section h2")).toStrictEqual([
      "Places and lock",
      "Confirmed · 1 of 2",
      "Waiting list · 1",
      "Can't make it · 0",
      "Available · 1",
    ]);
    expect(
      text(page.querySelector("#waiting-title ~ ol .line-text"))
    ).toContain("By Camille");
    expect(text(page.querySelector("#noAnswer-title ~ ol .pill"))).toBe(
      "No account"
    );
  });

  it("moves a player, and saves the places and the lock", async () => {
    button("Confirm Heron")?.click();
    const moved = http.expectOne(`${API}/roster/heron`);

    expect(moved.request.method).toBe("PUT");
    expect(moved.request.body).toStrictEqual({ status: "CONFIRMED" });
    moved.flush(ROSTER);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "Heron is confirmed."
      );
    });

    const places = page.querySelector<HTMLInputElement>("[name=places]");

    if (places) {
      places.value = "3";
    }

    page.querySelector<HTMLFormElement>("form.places")?.requestSubmit();
    const saved = http.expectOne(`${ADMIN}/seasons/3/schedule/3`);

    expect(saved.request.method).toBe("PUT");
    expect(saved.request.body).toStrictEqual({
      autoPromote: null,
      cancelled: null,
      date: "2026-12-05",
      location: "Discord #matchs",
      places: 3,
      replyBy: "2026-12-03",
      startTime: "20:30:00",
    });
    saved.flush({});
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(`${API}/roster`).flush({ ...ROSTER, places: 3 });
    });
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "The places are saved."
      );
    });

    button("Lock the roster")?.click();
    const locked = http.expectOne(`${API}/lock`);

    expect(locked.request.method).toBe("POST");
    locked.flush({ ...ROSTER, locked: true });
  });

  it("reorders the waiting list and turns automatic moves off", async () => {
    await vi.waitFor(() => {
      harness.detectChanges();
    });

    const two: Roster = {
      ...ROSTER,
      waiting: [
        ...ROSTER.waiting,
        line("moth", "Moth#EUW", { answeredBy: "Camille", source: "ADMIN" }),
      ],
    };

    harness.detectChanges();
    page.querySelector<HTMLInputElement>("[name=autoPromote]")?.click();
    const off = http.expectOne(`${ADMIN}/seasons/3/schedule/3`);

    expect(off.request.body).toMatchObject({ autoPromote: false, places: 2 });
    off.flush({});
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(`${API}/roster`).flush(two);
    });
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(button("↓ Move Heron down")).toBeDefined();
    });

    button("↓ Move Heron down")?.click();
    const moved = http.expectOne(`${API}/waiting`);

    expect(moved.request.body).toStrictEqual({ players: ["moth", "heron"] });
    moved.flush(two);
  });

  it("reminds the players who haven't answered", async () => {
    button("Remind them")?.click();
    const reminded = http.expectOne(`${API}/remind`);

    expect(reminded.request.method).toBe("POST");
    reminded.flush({ players: 1 });
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "1 player reminded."
      );
    });
  });
});
