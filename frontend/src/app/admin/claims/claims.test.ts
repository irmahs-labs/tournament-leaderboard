import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import type { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { ClaimRow } from "../../claim/api";
import {
  ALSACE,
  ME,
  openAs,
  rendered,
  testProviders,
  text,
  texts,
} from "../../testing";

const API = `/api/orgs/${ALSACE.slug}/admin/claims`;

const row = (id: string, name: string, own = false): ClaimRow => ({
  claim: {
    decidedAt: null,
    id,
    note: "I'm Kestrel, I play Jett.",
    playerId: "kestrel",
    reason: null,
    riotId: "Kestrel#EUW",
    selfApproved: false,
    sentAt: "2026-11-20T08:00:00Z",
    status: "PENDING",
  },
  competing: 2,
  email: `${name.toLowerCase()}@example.com`,
  id,
  memberSince: "2026-11-01T10:00:00Z",
  name,
  own,
  player: {
    aliases: [],
    discordUsername: null,
    games: 15,
    id: "kestrel",
    rank: "Diamond 3",
    riotId: "Kestrel#EUW",
  },
});

const WAITING = [row("c1", "Léa"), row("c2", "Camille", true)];

describe("the Claims screen", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  const button = (label: string): HTMLButtonElement | undefined =>
    [...page.querySelectorAll<HTMLButtonElement>("button")].find((each) =>
      text(each)?.startsWith(label)
    );

  const answerQueue = async (rows: ClaimRow[]): Promise<void> => {
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne((each) => each.url === API).flush(rows);
    });
    harness.detectChanges();
  };

  beforeEach(async () => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    harness = await openAs("/org/alsace-arena/admin/claims", ME);
    await answerQueue(WAITING);
    page = rendered(harness);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it("lists the waiting claims, with competing ones and the admin's own", () => {
    expect(texts(page, ".who-text strong")).toStrictEqual([
      "Léa",
      "Camille (you)",
    ]);
    expect(texts(page, ".pill.warn")).toStrictEqual([
      "2 claims for this player",
      "2 claims for this player",
    ]);
    expect(texts(page, ".actions .pill")).toStrictEqual(["Your own claim"]);
  });

  it("accepts a claim", async () => {
    button("Accept Léa")?.click();

    const accept = await vi.waitFor(() => http.expectOne(`${API}/c1/accept`));

    expect(accept.request.method).toBe("POST");
    accept.flush(row("c1", "Léa"));
    await answerQueue([row("c2", "Camille", true)]);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "Léa is now linked to Kestrel."
      );
    });
  });

  it("declines a claim with a reason", async () => {
    button("Decline Léa")?.click();
    harness.detectChanges();
    const reason = page.querySelector<HTMLInputElement>(".decline input");

    if (reason) {
      reason.value = "Kestrel is someone else";
    }

    button("Decline the claim")?.click();

    const decline = await vi.waitFor(() => http.expectOne(`${API}/c1/decline`));

    expect(decline.request.body).toStrictEqual({
      reason: "Kestrel is someone else",
    });
    decline.flush(row("c1", "Léa"));
    await answerQueue([row("c2", "Camille", true)]);
  });

  it("shows the other tabs", async () => {
    button("Accepted")?.click();

    const request = await vi.waitFor(() =>
      http.expectOne((each) => each.url === API)
    );

    expect(request.request.params.get("status")).toBe("ACCEPTED");
    request.flush([]);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector(".notice"))).toBe(
        "No claim accepted yet."
      );
    });
  });
});
