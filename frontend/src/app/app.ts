import type { OnInit } from "@angular/core";
import { Component, inject } from "@angular/core";
import { RouterOutlet } from "@angular/router";

import { LanguageService } from "./core/language";
import { SessionService } from "./core/session";
import { ThemeService } from "./core/theme";

@Component({
  imports: [RouterOutlet],
  selector: "app-root",
  template: "<router-outlet />",
})
export class App implements OnInit {
  readonly #session = inject(SessionService);

  constructor() {
    // Started here so the theme, accent and language apply before the first page shows.
    inject(ThemeService);
    inject(LanguageService);
  }

  ngOnInit(): void {
    // Who is signed in decides the header's last button; a failure leaves it on "Sign in".
    this.#session.load().catch(() => null);
  }
}
