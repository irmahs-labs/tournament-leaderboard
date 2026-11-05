import { TestBed } from "@angular/core/testing";
import { beforeEach, describe, expect, it } from "vitest";

import { App } from "./app";

describe(App, () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
    }).compileComponents();
  });

  it("should create the app", () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it("should show the coming-soon placeholder", async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    // SAFETY: the root of a component fixture is always an HTMLElement.
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector("h1")?.textContent).toContain("Coming soon");
  });
});
