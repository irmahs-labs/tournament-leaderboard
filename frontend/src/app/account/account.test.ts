import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { Router } from "@angular/router";
import { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { Me } from "../shared/session";
import { SessionService } from "../shared/session";
import { ME, rendered, testProviders, text, texts } from "../testing";

const MEMBER: Me = {
  ...ME,
  memberships: [
    {
      name: "Lyon Game Bar",
      owner: false,
      playerId: null,
      role: "PLAYER",
      slug: "lyon-game-bar",
    },
  ],
};

describe("My account", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  const open = async (me: Me | null): Promise<void> => {
    const loading = TestBed.inject(SessionService).load();
    const request = http.expectOne("/api/me");

    if (me) {
      request.flush(me);
    } else {
      request.flush(null, { status: 401, statusText: "Unauthorized" });
    }

    await loading;
    harness = await RouterTestingHarness.create("/account");
    page = rendered(harness);
  };

  const button = (label: string): HTMLButtonElement | undefined =>
    [...page.querySelectorAll<HTMLButtonElement>("button")].find((each) =>
      text(each)?.startsWith(label)
    );

  const typeConfirmation = (word: string): void => {
    const input = page.querySelector<HTMLInputElement>("input[name=confirm]");

    if (input) {
      input.value = word;
      input.dispatchEvent(new Event("input"));
    }

    harness.detectChanges();
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

  it("sends anyone signed out to sign in first", async () => {
    await open(null);

    expect(TestBed.inject(Router).url).toBe("/sign-in?from=%2Faccount");
  });

  it("lists the organisations, and an owner can't leave or delete", async () => {
    await open(ME);

    expect(texts(page, ".orgs strong")).toStrictEqual(["Alsace Arena"]);
    expect(text(page.querySelector(".orgs .pill"))).toBe("Owner");
    expect(button("Leave")).toBeUndefined();
    expect(page.querySelector("input[name=confirm]")).toBeNull();
  });

  it("links to the player's profile, or to claiming it", async () => {
    await open({
      ...ME,
      memberships: [
        ...ME.memberships.map((membership) => ({
          ...membership,
          playerId: "kestrel",
        })),
        ...MEMBER.memberships,
      ],
    });

    expect(
      [...page.querySelectorAll(".orgs a.button-ghost")].map((link) =>
        link.getAttribute("href")
      )
    ).toStrictEqual([
      "/org/alsace-arena/players/kestrel",
      "/org/lyon-game-bar/claim",
    ]);
  });

  it("leaves an organisation after asking", async () => {
    await open(MEMBER);
    vi.spyOn(globalThis, "confirm").mockReturnValue(true);

    button("Leave")?.click();

    const leave = await vi.waitFor(() =>
      http.expectOne("/api/orgs/lyon-game-bar/membership")
    );

    expect(leave.request.method).toBe("DELETE");
    leave.flush(null);
    await vi.waitFor(() =>
      http.expectOne("/api/me").flush({ ...MEMBER, memberships: [] })
    );
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "You left Lyon Game Bar."
      );
    });
  });

  it("deletes the account once DELETE is typed", async () => {
    await open(MEMBER);

    const navigate = vi
      .spyOn(TestBed.inject(Router), "navigateByUrl")
      .mockResolvedValue(true);

    typeConfirmation("delete");

    expect(button("Delete account")?.disabled).toBeTruthy();

    typeConfirmation("DELETE");
    button("Delete account")?.click();
    const request = await vi.waitFor(() => http.expectOne("/api/me"));

    expect(request.request.method).toBe("DELETE");
    request.flush(null);
    await vi.waitFor(() =>
      http
        .expectOne("/api/me")
        .flush(null, { status: 401, statusText: "Unauthorized" })
    );
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith("/"));
  });
});
