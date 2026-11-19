import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SessionService } from "../shared/session";
import {
  ALSACE,
  answer,
  ME,
  rendered,
  SEASONS,
  testProviders,
  text,
  texts,
} from "../testing";
import type { ClaimState, PlayerSummary } from "./api";
import { matchingPlayers } from "./api";

const API = `/api/orgs/${ALSACE.slug}/claim`;

const PLAYERS: PlayerSummary[] = [
  {
    aliases: ["kes"],
    discordUsername: "kestrel.gg",
    games: 15,
    id: "kestrel",
    rank: "Diamond 3",
    riotId: "Kestrel#EUW",
  },
  {
    aliases: [],
    discordUsername: null,
    games: 2,
    id: "wren",
    rank: null,
    riotId: "Wren#EUW",
  },
];

const MEMBER: ClaimState = {
  claim: null,
  joinedAt: "2026-11-03T10:00:00Z",
  linked: null,
  member: true,
};

describe("the players to claim", () => {
  it("are found by Riot ID, Discord or alias", () => {
    expect(matchingPlayers(PLAYERS, "")).toHaveLength(2);
    expect(matchingPlayers(PLAYERS, "KES").map((p) => p.id)).toStrictEqual([
      "kestrel",
    ]);
    expect(matchingPlayers(PLAYERS, "wren").map((p) => p.id)).toStrictEqual([
      "wren",
    ]);
  });
});

describe("the Claim page", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  const button = (label: string): HTMLButtonElement | undefined =>
    [...page.querySelectorAll<HTMLButtonElement>("button")].find((each) =>
      text(each)?.startsWith(label)
    );

  const open = async (state: ClaimState): Promise<void> => {
    const loading = TestBed.inject(SessionService).load();

    http.expectOne("/api/me").flush(ME);
    await loading;
    harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl("/org/alsace-arena/claim");

    await answer(harness, `/api/orgs/${ALSACE.slug}`, ALSACE);
    await navigation;
    await answer(harness, `/api/orgs/${ALSACE.slug}/seasons`, SEASONS);
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(API).flush(state);
    });
    harness.detectChanges();
    page = rendered(harness);
  };

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

  it("offers to join as a player first", async () => {
    await open({ ...MEMBER, member: false });
    button("Join as a player")?.click();

    const join = await vi.waitFor(() =>
      http.expectOne(`/api/orgs/${ALSACE.slug}/membership`)
    );

    expect(join.request.method).toBe("POST");
    join.flush(MEMBER);
    await vi.waitFor(() => http.expectOne(`${API}/players`).flush(PLAYERS));
    await vi.waitFor(() => http.expectOne("/api/me").flush(ME));
  });

  it("lets a member pick their profile and send a claim with a note", async () => {
    await open(MEMBER);
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(`${API}/players`).flush(PLAYERS);
    });
    harness.detectChanges();

    expect(texts(page, ".choices strong")).toStrictEqual([
      "Kestrel #EUW",
      "Wren #EUW",
    ]);

    button("This is me Kestrel")?.click();
    harness.detectChanges();
    const note = page.querySelector<HTMLTextAreaElement>("textarea");

    if (note) {
      note.value = "I play Jett";
    }

    button("Send the claim")?.click();

    const sent = await vi.waitFor(() =>
      http.expectOne((each) => each.url === API && each.method === "POST")
    );

    expect(sent.request.body).toStrictEqual({
      note: "I play Jett",
      playerId: "kestrel",
    });
    sent.flush({
      ...MEMBER,
      claim: {
        decidedAt: null,
        id: "claim-1",
        note: "I play Jett",
        playerId: "kestrel",
        reason: null,
        riotId: "Kestrel#EUW",
        selfApproved: false,
        sentAt: "2026-11-20T10:00:00Z",
        status: "PENDING",
      },
    });
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("h2"))).toBe(
        "Your claim for Kestrel was sent"
      );
    });
  });

  it("shows the linked profile once a claim is accepted", async () => {
    await open({ ...MEMBER, linked: PLAYERS[0] ?? null });

    expect(text(page.querySelector("h2"))).toBe("Your profile is Kestrel");
    expect(page.querySelector("a.start")?.getAttribute("href")).toBe(
      "/org/alsace-arena/players/kestrel"
    );
  });
});
