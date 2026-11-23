import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import type { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  ALSACE,
  ME,
  openAs,
  rendered,
  testProviders,
  text,
  texts,
} from "../testing";
import type { MyMatch, MyMatches } from "./api";
import { bySeason } from "./my-matches";

const API = `/api/orgs/${ALSACE.slug}`;

const match = (
  id: number,
  seasonNumber: number,
  extra: Partial<MyMatch> = {}
): MyMatch => ({
  cancelled: false,
  confirmed: 1,
  date: "2026-12-05",
  id,
  location: "Discord #matchs",
  locked: false,
  number: id,
  places: 10,
  position: null,
  replyBy: null,
  seasonName: `Season ${seasonNumber}`,
  seasonNumber,
  startTime: "20:30",
  status: null,
  ...extra,
});

const MINE: MyMatches = {
  matches: [
    match(1, 9),
    match(2, 9, { position: 2, status: "WAITING" }),
    match(3, 10, { cancelled: true }),
  ],
  player: true,
};

describe("matches by season", () => {
  it("keeps the order they come in", () => {
    expect(
      bySeason(MINE.matches).map((season) => [
        season.name,
        season.matches.length,
      ])
    ).toStrictEqual([
      ["Season 9", 2],
      ["Season 10", 1],
    ]);
  });
});

describe("the My matches page", () => {
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

  const open = async (
    answer: (request: ReturnType<HttpTestingController["expectOne"]>) => void
  ): Promise<{ harness: RouterTestingHarness; page: HTMLElement }> => {
    const harness = await openAs("/org/alsace-arena/me/matches", ME);
    await vi.waitFor(() => {
      harness.detectChanges();
      answer(http.expectOne(`${API}/me/matches`));
    });
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(rendered(harness).querySelector("[aria-live]")).toBeNull();
    });

    return { harness, page: rendered(harness) };
  };

  it("lists the upcoming matches by season, and answers one", async () => {
    const { harness, page } = await open((request) => request.flush(MINE));

    expect(texts(page, ".seasons > li > h2")).toStrictEqual([
      "Season 9",
      "Season 10",
    ]);
    expect(texts(page, ".match-head .pill")).toStrictEqual([
      "Waiting list · #2",
      "Called off",
    ]);

    [...page.querySelectorAll<HTMLButtonElement>("button")]
      .find((button) => text(button) === "I'm in Match 1")
      ?.click();
    const answered = http.expectOne(`${API}/matches/1/attendance`);

    expect(answered.request.body).toStrictEqual({ answer: "YES" });
    answered.flush({
      ...MINE,
      matches: [match(1, 9, { status: "CONFIRMED" }), ...MINE.matches.slice(1)],
    });
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "You're in for match 1."
      );
    });
  });

  it("asks for a profile first", async () => {
    const { page } = await open((request) =>
      request.flush({ matches: [], player: false })
    );

    expect(text(page.querySelector("#profile-title"))).toBe(
      "You need a player profile first"
    );
  });

  it("invites someone who isn't a member to join", async () => {
    const { page } = await open((request) =>
      request.flush(null, { status: 403, statusText: "Forbidden" })
    );

    expect(text(page.querySelector("#join-title"))).toBe(
      "Play at Alsace Arena?"
    );
  });
});
