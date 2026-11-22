import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { Router } from "@angular/router";
import { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { Me } from "../shared/session";
import { SessionService } from "../shared/session";
import { ME, rendered, testProviders, text, texts } from "../testing";
import type { PlatformOrganization } from "./platform";
import { matching } from "./platform";

const API = "/api/platform/organizations";
const PLATFORM_ADMIN: Me = { ...ME, platformAdmin: true };

const ORGANIZATIONS: PlatformOrganization[] = [
  {
    createdAt: "2026-11-01T10:00:00Z",
    deletable: false,
    featured: true,
    lastActivityAt: "2026-11-18T20:00:00Z",
    members: 42,
    name: "Alsace Arena",
    ownerEmail: null,
    slug: "alsace-arena",
    status: "ACTIVE",
  },
  {
    createdAt: "2026-11-05T10:00:00Z",
    deletable: true,
    featured: false,
    lastActivityAt: null,
    members: 2,
    name: "Lyon Game Bar",
    ownerEmail: "lyon@example.com",
    slug: "lyon-game-bar",
    status: "SUSPENDED",
  },
];

describe("the platform search", () => {
  it("finds organisations by name, address or owner's email", () => {
    expect(matching(ORGANIZATIONS, "")).toHaveLength(2);
    expect(matching(ORGANIZATIONS, " ALSACE ")).toStrictEqual([
      ORGANIZATIONS[0],
    ]);
    expect(matching(ORGANIZATIONS, "lyon@")).toStrictEqual([ORGANIZATIONS[1]]);
    expect(matching(ORGANIZATIONS, "game-bar")).toStrictEqual([
      ORGANIZATIONS[1],
    ]);
  });
});

describe("the platform page", () => {
  let http: HttpTestingController;

  const open = async (me: Me | null): Promise<RouterTestingHarness> => {
    const loading = TestBed.inject(SessionService).load();
    const request = http.expectOne("/api/me");

    if (me) {
      request.flush(me);
    } else {
      request.flush(null, { status: 401, statusText: "Unauthorized" });
    }

    await loading;

    return RouterTestingHarness.create("/platform");
  };

  const openAsPlatformAdmin = async (): Promise<{
    harness: RouterTestingHarness;
    page: HTMLElement;
  }> => {
    const harness = await open(PLATFORM_ADMIN);

    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(API).flush(ORGANIZATIONS);
    });
    harness.detectChanges();

    return { harness, page: rendered(harness) };
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

  it("lists every organisation, with the one on the home page", async () => {
    const { page } = await openAsPlatformAdmin();

    expect(texts(page, ".tile .number")).toStrictEqual([
      "2",
      "1",
      "1",
      "Alsace Arena",
    ]);
    expect(texts(page, "tbody .slug")).toStrictEqual([
      "/org/alsace-arena",
      "/org/lyon-game-bar",
    ]);
    expect(text(page.querySelector("tbody .home"))).toBe("On the home page");
  });

  it("filters the table with the search", async () => {
    const { harness, page } = await openAsPlatformAdmin();
    const search = page.querySelector<HTMLInputElement>("input[type=search]");

    if (search) {
      search.value = "lyon";
      search.dispatchEvent(new Event("input"));
    }

    harness.detectChanges();

    expect(texts(page, "tbody .slug")).toStrictEqual(["/org/lyon-game-bar"]);
  });

  it("suspends after asking, then reactivates", async () => {
    const { harness, page } = await openAsPlatformAdmin();
    const confirm = vi.spyOn(globalThis, "confirm").mockReturnValue(true);
    const buttons = page.querySelectorAll<HTMLButtonElement>("tbody button");

    buttons[0]?.click();

    const suspend = await vi.waitFor(() =>
      http.expectOne(`${API}/alsace-arena/status`)
    );

    expect(confirm).toHaveBeenCalledOnce();
    expect(suspend.request.body).toStrictEqual({ status: "SUSPENDED" });
    suspend.flush(null);
    await vi.waitFor(() => http.expectOne(API).flush(ORGANIZATIONS));

    buttons[1]?.click();

    const reactivate = await vi.waitFor(() =>
      http.expectOne(`${API}/lyon-game-bar/status`)
    );

    expect(reactivate.request.body).toStrictEqual({ status: "ACTIVE" });
    expect(confirm).toHaveBeenCalledOnce();
    reactivate.flush(null);
    await vi.waitFor(() => http.expectOne(API).flush(ORGANIZATIONS));
    harness.detectChanges();
  });

  it("deletes an organisation nobody uses yet once its name is typed", async () => {
    const { harness, page } = await openAsPlatformAdmin();
    const prompt = vi.spyOn(globalThis, "prompt").mockReturnValue("Lyon");

    const remove = (): void => {
      [...page.querySelectorAll<HTMLButtonElement>("tbody button")]
        .find((button) => text(button)?.startsWith("Delete"))
        ?.click();
    };

    expect(
      texts(page, "tbody button").filter((label) => label?.startsWith("Delete"))
    ).toStrictEqual(["Delete Lyon Game Bar"]);

    remove();
    harness.detectChanges();

    expect(text(page.querySelector("[role=status]"))).toBe(
      "That isn't “Lyon Game Bar”: nothing was deleted."
    );

    prompt.mockReturnValue(" Lyon Game Bar ");
    remove();

    const deleted = await vi.waitFor(() =>
      http.expectOne(`${API}/lyon-game-bar`)
    );

    expect(deleted.request.method).toBe("DELETE");
    expect(deleted.request.body).toStrictEqual({ name: "Lyon Game Bar" });
    deleted.flush(null);
    await vi.waitFor(() =>
      http.expectOne(API).flush(ORGANIZATIONS.slice(0, 1))
    );
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "Lyon Game Bar is deleted."
      );
    });
  });

  it("is Not found for anyone but a platform admin", async () => {
    await open(ME);

    expect(TestBed.inject(Router).url).toBe("/not-found");
  });
});
