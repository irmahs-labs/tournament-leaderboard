import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { Router } from "@angular/router";
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

  it("shows the platform's home page at the root", async () => {
    const harness = await RouterTestingHarness.create("/");

    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne("/api/featured").flush(null, {
        status: 204,
        statusText: "No Content",
      });
    });
    harness.detectChanges();
    const page = rendered(harness);

    expect(text(page.querySelector("header .name"))).toBe(
      "Tournament Leaderboard"
    );
    expect(page.querySelector("#create form")).not.toBeNull();
    expect(page.querySelector(".featured")).toBeNull();
  });

  it("sends the addresses from before /org/ on to /org/, with the season", async () => {
    await open("/o/alsace-arena/matches?season=1");

    expect(TestBed.inject(Router).url).toBe(
      "/org/alsace-arena/matches?season=1"
    );
  });

  it("shows the organisation in the header and the footer", async () => {
    const page = await open("/org/alsace-arena");

    expect(text(page.querySelector("header .logo"))).toBe("AA");
    expect(text(page.querySelector("footer .org"))).toBe(
      "Alsace Arena · Spike Tournament"
    );
    expect(document.title).toBe("Alsace Arena · Spike Tournament");
  });

  it("applies the organisation's accent and language", async () => {
    await open("/org/alsace-arena");

    const { style } = document.documentElement;

    expect(style.getPropertyValue("--accent")).toBe("#2F5BFF");
    expect(style.getPropertyValue("--on-accent")).toBe("#ffffff");
    expect(TestBed.inject(TranslocoService).getActiveLang()).toBe("fr");
    expect(document.documentElement.lang).toBe("fr");
  });

  it("keeps a visitor's own language over the organisation's", async () => {
    localStorage.setItem("lang", "en");

    const page = await open("/org/alsace-arena/matches");

    expect(text(page.querySelector("h1"))).toBe("Matches");
  });

  it("switches language with the one EN / FR button and remembers it", async () => {
    const page = await open("/org/alsace-arena");
    const button = page.querySelector<HTMLButtonElement>("header .language");

    // Alsace Arena's own language is French.
    expect(text(button?.querySelector(".on"))).toBe("FR");
    expect(button?.getAttribute("aria-label")).toBe(
      "EN / FR : passer en anglais"
    );

    button?.click();
    TestBed.tick();

    expect(text(page.querySelector("main h1"))).toBe("Season leaderboard");
    expect(text(button?.querySelector(".on"))).toBe("EN");
    expect(localStorage.getItem("lang")).toBe("en");
  });

  it("switches back from English to French with the same button", async () => {
    localStorage.setItem("lang", "en");
    const page = await open("/org/alsace-arena");
    const button = page.querySelector<HTMLButtonElement>("header .language");

    expect(text(button?.querySelector(".on"))).toBe("EN");

    button?.click();
    TestBed.tick();

    expect(text(page.querySelector("main h1"))).toBe("Classement de la saison");
    expect(text(button?.querySelector(".on"))).toBe("FR");
    expect(localStorage.getItem("lang")).toBe("fr");
  });

  it("switches theme with the pill and remembers it", async () => {
    const page = await open("/org/alsace-arena");

    const pill = page.querySelector<HTMLButtonElement>(
      'header [role="switch"]'
    );

    const before = document.documentElement.dataset["theme"];

    expect(pill?.getAttribute("aria-checked")).toBe(String(before === "dark"));

    pill?.click();
    TestBed.tick();

    const after = document.documentElement.dataset["theme"];

    expect(after).not.toBe(before);
    expect(pill?.getAttribute("aria-checked")).toBe(String(after === "dark"));
    expect(localStorage.getItem("theme")).toBe(after);
  });

  it("marks the current page in the navigation", async () => {
    const page = await open("/org/alsace-arena/matches");

    expect(text(page.querySelector('nav a[aria-current="page"]'))).toBe(
      "Matchs"
    );
  });

  it("shows Not found for an organisation that doesn't exist, keeping the address", async () => {
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl("/org/nowhere");

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
