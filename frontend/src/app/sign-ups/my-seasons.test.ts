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
import type { MyMatch, MySeason, MySeasons } from "./api";

const API = `/api/orgs/${ALSACE.slug}/me/seasons`;
const MATCHES = `/api/orgs/${ALSACE.slug}/me/matches`;

const MATCH: MyMatch = {
  cancelled: false,
  confirmed: 9,
  date: "2026-12-05",
  id: 41,
  location: "Discord #matchs",
  locked: false,
  number: 2,
  places: 10,
  position: null,
  replyBy: "2026-12-03",
  seasonName: "Season 9",
  seasonNumber: 9,
  startTime: "20:30:00",
  status: null,
};

const SEASON: MySeason = {
  endsOn: "2026-12-15",
  name: "Season 9",
  number: 9,
  signedUp: false,
  startsOn: "2026-11-25",
  takesSignUps: true,
};

describe("the My seasons page", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  const answerMatches = async (matches: MyMatch[]): Promise<void> => {
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(MATCHES).flush({ matches });
    });
  };

  const open = async (
    mine: MySeasons,
    matches: MyMatch[] = []
  ): Promise<void> => {
    harness = await openAs("/org/alsace-arena/me/seasons", ME);
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(API).flush(mine);
    });

    if (mine.player) {
      await answerMatches(matches);
    }

    harness.detectChanges();
    page = rendered(harness);
  };

  const button = (label: string): HTMLButtonElement | undefined =>
    [...page.querySelectorAll<HTMLButtonElement>("button")].find((each) =>
      text(each)?.startsWith(label)
    );

  beforeEach(() => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it("is in the header for members", async () => {
    await open({ player: true, seasons: [] });

    expect(texts(page, "header nav.nav a")).toContain("My seasons");
    expect(text(page.querySelector("header nav.nav [aria-current=page]"))).toBe(
      "My seasons"
    );
    expect(text(page.querySelector(".notice"))).toBe(
      "No season is under way right now."
    );
  });

  it("signs the player up, then lets them withdraw", async () => {
    await open({ player: true, seasons: [SEASON] });

    expect(text(page.querySelector("h2"))).toBe("Season 9");
    expect(texts(page, ".seasons p")).toStrictEqual(["Nov 25 – Dec 15"]);

    button("Sign up")?.click();

    const sent = await vi.waitFor(() =>
      http.expectOne(`/api/orgs/${ALSACE.slug}/seasons/9/registration`)
    );

    expect(sent.request.method).toBe("POST");
    sent.flush({
      player: true,
      seasons: [{ ...SEASON, signedUp: true }],
    });
    await answerMatches([]);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "You're signed up for Season 9."
      );
    });
    expect(text(page.querySelector(".pill"))).toBe("Signed up");

    button("Withdraw")?.click();

    const withdrawn = await vi.waitFor(() =>
      http.expectOne(`/api/orgs/${ALSACE.slug}/seasons/9/registration`)
    );

    expect(withdrawn.request.method).toBe("DELETE");
    withdrawn.flush({
      player: true,
      seasons: [SEASON],
    });
    await answerMatches([]);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(button("Sign up")).toBeDefined();
    });
  });

  it("asks for a profile first, and says when sign-ups are closed", async () => {
    await open({
      player: false,
      seasons: [{ ...SEASON, takesSignUps: false }],
    });

    expect(text(page.querySelector("h2"))).toBe(
      "You need a player profile first"
    );
    expect(page.querySelector("a.button")?.getAttribute("href")).toBe(
      "/org/alsace-arena/claim"
    );
    expect(button("Sign up")).toBeUndefined();
    expect(texts(page, ".seasons p").at(-1)).toBe(
      "Sign-ups are closed. An admin can still sign you up."
    );
  });

  it("answers a match: in, then can't make it", async () => {
    await open({ player: true, seasons: [{ ...SEASON, signedUp: true }] }, [
      MATCH,
    ]);

    expect(text(page.querySelector(".matches strong"))).toBe("Match 2");
    expect(texts(page, ".matches p")).toStrictEqual([
      "Dec 5 · 8:30 PM · Discord #matchs",
      "9 of 10 places taken · Reply by Dec 3",
    ]);

    button("I'm in")?.click();

    const sent = await vi.waitFor(() =>
      http.expectOne(`/api/orgs/${ALSACE.slug}/matches/41/attendance`)
    );

    expect(sent.request.body).toStrictEqual({ answer: "YES" });
    sent.flush({
      matches: [{ ...MATCH, confirmed: 10, status: "CONFIRMED" }],
    });
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "You're in for match 2."
      );
    });
    expect(text(page.querySelector(".matches .pill"))).toBe("You're in");
    expect(button("I'm in")).toBeUndefined();

    button("Can't make it")?.click();
    http
      .expectOne(`/api/orgs/${ALSACE.slug}/matches/41/attendance`)
      .flush({ matches: [{ ...MATCH, status: "DECLINED" }] });
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector(".matches .pill"))).toBe("Can't make it");
    });
  });

  it("shows the waiting list position, and a locked roster", async () => {
    await open({ player: true, seasons: [{ ...SEASON, signedUp: true }] }, [
      { ...MATCH, locked: true, position: 2, status: "WAITING" },
    ]);

    expect(text(page.querySelector(".matches .pill"))).toBe(
      "Waiting list · #2"
    );
    expect(button("I'm in")).toBeUndefined();
    expect(texts(page, ".matches p").at(-1)).toBe(
      "The roster is locked: ask an admin to change your answer."
    );
  });
});
