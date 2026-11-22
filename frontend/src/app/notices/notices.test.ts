import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { RouterTestingHarness } from "@angular/router/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { Notice, Notices } from "../shared/notices";
import { SessionService } from "../shared/session";
import { ME, rendered, testProviders, text, texts } from "../testing";
import { keyOf, linkOf } from "./notices";

const notice = (
  id: number,
  kind: Notice["kind"],
  details: Notice["details"],
  read = false
): Notice => ({
  at: "2026-11-22T10:00:00Z",
  details,
  id,
  kind,
  organizationName: "Alsace Arena",
  organizationSlug: "alsace-arena",
  read,
});

const MATCH = {
  date: "2026-12-05",
  location: "Discord #matchs",
  match: 3,
  season: "Season 9",
  startTime: "20:30",
};

const GAME_SAVED = {
  by: "Kestrel",
  change: "GAME_SAVED",
  game: 2,
  match: 3,
  season: "Season 9",
};

const NOTICES: Notices = {
  notices: [
    notice(4, "MATCH_PLANNED", MATCH),
    notice(3, "ADMIN_CHANGE", GAME_SAVED),
    notice(2, "CLAIM_ANSWERED", {
      accepted: false,
      reason: "Not you",
      riotId: "Wren#EUW",
    }),
    notice(1, "MATCH_CANCELLED", { ...MATCH, date: null }, true),
  ],
  unread: 3,
};

describe("a notice's text and link", () => {
  it("has one text per change and per answer", () => {
    expect(keyOf(notice(3, "ADMIN_CHANGE", GAME_SAVED))).toBe(
      "notices.changes.GAME_SAVED"
    );
    expect(
      keyOf(notice(5, "CLAIM_ANSWERED", { accepted: true, riotId: "W#1" }))
    ).toBe("notices.kinds.CLAIM_ACCEPTED");
    expect(
      keyOf(notice(5, "CLAIM_ANSWERED", { accepted: false, riotId: "W#1" }))
    ).toBe("notices.kinds.CLAIM_DECLINED");
  });

  it("leads to the page it's about", () => {
    expect(linkOf(notice(5, "REMINDER", MATCH))).toStrictEqual([
      "/org",
      "alsace-arena",
      "me",
      "matches",
    ]);
    expect(linkOf(notice(5, "CLAIM_ANSWERED", {}))?.at(-1)).toBe("claim");
    expect(linkOf(notice(5, "CLAIM_RECEIVED", {}))?.at(-1)).toBe("claims");
    expect(linkOf(notice(5, "ADMIN_CHANGE", {}))?.at(-1)).toBe("admin");
    expect(linkOf(notice(5, "ORGANIZATION_SUSPENDED", {}))).toBeNull();
  });
});

describe("the Notices page", () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({
      providers: testProviders({ notices: true }),
    });
    http = TestBed.inject(HttpTestingController);
    const loading = TestBed.inject(SessionService).load();
    http.expectOne("/api/me").flush(ME);
    await loading;
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it("counts the unread notices in the header, lists them and marks them read", async () => {
    harness = await RouterTestingHarness.create("/notices");
    await vi.waitFor(() => {
      harness.detectChanges();

      for (const request of http.match("/api/me/notifications")) {
        request.flush(NOTICES);
      }

      expect(text(harness.fixture.nativeElement.querySelector(".count"))).toBe(
        "3"
      );
    });

    const read = http.expectOne("/api/me/notifications/read");

    expect(read.request.method).toBe("POST");
    read.flush(null);
    await vi.waitFor(() => {
      harness.detectChanges();
      expect(harness.fixture.nativeElement.querySelector(".count")).toBeNull();
    });

    const page = rendered(harness);

    expect(texts(page, ".notice p")).toStrictEqual([
      "Match 3 of Season 9 is planned: Dec 5 · 8:30 PM · Discord #matchs. Say whether you can make it.",
      "Kestrel saved game 2 of match 3 in Season 9.",
      "Your claim to Wren#EUW was declined: “Not you”.",
      "Match 3 of Season 9 is called off.",
    ]);
    expect(page.querySelectorAll(".notice.unread")).toHaveLength(3);
    expect(texts(page, ".notice a")).toStrictEqual([
      "My matches",
      "Admin",
      "Your claim",
      "My matches",
    ]);
  });

  it("says when there's nothing new", async () => {
    harness = await RouterTestingHarness.create("/notices");
    await vi.waitFor(() => {
      harness.detectChanges();

      for (const request of http.match("/api/me/notifications")) {
        request.flush({ notices: [], unread: 0 });
      }

      expect(text(rendered(harness).querySelector(".empty"))).toBe(
        "Nothing new for now."
      );
    });
  });
});
