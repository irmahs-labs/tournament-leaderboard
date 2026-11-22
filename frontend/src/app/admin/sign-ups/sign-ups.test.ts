import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import type { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { SignUp, SignUps } from "../../sign-ups/api";
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
import type { AdminPlayer } from "../api";
import { notSignedUp } from "./sign-ups";

const ADMIN = `/api/orgs/${ALSACE.slug}/admin`;
const API = `${ADMIN}/seasons/3`;

const player = (id: string, riotId: string, linked: boolean): AdminPlayer => ({
  aliases: [],
  discordUsername: null,
  expectedEmail: null,
  id,
  linked,
  played: false,
  rank: null,
  riotId,
});

const PLAYERS = [
  player("kestrel", "Kestrel#EUW", true),
  player("wren", "Wren#EUW", false),
  player("moth", "Moth#EUW", true),
];

const signUp = (
  playerId: string,
  riotId: string,
  extra: Partial<SignUp> = {}
): SignUp => ({
  account: true,
  addedBy: null,
  byAdmin: false,
  playerId,
  registeredAt: "2026-11-20T18:00:00Z",
  riotId,
  ...extra,
});

const SIGN_UPS: SignUps = {
  finished: false,
  name: "Season 3",
  number: 3,
  open: true,
  registrations: [
    signUp("kestrel", "Kestrel#EUW"),
    signUp("wren", "Wren#EUW", {
      account: false,
      addedBy: "Camille Martin",
      byAdmin: true,
    }),
  ],
};

describe("the players left to sign up", () => {
  it("leave out those signed up, and match the search", () => {
    expect(
      notSignedUp(PLAYERS, SIGN_UPS.registrations, "").map((each) => each.id)
    ).toStrictEqual(["moth"]);
    expect(
      notSignedUp(PLAYERS, SIGN_UPS.registrations, "KES").map((each) => each.id)
    ).toStrictEqual([]);
    expect(notSignedUp(PLAYERS, [], "wr").map((each) => each.id)).toStrictEqual(
      ["wren"]
    );
  });
});

describe("the Season sign-ups page", () => {
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
      "/org/alsace-arena/admin/seasons/3/sign-ups",
      ME,
      SEASONS
    );
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(`${API}/registrations`).flush(SIGN_UPS);
      http.expectOne(`${ADMIN}/players`).flush(PLAYERS);
    });
    harness.detectChanges();
    page = rendered(harness);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it("lists who signed up, in order", () => {
    expect(text(page.querySelector("h1"))).toBe("Season 3 sign-ups");
    expect(text(page.querySelector("#list-title"))).toBe("Signed up · 2");
    expect(
      [...page.querySelectorAll("tbody tr")].map((row) => texts(row, "td"))
    ).toStrictEqual([
      [
        "1",
        "Kestrel#EUW",
        expect.any(String),
        "Themselves",
        "Withdraw Kestrel",
      ],
      [
        "2",
        "Wren#EUW No account",
        expect.any(String),
        "By Camille Martin",
        "Withdraw Wren",
      ],
    ]);
  });

  it("closes sign-ups", async () => {
    const form = page.querySelector<HTMLFormElement>("form.settings");
    const open = form?.querySelector<HTMLInputElement>("[name=open]");

    if (open) {
      open.checked = false;
    }

    form?.requestSubmit();
    const saved = http.expectOne(`${API}/signup`);

    expect(saved.request.method).toBe("PUT");
    expect(saved.request.body).toStrictEqual({ open: false });
    saved.flush({ ...SIGN_UPS, open: false });
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector(".block-head .pill"))).toBe("Closed");
    });
  });

  it("signs someone up from a search, and withdraws someone", async () => {
    const search = page.querySelector<HTMLInputElement>("input[type=search]");

    if (search) {
      search.value = "mo";
      search.dispatchEvent(new Event("input"));
    }

    harness.detectChanges();
    expect(texts(page, "#add-title ~ .choices strong")).toStrictEqual([
      "Moth#EUW",
    ]);

    button("Sign up Moth")?.click();
    const added = http.expectOne(`${API}/registrations/moth`);

    expect(added.request.method).toBe("POST");
    added.flush(SIGN_UPS);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "Moth is signed up."
      );
    });

    button("Withdraw Wren")?.click();
    const removed = http.expectOne(`${API}/registrations/wren`);

    expect(removed.request.method).toBe("DELETE");
    removed.flush(SIGN_UPS);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "Wren was withdrawn. They stop hearing about this season's matches."
      );
    });
  });
});
