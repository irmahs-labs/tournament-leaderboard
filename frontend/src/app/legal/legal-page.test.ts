import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { RouterTestingHarness } from "@angular/router/testing";
import { TranslocoService } from "@jsverse/transloco";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import { openAlsace, rendered, testProviders, text, texts } from "../testing";
import { LEGAL, LEGAL_PAGES } from "./legal-content";

const open = async (url: string): Promise<HTMLElement> =>
  rendered(await RouterTestingHarness.create(url));

describe("the legal pages", () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: testProviders() });
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
  });

  it.each(LEGAL_PAGES)("shows /%s with its title in the tab", async (id) => {
    const page = await open(`/${id}`);

    expect(text(page.querySelector("h1"))).toBe(LEGAL.en[id].title);
    expect(document.title).toBe(
      `${LEGAL.en[id].title} · Tournament Leaderboard`
    );
    expect(page.querySelector("time")?.getAttribute("datetime")).toBe(
      "2026-11-18"
    );
  });

  it("names the publisher and the host in the legal notice", async () => {
    const page = await open("/legal");

    expect(texts(page, "h2")).toStrictEqual([
      "Publisher",
      "Host",
      "Content",
      "Accessibility",
      "Source code",
    ]);
    expect(text(page)).toContain("Hetzner Online GmbH, Industriestr. 25");
  });

  it("lists every cookie and stored value in a table with headers", async () => {
    const page = await open("/cookies");

    expect(texts(page, "tbody th")).toStrictEqual([
      "XSRF-TOKEN",
      "JSESSIONID",
      "lang",
      "theme",
      "signInReturnTo",
    ]);
    expect(texts(page, "thead th")).toHaveLength(4);
    expect(text(page.querySelector("caption"))).toBe(
      "Cookies and browser storage used by the site"
    );
  });

  it("switches to French with the rest of the site", async () => {
    const harness = await RouterTestingHarness.create("/privacy");

    TestBed.inject(TranslocoService).setActiveLang("fr");
    harness.detectChanges();

    expect(text(rendered(harness).querySelector("h1"))).toBe(
      "Politique de confidentialité"
    );
    expect(document.title).toBe(
      "Politique de confidentialité · Tournament Leaderboard"
    );
  });

  it("gives the tab its title back when leaving", async () => {
    const harness = await RouterTestingHarness.create("/terms");

    await harness.navigateByUrl("/sign-in");

    expect(document.title).toBe("Tournament Leaderboard");
  });

  it("links to the cookie policy inside the app", async () => {
    const page = await open("/privacy");

    const link = [...page.querySelectorAll("a")].find(
      (anchor) => text(anchor) === "Cookie policy"
    );

    expect(link?.getAttribute("href")).toBe("/cookies");
  });
});

describe("the footer", () => {
  beforeEach(() => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
  });

  it("links to the legal pages on every organisation page", async () => {
    const page = rendered(await openAlsace("/o/alsace-arena"));
    const footer = page.ownerDocument.querySelector("footer nav");

    expect(texts(footer ?? page, "a")).toStrictEqual([
      "Legal notice",
      "Privacy",
      "Cookies",
      "Terms of use",
    ]);
    expect(footer?.getAttribute("aria-label")).toBe("Legal");
  });
});
