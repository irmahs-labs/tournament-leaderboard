import { HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { beforeEach, describe, expect, it } from "vitest";

import { ME, testProviders } from "../testing";
import {
  displayName,
  isSafeReturn,
  SessionService,
  takeReturn,
} from "./session";

describe(SessionService, () => {
  let http: HttpTestingController;
  let session: SessionService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionService);
  });

  it("knows who is signed in", async () => {
    const loading = session.load();

    http.expectOne("/api/me").flush(ME);

    await expect(loading).resolves.toStrictEqual(ME);
    expect(session.me()).toStrictEqual(ME);
  });

  it("treats 401 as signed out", async () => {
    const loading = session.load();

    http
      .expectOne("/api/me")
      .flush(null, { status: 401, statusText: "Unauthorized" });

    await expect(loading).resolves.toBeNull();
    expect(session.me()).toBeNull();
  });

  it("signs out with a POST", async () => {
    const signingOut = session.signOut();

    const request = http.expectOne("/api/logout");

    expect(request.request.method).toBe("POST");
    request.flush(null, { status: 204, statusText: "No Content" });
    await signingOut;
    expect(session.me()).toBeNull();
  });

  it("returns to a page only once", () => {
    sessionStorage.setItem("signInReturnTo", "/org/alsace-arena/matches");

    expect(takeReturn()).toBe("/org/alsace-arena/matches");
    expect(takeReturn()).toBeNull();
  });
});

describe(isSafeReturn, () => {
  it("accepts the app's own pages", () => {
    expect(isSafeReturn("/org/alsace-arena")).toBeTruthy();
  });

  it("refuses other sites and the sign-in page", () => {
    expect(isSafeReturn("//evil.example")).toBeFalsy();
    expect(isSafeReturn("/\\evil.example")).toBeFalsy();
    expect(isSafeReturn("https://evil.example")).toBeFalsy();
    expect(isSafeReturn("/sign-in?from=/x")).toBeFalsy();
  });

  it("refuses nothing at all", () => {
    expect(isSafeReturn(null)).toBeFalsy();
  });
});

describe(displayName, () => {
  it("falls back to the start of the email", () => {
    expect(displayName(ME)).toBe("Camille Martin");
    expect(displayName({ ...ME, displayName: " " })).toBe("camille");
  });
});
