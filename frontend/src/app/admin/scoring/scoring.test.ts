import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import type { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { Rules } from "../../results/api";
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
import { draftOf, inputOf, parseRuleNumber, problemOf } from "./scoring";

const RULES_API = `/api/orgs/${ALSACE.slug}/admin/seasons/3/rules`;

/** Season 3's rules with an "at least" bonus, as the platform defaults have. */
const WITH_HONOURS: Rules = {
  ...RULES,
  bonuses: [
    ...RULES.bonuses,
    {
      award: "AT_LEAST",
      code: "HON",
      label: "Most honourable",
      points: 10,
      stat: "HONOURS",
      threshold: 3,
    },
  ],
};

describe("the Scoring screen", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let page: HTMLElement;

  const open = async (url: string, season: number): Promise<void> => {
    harness = await openAs(url, ME, SEASONS);
    await answer(
      harness,
      `/api/orgs/${ALSACE.slug}/seasons/${season}/rules`,
      WITH_HONOURS
    );
    harness.detectChanges();
    page = rendered(harness);
  };

  const button = (label: string): HTMLButtonElement | undefined =>
    [...page.querySelectorAll<HTMLButtonElement>("button")].find(
      (each) => text(each) === label
    );

  /** The box named `label`, by its aria-label or by the label around it. */
  const inputNamed = (label: string): HTMLInputElement | null =>
    page.querySelector<HTMLInputElement>(`input[aria-label="${label}"]`) ??
    [...page.querySelectorAll("label")]
      .find((each) => text(each.querySelector(".label")) === label)
      ?.querySelector("input") ??
    null;

  const type = (label: string, value: string): void => {
    const input = inputNamed(label);

    if (input) {
      input.value = value;
      input.dispatchEvent(new Event("input"));
    }

    harness.detectChanges();
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

  it("shows each stat with its switch and its points, and each bonus", async () => {
    await open("/org/alsace-arena/admin/scoring", 3);

    expect(
      [
        ...page.querySelectorAll<HTMLInputElement>(
          "#stats-title ~ .scroll input:not([role=switch])"
        ),
      ].map((box) => box.value)
    ).toStrictEqual(["1", "1", "-1", "1"]);
    expect(
      [...page.querySelectorAll<HTMLInputElement>("input[role=switch]")].map(
        (box) => box.checked
      )
    ).toStrictEqual([true, true, true, true, false]);
    expect(texts(page, "#stats-title ~ .scroll td span.muted")).toStrictEqual([
      "Shown in tables, not added to points",
    ]);
    expect(texts(page, "#bonuses-title ~ .scroll td span")).toStrictEqual([
      "Highest: First bloods",
      "Lowest: Deaths",
      "Honours: at least",
    ]);
    expect(button("Save the rules")?.disabled).toBeTruthy();
  });

  it("saves what's typed, and says so", async () => {
    await open("/org/alsace-arena/admin/scoring", 3);
    type("Points for each unit of Kills", "1,5");
    type("Points for the KDA ratio", "10");
    type("Threshold for HON", "4");
    page.querySelectorAll<HTMLInputElement>("input[role=switch]")[4]?.click();
    harness.detectChanges();
    button("Save the rules")?.click();
    const request = http.expectOne((each) => each.method === "PUT");

    expect(request.request.url).toBe(RULES_API);
    expect(request.request.body).toStrictEqual({
      bonuses: [
        { code: "FB", points: 10, threshold: null },
        { code: "LD", points: 10, threshold: null },
        { code: "HON", points: 10, threshold: 4 },
      ],
      kdaPoints: 10,
      pointsPerWin: 50,
      stats: [
        { points: 1, recorded: true, stat: "PERFORMANCE_SCORE" },
        { points: 1.5, recorded: true, stat: "KILLS" },
        { points: -1, recorded: true, stat: "DEATHS" },
        { points: 0, recorded: true, stat: "ADR" },
        { points: 1, recorded: true, stat: "CLUTCHES" },
      ],
    });

    request.flush(WITH_HONOURS);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "Season 3’s rules are saved, and its points follow them."
      );
    });
  });

  it("explains a wrong number instead of sending it", async () => {
    await open("/org/alsace-arena/admin/scoring", 3);
    type("Points for each unit of Deaths", "-1.255");
    button("Save the rules")?.click();
    harness.detectChanges();

    expect(text(page.querySelector("[role=status]"))).toBe(
      "Deaths: points go from -1000 to 1000, with up to two decimals."
    );
  });

  it("uses the platform defaults once the admin agrees", async () => {
    await open("/org/alsace-arena/admin/scoring", 3);
    vi.spyOn(globalThis, "confirm").mockReturnValue(true);
    button("Use the platform defaults")?.click();
    const request = http.expectOne(`${RULES_API}/platform-defaults`);

    expect(request.request.method).toBe("POST");

    request.flush(WITH_HONOURS);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(text(page.querySelector("[role=status]"))).toBe(
        "Season 3 now uses the platform defaults."
      );
    });
  });

  it("locks a finished season's rules", async () => {
    await open("/org/alsace-arena/admin/scoring?season=1", 1);

    expect(text(page.querySelector(".notice"))).toBe(
      "Season 1 is finished, so its rules can’t change. Reopen it on the Seasons screen to change them."
    );
    expect(
      [...page.querySelectorAll<HTMLInputElement>("input")].every(
        (box) => box.disabled
      )
    ).toBeTruthy();
    expect(button("Use the platform defaults")?.disabled).toBeTruthy();
  });
});

describe("typed rules", () => {
  it("accept whole and decimal points, a decimal comma, and refuse the rest", () => {
    const limits = { max: 1000, min: -1000 };

    expect(
      ["2", "1,5", "-0.25", " 10 ", "1.255", "abc", "", "1001"].map((typed) =>
        parseRuleNumber(typed, limits)
      )
    ).toStrictEqual([
      2,
      1.5,
      -0.25,
      10,
      "invalid",
      "invalid",
      "invalid",
      "invalid",
    ]);
    expect(parseRuleNumber("2.5", { ...limits, whole: true })).toBe("invalid");
  });

  it("find the first problem, and ignore stats that can't count", () => {
    const draft = draftOf(WITH_HONOURS);

    expect(problemOf(draft, WITH_HONOURS)).toBeNull();
    expect(
      problemOf(
        {
          ...draft,
          stats: { ...draft.stats, ADR: { points: "x", recorded: true } },
        },
        WITH_HONOURS
      )
    ).toBeNull();
    expect(
      problemOf({ ...draft, pointsPerWin: "2.5" }, WITH_HONOURS)
    ).toStrictEqual({
      kind: "win",
    });
    expect(
      problemOf(
        {
          ...draft,
          bonuses: { ...draft.bonuses, HON: { points: "10", threshold: "" } },
        },
        WITH_HONOURS
      )
    ).toStrictEqual({ code: "HON", kind: "threshold" });
  });

  it("send every stat and bonus, a non-countable stat at 0", () => {
    const draft = draftOf(WITH_HONOURS);

    const input = inputOf(
      {
        ...draft,
        stats: { ...draft.stats, ADR: { points: "7", recorded: false } },
      },
      WITH_HONOURS
    );

    expect(input.stats.find((rule) => rule.stat === "ADR")).toStrictEqual({
      points: 0,
      recorded: false,
      stat: "ADR",
    });
    expect(input.bonuses.map((bonus) => bonus.threshold)).toStrictEqual([
      null,
      null,
      3,
    ]);
  });
});
