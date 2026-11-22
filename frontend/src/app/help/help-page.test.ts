import { TestBed } from "@angular/core/testing";
import { RouterTestingHarness } from "@angular/router/testing";
import { TranslocoService } from "@jsverse/transloco";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import { rendered, testProviders, text, texts } from "../testing";
import { HELP } from "./help-content";

describe("the Help page", () => {
  beforeEach(() => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
  });

  afterEach(() => {
    localStorage.clear();
  });

  it("answers players, admins and account questions, each opening on its own", async () => {
    const harness = await RouterTestingHarness.create("/help");
    const page = rendered(harness);

    expect(text(page.querySelector("h1"))).toBe("Help");
    expect(texts(page, "section h2")).toStrictEqual([
      "For players",
      "For admins",
      "Your account",
    ]);
    expect(texts(page, ".toc a")).toStrictEqual([
      "For players",
      "For admins",
      "Your account",
    ]);
    expect(
      page.querySelector<HTMLDetailsElement>("#players-join")?.open
    ).toBeFalsy();
    expect(text(page.querySelector("#admins-invite-players summary"))).toBe(
      "How do I invite players?"
    );
  });

  it("follows the language on screen", async () => {
    const harness = await RouterTestingHarness.create("/help");
    TestBed.inject(TranslocoService).setActiveLang("fr");
    harness.detectChanges();

    expect(text(rendered(harness).querySelector("h1"))).toBe("Aide");
  });
});

const ids = (lang: "en" | "fr") =>
  HELP[lang].sections.map((section) => [
    section.id,
    section.questions.map((question) => question.id),
  ]);

describe("the help content", () => {
  it("asks the same questions in both languages", () => {
    expect(ids("fr")).toStrictEqual(ids("en"));
  });
});
