import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import type { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { Season } from "../../results/api";
import {
  ALSACE,
  ME,
  openAs,
  rendered,
  SEASONS,
  testProviders,
  text,
  texts,
} from "../../testing";
import { problemOf } from "./seasons";

const API = `/api/orgs/${ALSACE.slug}/admin/seasons`;

const DATED: Season[] = SEASONS.map((season) =>
  season.number === 1
    ? { ...season, endsOn: "2025-07-31", startsOn: "2025-03-01" }
    : season
);

describe("the Seasons page", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  beforeEach(async () => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    harness = await openAs("/org/alsace-arena/admin/seasons", ME, DATED);
    page = rendered(harness);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  /** After a change, the page asks for the seasons again. */
  const answerSeasons = async (): Promise<void> => {
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(`/api/orgs/${ALSACE.slug}/seasons`).flush(DATED);
    });
    harness.detectChanges();
  };

  it("lists the seasons, newest first", () => {
    expect(
      [...page.querySelectorAll("tbody tr")].map((row) => texts(row, "td"))
    ).toStrictEqual([
      ["Season 3", "–", "8", "1–5", "Current", "Edit Season 3"],
      [
        "Season 1",
        "Mar 2025 – Jul 2025",
        "8",
        "1–5",
        "Finished",
        "Edit Season 1",
      ],
    ]);
  });

  it("starts the next season with the newest one's rules", async () => {
    const form = page.querySelector<HTMLFormElement>("form.start");
    const matches = form?.querySelector<HTMLInputElement>("[name=matchCount]");

    if (matches) {
      matches.value = "6";
    }

    form?.requestSubmit();

    const request = http.expectOne(
      (each) => each.url === API && each.method === "POST"
    );

    expect(request.request.body).toStrictEqual({
      copyRulesFrom: 3,
      endsOn: null,
      matchCount: 6,
      maxGames: 5,
      minGames: 1,
      name: null,
      startsOn: null,
    });

    request.flush({ ...SEASONS[1], name: "Season 4", number: 4 });
    await answerSeasons();

    expect(text(page.querySelector("[role=status]"))).toBe(
      "Season 4 was started."
    );
  });

  it("closes a season from its row", async () => {
    page.querySelector<HTMLButtonElement>("tbody tr button")?.click();
    harness.detectChanges();
    const row = page.querySelector("tr.editing");
    row?.querySelector<HTMLInputElement>("input[type=checkbox]")?.click();
    [...(row?.querySelectorAll("button") ?? [])]
      .find((button) => text(button) === "Save")
      ?.click();
    const request = http.expectOne((each) => each.method === "PUT");

    expect(request.request.url).toBe(`${API}/3`);
    expect(request.request.body).toMatchObject({
      finished: true,
      matchCount: 8,
      name: "Season 3",
    });

    request.flush({ ...SEASONS[1], finished: true });
    await answerSeasons();

    expect(page.querySelector("tr.editing")).toBeNull();
  });

  it("says when saved games don't fit the new limits", async () => {
    page.querySelector<HTMLButtonElement>("tbody tr button")?.click();
    harness.detectChanges();
    const row = page.querySelector("tr.editing");
    [...(row?.querySelectorAll("button") ?? [])]
      .find((button) => text(button) === "Save")
      ?.click();
    http
      .expectOne((each) => each.method === "PUT")
      .flush(null, { status: 409, statusText: "Conflict" });

    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "Saved games don’t fit: the season already has a later match, or a match with more games."
      );
    });
  });
});

describe("a season's details", () => {
  const fields = {
    endsOn: "",
    matchCount: "8",
    maxGames: "5",
    minGames: "1",
    name: "Season 4",
    startsOn: "",
  };

  it("are checked as the API checks them", () => {
    expect([
      problemOf(fields, true),
      problemOf({ ...fields, name: "" }, true),
      problemOf({ ...fields, name: "" }, false),
      problemOf({ ...fields, matchCount: "21" }, true),
      problemOf({ ...fields, minGames: "6" }, true),
      problemOf(
        { ...fields, endsOn: "2026-01-01", startsOn: "2026-02-01" },
        true
      ),
    ]).toStrictEqual([null, "name", null, "matches", "games", "dates"]);
  });
});
