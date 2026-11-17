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
export class App {
  constructor() {
    // Started here so the theme, accent and language apply before the first page shows.
    inject(ThemeService);
    inject(LanguageService);
    // Who is signed in decides the header's last button; a failure leaves it on "Sign in".
    inject(SessionService)
      .load()
      .catch(() => null);
  }
}
