import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { Router } from "@angular/router";
import { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { Me } from "../shared/session";
import { SessionService } from "../shared/session";
import { ALSACE, ME, rendered, testProviders, text } from "../testing";
import { foundingProblem, slugFrom } from "./home";

describe("the founding checks", () => {
  it("makes an address from a name", () => {
    expect(slugFrom("Lyon Game Bar")).toBe("lyon-game-bar");
    expect(slugFrom("  Été à Strasbourg!! ")).toBe("ete-a-strasbourg");
    expect(slugFrom("x".repeat(50))).toHaveLength(40);
  });

  it("says what's wrong with the name or the address", () => {
    expect(foundingProblem("Metz Esport", "metz-esport")).toBeNull();
    expect(foundingProblem(" ", "metz-esport")).toBe("name");
    expect(foundingProblem("Metz", "ab")).toBe("slug");
    expect(foundingProblem("Metz", "metz--esport")).toBe("slug");
    expect(foundingProblem("Metz", "admin")).toBe("reserved");
  });
});

describe("the home page", () => {
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
    harness = await RouterTestingHarness.create("/");
    page = rendered(harness);
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne("/api/featured").flush(ALSACE);
    });
    harness.detectChanges();
  };

  const type = (name: string, value: string): void => {
    const input = page.querySelector<HTMLInputElement>(`input[name=${name}]`);

    if (input) {
      input.value = value;
      input.dispatchEvent(new Event("input"));
    }

    harness.detectChanges();
  };

  const submit = (): void => {
    page.querySelector<HTMLButtonElement>("form button[type=submit]")?.click();
    harness.detectChanges();
  };

  beforeEach(() => {
    localStorage.setItem("lang", "en");
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it("features the most recently active organisation", async () => {
    await open(null);

    expect(text(page.querySelector("h1"))).toBe(
      "Run your seasons. Your colours, your rules."
    );
    expect(text(page.querySelector(".featured .org-name"))).toBe(
      "Alsace Arena · Spike Tournament"
    );
    expect(page.querySelector(".featured a")?.getAttribute("href")).toBe(
      "/o/alsace-arena"
    );
  });

  it("fills the address from the name until it's typed by hand", async () => {
    await open(null);
    type("name", "Metz Esport");

    expect(
      page.querySelector<HTMLInputElement>("input[name=slug]")?.value
    ).toBe("metz-esport");
    expect(text(page.querySelector(".address code"))).toContain(
      "/o/metz-esport"
    );

    type("slug", "metz");
    type("name", "Metz Esport Club");

    expect(
      page.querySelector<HTMLInputElement>("input[name=slug]")?.value
    ).toBe("metz");
  });

  it("signed out, keeps the form and signs in with Google first", async () => {
    await open(null);

    const signIn = vi
      .spyOn(TestBed.inject(SessionService), "signIn")
      .mockImplementation(() => {});

    type("name", "Metz Esport");
    submit();
    await vi.waitFor(() => expect(signIn).toHaveBeenCalledWith("/#create"));

    expect(sessionStorage.getItem("foundingDraft")).toBe(
      "Metz Esport\nmetz-esport"
    );
  });

  it("signed in, creates the organisation and opens its Seasons page", async () => {
    await open({ ...ME, memberships: [] });

    const navigate = vi
      .spyOn(TestBed.inject(Router), "navigate")
      .mockResolvedValue(true);

    type("name", "Metz Esport");
    submit();
    const request = await vi.waitFor(() => http.expectOne("/api/orgs"));

    expect(request.request.body).toStrictEqual({
      name: "Metz Esport",
      slug: "metz-esport",
    });
    request.flush({ name: "Metz Esport", slug: "metz-esport" });
    await vi.waitFor(() => http.expectOne("/api/me").flush(ME));
    await vi.waitFor(() =>
      expect(navigate).toHaveBeenCalledWith([
        "/o",
        "metz-esport",
        "admin",
        "seasons",
      ])
    );
  });

  it("says when the address is taken", async () => {
    await open(ME);
    type("name", "Alsace Arena");
    submit();
    await vi.waitFor(() =>
      http
        .expectOne("/api/orgs")
        .flush(null, { status: 409, statusText: "Conflict" })
    );
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector(".problem"))).toBe(
        "That address is already taken. Choose another one."
      );
    });
  });
});
