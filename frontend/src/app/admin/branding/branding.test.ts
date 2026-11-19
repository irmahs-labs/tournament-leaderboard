import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import type { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { Organization } from "../../shared/organization";
import {
  ALSACE,
  ME,
  openAs,
  rendered,
  testProviders,
  text,
  texts,
} from "../../testing";
import type { BrandingFields } from "./branding";
import { brandingInput, linkAllowed, problemOf } from "./branding";

const API = `/api/orgs/${ALSACE.slug}/admin`;

const FIELDS: BrandingFields = {
  accentColor: "#2f5bff",
  defaultLocale: "fr",
  description: "  ",
  name: " Alsace Arena ",
  playersPerMatch: "12",
  socialLinks: {
    discord: "https://discord.gg/alsace",
    instagram: "",
    tiktok: "",
    twitch: "",
    website: "https://alsace-arena.example",
    youtube: "https://www.youtube.com/@alsace",
  },
  tournamentName: "Spike Tournament",
};

describe("the branding checks", () => {
  it("accepts https links on each site, and the website anywhere", () => {
    expect(linkAllowed("discord", "https://discord.gg/alsace")).toBeTruthy();
    expect(linkAllowed("discord", "https://discord.com/invite/x")).toBeTruthy();
    expect(linkAllowed("youtube", "https://youtu.be/abc")).toBeTruthy();
    expect(linkAllowed("instagram", "")).toBeTruthy();
    expect(linkAllowed("website", "https://alsace-arena.example")).toBeTruthy();
  });

  it("refuses other sites, plain http, and tricks with the address", () => {
    expect(
      linkAllowed("discord", "https://discord.gg.evil.example/x")
    ).toBeFalsy();
    expect(linkAllowed("twitch", "http://twitch.tv/alsace")).toBeFalsy();
    expect(linkAllowed("youtube", "https://instagram.com/alsace")).toBeFalsy();
    expect(
      linkAllowed("website", ["javascript", "alert(1)"].join(":"))
    ).toBeFalsy();
    expect(
      linkAllowed("website", "https://me@alsace-arena.example")
    ).toBeFalsy();
  });

  it("says the first thing wrong with the form", () => {
    expect(problemOf(FIELDS)).toBeNull();
    expect(problemOf({ ...FIELDS, name: " " })).toBe("name");
    expect(problemOf({ ...FIELDS, accentColor: "blue" })).toBe("accent");
    expect(problemOf({ ...FIELDS, playersPerMatch: "41" })).toBe("players");
    expect(
      problemOf({
        ...FIELDS,
        socialLinks: { ...FIELDS.socialLinks, tiktok: "https://x.example" },
      })
    ).toBe("link.tiktok");
  });

  it("sends the values tidied up, with nothing for empty fields", () => {
    expect(brandingInput(FIELDS)).toStrictEqual({
      accentColor: "#2F5BFF",
      defaultLocale: "fr",
      description: null,
      name: "Alsace Arena",
      playersPerMatch: 12,
      socialLinks: {
        discord: "https://discord.gg/alsace",
        instagram: null,
        tiktok: null,
        twitch: null,
        website: "https://alsace-arena.example",
        youtube: "https://www.youtube.com/@alsace",
      },
      tournamentName: "Spike Tournament",
    });
  });
});

describe("the Branding screen", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  const button = (label: string): HTMLButtonElement | undefined =>
    [...page.querySelectorAll<HTMLButtonElement>("button")].find((each) =>
      text(each)?.startsWith(label)
    );

  /** The whole shell, header and footer included. */
  const shell = (): HTMLElement => {
    const root: unknown = harness.fixture.nativeElement;

    if (!(root instanceof HTMLElement)) {
      throw new TypeError("The harness rendered nothing");
    }

    return root;
  };

  const field = (name: string): HTMLInputElement | null =>
    page.querySelector<HTMLInputElement>(`input[name=${name}]`);

  beforeEach(async () => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    harness = await openAs("/o/alsace-arena/admin/branding", ME);
    page = rendered(harness);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it("fills the form from the organisation", () => {
    expect(field("name")?.value).toBe("Alsace Arena");
    expect(field("tournamentName")?.value).toBe("Spike Tournament");
    expect(field("playersPerMatch")?.value).toBe("10");
    expect(field("accentColor")?.value).toBe("#2F5BFF");
  });

  it("fills the social links, and shows the address", () => {
    expect(field("discord")?.value).toBe("https://discord.gg/alsace");
    expect(field("twitch")?.value).toBe("");
    expect(text(page.querySelector(".address"))).toContain("/o/alsace-arena");
  });

  it("saves the branding, and the footer and header follow", async () => {
    const name = field("name");
    const twitch = field("twitch");

    if (name && twitch) {
      name.value = "Alsace Arena Strasbourg";
      twitch.value = "https://twitch.tv/alsace";
    }

    button("Français")?.click();
    button("Save branding")?.click();
    const request = http.expectOne(`${API}/branding`);

    expect(request.request.method).toBe("PUT");
    expect(request.request.body).toMatchObject({
      defaultLocale: "fr",
      name: "Alsace Arena Strasbourg",
      socialLinks: { twitch: "https://twitch.tv/alsace" },
    });

    const saved: Organization = {
      ...ALSACE,
      name: "Alsace Arena Strasbourg",
      socialLinks: {
        ...ALSACE.socialLinks,
        twitch: "https://twitch.tv/alsace",
      },
    };

    request.flush(saved);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector(".save [role=status]"))).toBe(
        "Branding saved."
      );
    });

    expect(text(shell().querySelector("header .name"))).toBe(
      "Alsace Arena Strasbourg"
    );
    expect(texts(shell(), "footer nav:first-of-type a")).toContain("Twitch");
  });

  it("says what's wrong before sending", () => {
    const discord = field("discord");

    if (discord) {
      discord.value = "https://example.com/not-discord";
    }

    button("Save branding")?.click();
    harness.detectChanges();

    expect(text(page.querySelector(".save [role=status]"))).toBe(
      "The Discord link must be an https:// address on Discord."
    );
  });

  it("uploads a logo as it is and shows it in the header", async () => {
    const input = page.querySelector<HTMLInputElement>("input[type=file]");
    const file = new File(["<svg/>"], "logo.svg", { type: "image/svg+xml" });

    if (input) {
      Object.defineProperty(input, "files", { value: [file] });
      input.dispatchEvent(new Event("change"));
    }

    const request = http.expectOne(`${API}/logo`);

    expect(request.request.method).toBe("PUT");
    expect(request.request.body).toBe(file);
    expect(request.request.headers.get("Content-Type")).toBe("image/svg+xml");

    request.flush({ ...ALSACE, logo: "/api/orgs/alsace-arena/logo?v=1" });
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(shell().querySelector("header img.logo")).not.toBeNull();
    });
    expect(text(page.querySelector(".logo-actions [role=status]"))).toBe(
      "Logo saved."
    );
  });
});
