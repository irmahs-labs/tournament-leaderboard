import { Component, inject } from "@angular/core";
import { RouterOutlet } from "@angular/router";

import { LanguageService } from "./core/language";
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
  }
}
