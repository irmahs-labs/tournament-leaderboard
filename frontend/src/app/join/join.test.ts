import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { Me } from "../shared/session";
import { ME, rendered, testProviders, text } from "../testing";
import type { JoinView } from "./join";
import { stepOf, tidyCode } from "./join";

const INVITE: JoinView = {
  email: "Quentin@Example.com",
  expiresAt: "2026-11-20T10:00:00Z",
  invitedBy: "Camille Martin",
  organization: {
    name: "Alsace Arena",
    slug: "alsace-arena",
    tournamentName: "Spike Tournament",
  },
  role: "ADMIN",
  state: "VALID",
};

const QUENTIN: Me = { ...ME, email: "quentin@example.com", memberships: [] };

describe("the join page", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  const open = async (me: Me | null, invite: JoinView): Promise<void> => {
    harness = await RouterTestingHarness.create();
    void harness.navigateByUrl("/join/K7QM-4FXP");
    await vi.waitFor(() => {
      harness.detectChanges();

      for (const request of http.match("/api/me")) {
        if (me) {
          request.flush(me);
        } else {
          request.flush(null, { status: 401, statusText: "Unauthorized" });
        }
      }

      http.expectOne("/api/invites/K7QM-4FXP").flush(invite);
    });
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(rendered(harness).querySelector("h1"))).toBeTruthy();
    });
    page = rendered(harness);
  };

  beforeEach(() => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    for (const request of http.match("/api/me")) {
      request.flush(null);
    }

    http.verify();
    localStorage.clear();
  });

  it("asks someone signed out to sign in with Google", async () => {
    await open(null, INVITE);

    expect(text(page.querySelector("h1"))).toBe(
      "Join Alsace Arena as an admin"
    );
    expect(text(page.querySelector(".google"))).toBe("Continue with Google");
  });

  it("lets the invited account accept, and says it's done", async () => {
    await open(QUENTIN, INVITE);
    page.querySelector<HTMLButtonElement>(".buttons .button")?.click();
    http.expectOne("/api/invites/K7QM-4FXP/accept").flush({
      name: "Alsace Arena",
      owner: false,
      role: "ADMIN",
      slug: "alsace-arena",
    });
    await vi.waitFor(() => {
      harness.detectChanges();

      for (const request of http.match("/api/me")) {
        request.flush(QUENTIN);
      }

      expect(text(page.querySelector("h1"))).toBe(
        "You’re an admin of Alsace Arena"
      );
    });
  });

  it("explains an expired code", async () => {
    await open(QUENTIN, { ...INVITE, state: "EXPIRED" });

    expect(text(page.querySelector("h1"))).toBe("This code has expired");
  });
});

describe("a code", () => {
  it("leads to the right step", () => {
    expect([
      stepOf(INVITE, null, false),
      stepOf(INVITE, "quentin@example.com", false),
      stepOf(INVITE, "someone@example.com", false),
      stepOf({ ...INVITE, state: "USED" }, "quentin@example.com", false),
      stepOf(INVITE, "quentin@example.com", true),
    ]).toStrictEqual(["signIn", "accept", "wrongAccount", "used", "accepted"]);
  });

  it("is tidied as typed", () => {
    expect(
      ["k7qm 4fxp", "K7QM-4FXP", "k7qm4fxp", "abc"].map(tidyCode)
    ).toStrictEqual(["K7QM-4FXP", "K7QM-4FXP", "K7QM-4FXP", "ABC"]);
  });
});
