import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { Router } from "@angular/router";
import { RouterTestingHarness } from "@angular/router/testing";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SessionService } from "../core/session";
import { ALSACE, ME, testProviders } from "../testing";

const text = (element: Element | null | undefined) =>
  element?.textContent?.replaceAll(/\s+/gu, " ").trim();

describe("the sign-in page", () => {
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  /** Opens an address with the session already known. */
  const open = async (url: string, me: typeof ME | null) => {
    const loading = TestBed.inject(SessionService).load();

    if (me) {
      http.expectOne("/api/me").flush(me);
    } else {
      http
        .expectOne("/api/me")
        .flush(null, { status: 401, statusText: "Unauthorized" });
    }

    await loading;

    const harness = await RouterTestingHarness.create(url);
    const page = harness.routeNativeElement;

    if (!page) {
      throw new Error("The route rendered nothing");
    }

    return { harness, page };
  };

  it("offers Google when signed out", async () => {
    const { page } = await open("/sign-in", null);

    expect(text(page.querySelector("h1"))).toBe("Sign in");
    expect(text(page.querySelector("button.google"))).toBe(
      "Continue with Google"
    );
    expect(page.querySelector('[role="alert"]')).toBeNull();
  });

  it("says when signing in failed", async () => {
    const { page } = await open("/sign-in?error", null);

    expect(text(page.querySelector('[role="alert"]'))).toContain(
      "Signing in didn't work"
    );
  });

  it("remembers where to come back to before leaving for Google", async () => {
    const { page } = await open(
      "/sign-in?from=%2Fo%2Falsace-arena%2Fmatches",
      null
    );

    const assign = vi.fn<(url: string | URL) => void>();

    vi.spyOn(globalThis, "location", "get").mockReturnValue({
      ...location,
      assign,
    });
    page.querySelector<HTMLButtonElement>("button.google")?.click();

    expect(sessionStorage.getItem("signInReturnTo")).toBe(
      "/o/alsace-arena/matches"
    );
    expect(assign).toHaveBeenCalledWith("/oauth2/authorization/google");
    vi.restoreAllMocks();
  });

  it("lists the organisations once signed in", async () => {
    const { page } = await open("/sign-in", ME);

    expect(text(page.querySelector(".who strong"))).toBe("Camille Martin");
    expect(text(page.querySelector(".orgs .org-name"))).toBe("Alsace Arena");
    expect(text(page.querySelector(".orgs .pill"))).toBe("Owner");
    expect(page.querySelector(".orgs a")?.getAttribute("href")).toBe(
      "/o/alsace-arena"
    );
  });

  it("goes back to the page sign-in started from", async () => {
    sessionStorage.setItem("signInReturnTo", "/o/alsace-arena/matches");

    const { harness } = await open("/sign-in", ME);

    await vi.waitFor(() => {
      http.expectOne(`/api/orgs/${ALSACE.slug}`).flush(ALSACE);
    });
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe("/o/alsace-arena/matches");
    expect(sessionStorage.getItem("signInReturnTo")).toBeNull();
  });

  it("signs out from Not you?", async () => {
    const { harness, page } = await open("/sign-in", ME);

    page.querySelector<HTMLButtonElement>(".who button")?.click();
    http
      .expectOne("/api/logout")
      .flush(null, { status: 204, statusText: "No Content" });
    await harness.fixture.whenStable();

    expect(text(page.querySelector("button.google"))).toBe(
      "Continue with Google"
    );
  });

  it("puts Sign in in the header, with the page to return to", async () => {
    const harness = await RouterTestingHarness.create();
    const loading = TestBed.inject(SessionService).load();

    http
      .expectOne("/api/me")
      .flush(null, { status: 401, statusText: "Unauthorized" });
    await loading;

    const navigation = harness.navigateByUrl("/o/alsace-arena/matches");

    await vi.waitFor(() => {
      http.expectOne(`/api/orgs/${ALSACE.slug}`).flush(ALSACE);
    });
    await navigation;
    harness.detectChanges();

    const link = harness.routeNativeElement?.querySelector("header a.account");

    // Alsace Arena's default language is French.
    expect(text(link)).toBe("Se connecter");
    expect(link?.getAttribute("href")).toBe(
      "/sign-in?from=%2Fo%2Falsace-arena%2Fmatches"
    );
  });

  it("puts the person's name in the header once signed in", async () => {
    const { page } = await open("/sign-in", ME);

    expect(
      page.querySelector("header a.account")?.getAttribute("aria-label")
    ).toBe("Your account: Camille Martin");
  });
});
