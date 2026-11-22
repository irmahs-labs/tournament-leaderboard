import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import type { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  ALSACE,
  ME,
  openAs,
  rendered,
  SEASONS,
  testProviders,
  text,
} from "../../testing";
import type { Schedule, ScheduledMatch } from "../api";
import { planInput, planProblem } from "./schedule";

const API = `/api/orgs/${ALSACE.slug}/admin/seasons/3/schedule`;

const UNPLANNED: ScheduledMatch = {
  confirmed: 0,
  date: null,
  id: null,
  location: null,
  locked: false,
  number: 2,
  places: 10,
  played: false,
  replyBy: null,
  startTime: null,
  status: null,
  waiting: 0,
};

const SCHEDULE: Schedule = {
  defaultPlaces: 10,
  finished: false,
  matches: [
    {
      ...UNPLANNED,
      confirmed: 10,
      date: "2026-11-13",
      id: 31,
      number: 1,
      played: true,
      status: "PLANNED",
    },
    UNPLANNED,
    {
      ...UNPLANNED,
      confirmed: 6,
      date: "2026-12-05",
      id: 33,
      location: "Discord #matchs",
      number: 3,
      replyBy: "2026-12-03",
      startTime: "20:30:00",
      status: "PLANNED",
      waiting: 2,
    },
  ],
  name: "Season 3",
  number: 3,
};

describe("a match's plan", () => {
  const fields = {
    date: "2026-12-05",
    location: " Discord #matchs ",
    places: "8",
    replyBy: "2026-12-03",
    startTime: "20:30",
  };

  it("is checked as the API does", () => {
    expect(planProblem(fields)).toBeNull();
    expect(planProblem({ ...fields, places: "1" })).toBe(
      "adminSchedule.problems.places"
    );
    expect(planProblem({ ...fields, replyBy: "2026-12-06" })).toBe(
      "adminSchedule.problems.replyBy"
    );
    expect(planProblem({ ...fields, location: "x".repeat(201) })).toBe(
      "adminSchedule.problems.location"
    );
  });

  it("sends empty fields as nothing", () => {
    expect(
      planInput(
        { date: "", location: "  ", places: "10", replyBy: "", startTime: "" },
        null
      )
    ).toStrictEqual({
      cancelled: null,
      date: null,
      location: null,
      places: 10,
      replyBy: null,
      startTime: null,
    });
  });
});

describe("the Schedule page", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  const button = (label: string): HTMLButtonElement | undefined =>
    [...page.querySelectorAll<HTMLButtonElement>("button")].find((each) =>
      text(each)?.startsWith(label)
    );

  beforeEach(async () => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    harness = await openAs("/org/alsace-arena/admin/schedule", ME, SEASONS);
    await vi.waitFor(() => {
      harness.detectChanges();
      http.expectOne(API).flush(SCHEDULE);
    });
    harness.detectChanges();
    page = rendered(harness);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it("lists every match, planned or not, with how full it is", () => {
    expect(
      [...page.querySelectorAll("tbody tr")].map((row) =>
        text(row.querySelector(".pill"))
      )
    ).toStrictEqual(["Played", "Not planned", "Planned"]);
    expect(text(page.querySelector("tbody tr:nth-child(3) .num"))).toBe(
      "6 of 10 places taken · 2 waiting"
    );
    expect(
      page.querySelector("tbody tr:nth-child(3) a")?.getAttribute("href")
    ).toBe("/org/alsace-arena/admin/matches/33/roster");
    expect(button("Edit Match 1")).toBeUndefined();
  });

  it("plans a match", async () => {
    button("Plan Match 2")?.click();
    harness.detectChanges();
    const row = page.querySelector("tr.editing");

    const set = (label: string, value: string): void => {
      const input = row?.querySelector<HTMLInputElement>(
        `[aria-label="${label}"]`
      );

      if (input) {
        input.value = value;
      }
    };

    set("Date", "2026-12-12");
    set("Start time", "21:00");
    set("Places", "8");
    [...(row?.querySelectorAll("button") ?? [])]
      .find((each) => text(each) === "Save")
      ?.click();

    const sent = http.expectOne(`${API}/2`);

    expect(sent.request.body).toStrictEqual({
      cancelled: null,
      date: "2026-12-12",
      location: null,
      places: 8,
      replyBy: null,
      startTime: "21:00",
    });
    sent.flush(SCHEDULE);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "Match 2 is planned."
      );
    });
  });

  it("calls a match off, keeping its plan", () => {
    button("Call off Match 3")?.click();
    const sent = http.expectOne(`${API}/3`);

    expect(sent.request.body).toMatchObject({
      cancelled: true,
      date: "2026-12-05",
      places: 10,
    });
    sent.flush(SCHEDULE);
  });
});
