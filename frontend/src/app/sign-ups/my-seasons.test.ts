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
import type { MySeason, MySeasons } from "./api";

const API = `/api/orgs/${ALSACE.slug}/me/seasons`;

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

  const open = async (mine: MySeasons): Promise<void> => {
    harness = await openAs("/org/alsace-arena/me/seasons", ME);
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(API).flush(mine);
    });
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
});
