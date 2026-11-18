import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import type { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  ALSACE,
  answer,
  ME,
  openAs,
  rendered,
  RULES,
  SEASONS,
  testProviders,
  text,
  texts,
} from "../../testing";
import { effectOf } from "./scoring";

describe("the Record switches", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  beforeEach(async () => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    harness = await openAs("/o/alsace-arena/admin/scoring", ME, SEASONS);
    await answer(harness, `/api/orgs/${ALSACE.slug}/seasons/3/rules`, RULES);
    harness.detectChanges();
    page = rendered(harness);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  const save = (): HTMLButtonElement | undefined =>
    [...page.querySelectorAll<HTMLButtonElement>("button")].find(
      (button) => text(button) === "Save the stats to record"
    );

  it("shows each stat, whether it's recorded, and how it counts", () => {
    expect(
      [...page.querySelectorAll("tbody tr")].map((row) =>
        texts(row, "th, td.muted")
      )
    ).toStrictEqual([
      ["Performance", "Adds"],
      ["Kills", "Adds"],
      ["Deaths", "Subtracts"],
      ["Average damage per round", "Shown in tables, not added to points"],
      ["Clutches", "Adds"],
    ]);
    expect(
      [...page.querySelectorAll<HTMLInputElement>("input[role=switch]")].map(
        (box) => box.checked
      )
    ).toStrictEqual([true, true, true, true, false]);
    expect(texts(page, ".switch .visually-hidden")[0]).toBe(
      "Record Performance"
    );
    expect(save()?.disabled).toBeTruthy();
  });

  it("saves exactly the stats switched on", async () => {
    page.querySelector<HTMLInputElement>("input[role=switch]")?.click();
    page.querySelectorAll<HTMLInputElement>("input[role=switch]")[4]?.click();
    harness.detectChanges();
    save()?.click();
    const request = http.expectOne((each) => each.method === "PUT");

    expect(request.request.url).toBe(
      `/api/orgs/${ALSACE.slug}/admin/seasons/3/recorded-stats`
    );
    expect(request.request.body).toStrictEqual({
      stats: ["KILLS", "DEATHS", "ADR", "CLUTCHES"],
    });

    request.flush(RULES);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "Season 3 records these stats from now on."
      );
    });
  });
});

describe("a stat's effect", () => {
  it("is shown only when it can't count, else adds, subtracts or isn't counted", () => {
    expect(
      [
        { countable: false, effect: 0 },
        { countable: true, effect: 1 },
        { countable: true, effect: -1 },
        { countable: true, effect: 0 },
      ].map((rule) => effectOf({ ...rule, recorded: true, stat: "KILLS" }))
    ).toStrictEqual(["shownOnly", "adds", "subtracts", "notCounted"]);
  });
});
