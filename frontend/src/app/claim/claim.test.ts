import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { Router } from "@angular/router";
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

  const open = async (
    state: ClaimState,
    url = "/org/alsace-arena/claim"
  ): Promise<void> => {
    const loading = TestBed.inject(SessionService).load();

    http.expectOne("/api/me").flush(ME);
    await loading;
    harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl(url);

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

  it("joins at once when they came from the leaderboard's Join button", async () => {
    await open({ ...MEMBER, member: false }, "/org/alsace-arena/claim?join=1");

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
      "Kestrel#EUW",
      "Wren#EUW",
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

  it("sends someone already linked to My matches", async () => {
    await open({ ...MEMBER, linked: PLAYERS[0] ?? null });
    await vi.waitFor(() => {
      harness.detectChanges();
      http
        .expectOne(`/api/orgs/${ALSACE.slug}/me/matches`)
        .flush({ matches: [], player: true });
    });

    expect(TestBed.inject(Router).url).toBe("/org/alsace-arena/me/matches");
  });

  it("lets a member who never played create their profile", async () => {
    await open(MEMBER);
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(`${API}/players`).flush(PLAYERS);
    });
    harness.detectChanges();

    const form = page.querySelector<HTMLFormElement>("form.create");
    const riotId = form?.querySelector<HTMLInputElement>("[name=riotId]");

    if (riotId) {
      riotId.value = "  ";
    }

    form?.requestSubmit();
    harness.detectChanges();

    expect(text(page.querySelector("[role=status]"))).toBe(
      "Type your Riot ID, such as Name#TAG."
    );

    if (riotId) {
      riotId.value = "Moth";
    }

    form?.requestSubmit();
    harness.detectChanges();

    expect(text(page.querySelector("[role=status]"))).toBe(
      "A Riot ID is a name of 3 to 16 characters, # and a tag of 3 to 5 letters or numbers, such as Kestrel#EUW."
    );

    if (riotId) {
      riotId.value = " Moth#EUW ";
    }

    form?.requestSubmit();

    const created = await vi.waitFor(() =>
      http.expectOne(`/api/orgs/${ALSACE.slug}/players/me`)
    );

    expect(created.request.body).toStrictEqual({
      discordUsername: null,
      riotId: "Moth#EUW",
    });
    created.flush({
      ...MEMBER,
      linked: { ...PLAYERS[1], games: 0, id: "moth", riotId: "Moth#EUW" },
    });
    await vi.waitFor(() => http.expectOne("/api/me").flush(ME));
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("h2"))).toBe("Your profile is Moth");
    });
    expect(text(page.querySelector("[role=status]"))).toBe(
      "Your profile is created. Now say which matches you can make."
    );
  });

  it("creates the profile with the account's Riot ID when it has one", async () => {
    const loading = TestBed.inject(SessionService).load();

    http.expectOne("/api/me").flush({ ...ME, riotId: "Heron#EUW" });
    await loading;
    harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl("/org/alsace-arena/claim");

    await answer(harness, `/api/orgs/${ALSACE.slug}`, ALSACE);
    await navigation;
    await answer(harness, `/api/orgs/${ALSACE.slug}/seasons`, SEASONS);
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(API).flush(MEMBER);
    });
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(`${API}/players`).flush(PLAYERS);
    });
    harness.detectChanges();
    page = rendered(harness);

    expect(page.querySelector("form.create")).toBeNull();
    expect(text(page.querySelector("#create-title + p + p"))).toBe(
      "It takes the Riot ID on your account: Heron#EUW."
    );
    button("Create my profile")?.click();

    const created = await vi.waitFor(() =>
      http.expectOne(`/api/orgs/${ALSACE.slug}/players/me`)
    );

    expect(created.request.body).toStrictEqual({
      discordUsername: null,
      riotId: null,
    });
    created.flush({ ...MEMBER, linked: PLAYERS[1] ?? null });
    await vi.waitFor(() => http.expectOne("/api/me").flush(ME));
  });
});
