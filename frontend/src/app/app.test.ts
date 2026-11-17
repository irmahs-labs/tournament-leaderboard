import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { RouterTestingHarness } from "@angular/router/testing";
import { TranslocoService } from "@jsverse/transloco";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "./app";
import { SessionService } from "./shared/session";
import { ME, openAlsace, rendered, testProviders, text } from "./testing";

const open = async (url: string): Promise<HTMLElement> =>
  rendered(await openAlsace(url));

describe("the app's pages", () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    delete document.documentElement.dataset["theme"];
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it("sends the home page to Alsace Arena for now", async () => {
    const page = await open("/");

    expect(text(page.querySelector("header .name"))).toBe("Alsace Arena");
  });

  it("shows the organisation in the header and the footer", async () => {
    const page = await open("/o/alsace-arena");

    expect(text(page.querySelector("header .logo"))).toBe("AA");
    expect(text(page.querySelector("footer .org"))).toBe(
      "Alsace Arena · Spike Tournament"
    );
    expect(document.title).toBe("Alsace Arena · Spike Tournament");
  });

  it("applies the organisation's accent and language", async () => {
    await open("/o/alsace-arena");

    const { style } = document.documentElement;

    expect(style.getPropertyValue("--accent")).toBe("#2F5BFF");
    expect(style.getPropertyValue("--on-accent")).toBe("#ffffff");
    expect(TestBed.inject(TranslocoService).getActiveLang()).toBe("fr");
    expect(document.documentElement.lang).toBe("fr");
  });

  it("keeps a visitor's own language over the organisation's", async () => {
    localStorage.setItem("lang", "en");

    const page = await open("/o/alsace-arena/matches");

    expect(text(page.querySelector("h1"))).toBe("Matches");
  });

  it("switches language from the header and remembers it", async () => {
    const page = await open("/o/alsace-arena");

    const english = page.querySelector<HTMLButtonElement>(
      'header button[lang="en"]'
    );

    english?.click();
    TestBed.tick();

    expect(text(page.querySelector("main h1"))).toBe("Season leaderboard");
    expect(english?.getAttribute("aria-pressed")).toBe("true");
    expect(localStorage.getItem("lang")).toBe("en");
  });

  it("switches theme from the header and remembers it", async () => {
    const page = await open("/o/alsace-arena");
    const before = document.documentElement.dataset["theme"];

    page.querySelector<HTMLButtonElement>("header .icon")?.click();
    TestBed.tick();

    const after = document.documentElement.dataset["theme"];

    expect(after).not.toBe(before);
    expect(localStorage.getItem("theme")).toBe(after);
  });

  it("marks the current page in the navigation", async () => {
    const page = await open("/o/alsace-arena/matches");

    expect(text(page.querySelector('nav a[aria-current="page"]'))).toBe(
      "Matchs"
    );
  });

  it("shows Not found for an organisation that doesn't exist, keeping the address", async () => {
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl("/o/nowhere");

    await vi.waitFor(() => {
      http
        .expectOne("/api/orgs/nowhere")
        .flush(null, { status: 404, statusText: "Not Found" });
    });
    await navigation;
    harness.detectChanges();

    const page = rendered(harness);

    expect(text(page.querySelector("h1"))).toBe("Page not found");
    expect(text(page.querySelector("header .name"))).toBe(
      "Tournament Leaderboard"
    );
  });

  it("shows Not found for an unknown address", async () => {
    const harness = await RouterTestingHarness.create("/no/such/page");

    expect(text(rendered(harness).querySelector("h1"))).toBe("Page not found");
  });
});

describe("the app itself", () => {
  it("asks who is signed in once it starts", async () => {
    TestBed.configureTestingModule({ providers: testProviders() });

    const fixture = TestBed.createComponent(App);
    const http = TestBed.inject(HttpTestingController);

    fixture.detectChanges();
    http.expectOne("/api/me").flush(ME);
    await fixture.whenStable();

    expect(TestBed.inject(SessionService).me()).toStrictEqual(ME);
    http.match(() => true);
  });
});
