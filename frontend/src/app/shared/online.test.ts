import { TestBed } from "@angular/core/testing";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { openAlsace, rendered, testProviders, text } from "../testing";
import { OnlineService } from "./online";

/** Stands in for the browser's EventSource, so a test can send events. */
class FakeSource extends EventTarget {
  static readonly CLOSED = 2;
  static last: FakeSource | null = null;

  readonly url: string;
  readyState = 1;
  closed = false;

  constructor(url: string) {
    super();
    this.url = url;
    FakeSource.last = this;
  }

  send(count: string): void {
    this.dispatchEvent(new MessageEvent("online", { data: count }));
  }

  close(): void {
    this.closed = true;
  }
}

const source = (): FakeSource => {
  if (!FakeSource.last) {
    throw new Error("No stream was opened");
  }

  return FakeSource.last;
};

describe("the online count", () => {
  beforeEach(() => {
    FakeSource.last = null;
    vi.stubGlobal("EventSource", FakeSource);
    localStorage.setItem("lang", "en");
    TestBed.configureTestingModule({ providers: testProviders() });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it("follows the count the server sends", () => {
    const online = TestBed.inject(OnlineService);

    expect(source().url).toBe("/api/online");
    expect(online.count()).toBeNull();

    source().send("23");

    expect(online.count()).toBe(23);
  });

  it("ignores anything that isn't a count, and drops it when the stream closes", () => {
    const online = TestBed.inject(OnlineService);

    source().send("23");
    source().send("lots");

    expect(online.count()).toBe(23);

    source().readyState = FakeSource.CLOSED;
    source().dispatchEvent(new Event("error"));

    expect(online.count()).toBeNull();
  });

  it("shows in the header", async () => {
    const harness = await openAlsace("/o/alsace-arena");

    source().send("1234");
    harness.detectChanges();

    const badge = rendered(harness).querySelector("header .online");

    expect(text(badge)).toBe("1,234 online");
    expect(badge?.getAttribute("title")).toBe(
      "Pages open on Tournament Leaderboard right now"
    );
  });

  it("closes the stream with the app", () => {
    TestBed.inject(OnlineService);
    TestBed.resetTestingModule();

    expect(source().closed).toBeTruthy();
  });
});
