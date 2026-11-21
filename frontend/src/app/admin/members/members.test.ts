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
} from "../../testing";
import type { NewInvite, Team } from "../api";
import { joinLink, memberName } from "./members";

const API = `/api/orgs/${ALSACE.slug}/admin`;

const TEAM: Team = {
  invites: [
    {
      createdAt: "2026-11-19T10:00:00Z",
      email: "quentin@example.com",
      expiresAt: "2026-11-20T10:00:00Z",
      id: "invite-1",
      invitedBy: "Camille Martin",
    },
  ],
  members: [
    {
      email: "camille@example.com",
      joinedAt: "2026-11-01T10:00:00Z",
      name: "Camille Martin",
      owner: true,
      role: "ADMIN",
      userId: "camille",
    },
    {
      email: "yanis@example.com",
      joinedAt: "2026-11-02T10:00:00Z",
      name: null,
      owner: false,
      role: "ADMIN",
      userId: "yanis",
    },
    {
      email: "bram@example.com",
      joinedAt: "2026-11-03T10:00:00Z",
      name: "Bram",
      owner: false,
      role: "PLAYER",
      userId: "bram",
    },
  ],
};

describe("the Members screen", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  const button = (label: string): HTMLButtonElement | undefined =>
    [...page.querySelectorAll<HTMLButtonElement>("button")].find((each) =>
      text(each)?.startsWith(label)
    );

  const answerTeam = async (): Promise<void> => {
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(`${API}/members`).flush(TEAM);
    });
    harness.detectChanges();
  };

  beforeEach(async () => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    harness = await openAs("/org/alsace-arena/admin/members", ME);
    await answerTeam();
    page = rendered(harness);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it("lists the members with their role, and the waiting invites", () => {
    expect(
      texts(page, "#members-title ~ .scroll tbody td:first-child strong")
    ).toStrictEqual(["Camille Martin", "yanis", "Bram"]);
    expect(texts(page, "#members-title ~ .scroll .pill")).toStrictEqual([
      "Owner",
      "Admin",
      "Player",
    ]);
    expect(
      texts(page, "#waiting-title ~ .scroll tbody td:first-child")
    ).toStrictEqual(["quentin@example.com"]);
    expect(button("Make player yanis")).toBeDefined();
    expect(button("Remove Camille Martin")).toBeUndefined();
  });

  it("creates an invite and shows the message to paste", async () => {
    const email = page.querySelector<HTMLInputElement>("input[name=email]");

    if (email) {
      email.value = "new@example.com";
    }

    button("Create invite")?.click();
    const request = http.expectOne(`${API}/invites`);

    expect(request.request.body).toStrictEqual({
      email: "new@example.com",
      expiresInMinutes: 1440,
    });

    const made: NewInvite = {
      code: "K7QM-4FXP",
      email: "new@example.com",
      expiresAt: "2026-11-20T10:00:00Z",
      id: "invite-2",
    };

    request.flush(made);
    await answerTeam();

    expect(
      page.querySelector<HTMLTextAreaElement>("textarea")?.value
    ).toContain("/join/K7QM-4FXP (code K7QM-4FXP)");
    expect(
      page.querySelector<HTMLSelectElement>("select[name=expiry]")?.value
    ).toBe("1440");
  });

  it("makes a player an admin and removes a member after asking", async () => {
    button("Make admin Bram")?.click();
    const promote = http.expectOne(`${API}/members/bram/role`);

    expect(promote.request.body).toStrictEqual({ role: "ADMIN" });

    promote.flush(null);
    await answerTeam();
    vi.spyOn(globalThis, "confirm").mockReturnValue(true);
    button("Remove Bram")?.click();

    expect(http.expectOne(`${API}/members/bram`).request.method).toBe("DELETE");
  });

  it("lets the owner hand ownership to another admin", () => {
    vi.spyOn(globalThis, "confirm").mockReturnValue(true);
    button("Transfer ownership")?.click();

    expect(
      http.expectOne(`${API}/members/yanis/ownership`).request.method
    ).toBe("POST");
  });
});

describe("a member", () => {
  it("is named by their Google name, else the start of their email", () => {
    expect(
      [TEAM.members[0], TEAM.members[1]].map((member) =>
        member ? memberName(member) : ""
      )
    ).toStrictEqual(["Camille Martin", "yanis"]);
    expect(joinLink("https://example.com", "K7QM-4FXP")).toBe(
      "https://example.com/join/K7QM-4FXP"
    );
  });
});
