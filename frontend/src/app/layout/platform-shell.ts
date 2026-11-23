import { Component, inject } from "@angular/core";
import { Title } from "@angular/platform-browser";
import { RouterOutlet } from "@angular/router";

import { ThemeService } from "../shared/theme";
import { SiteFooter } from "./site-footer";
import { SiteHeader } from "./site-header";

/** The pages outside any organisation, such as sign-in and Not found: the platform's name and red. */
@Component({
  imports: [RouterOutlet, SiteFooter, SiteHeader],
  selector: "app-platform-shell",
  styleUrl: "./page.css",
  template: `
    <div class="page">
      <app-site-header />
      <main class="main" id="main" tabindex="-1"><router-outlet /></main>
      <app-site-footer />
    </div>
  `,
})
export class PlatformShell {
  constructor() {
    inject(ThemeService).setAccent(null);
    inject(Title).setTitle("Tournament Leaderboard");
  }
}
