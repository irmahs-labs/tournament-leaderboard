import { Component, inject } from "@angular/core";
import { Title } from "@angular/platform-browser";
import { RouterLink } from "@angular/router";
import { TranslocoDirective } from "@jsverse/transloco";

import { ThemeService } from "../core/theme";
import { SiteFooter } from "../layout/site-footer";
import { SiteHeader } from "../layout/site-header";

/** Any unknown address, and an organisation that doesn't exist or is suspended. */
@Component({
  imports: [RouterLink, SiteFooter, SiteHeader, TranslocoDirective],
  selector: "app-not-found",
  styleUrls: ["../layout/page.css", "./not-found.css"],
  template: `
    <div class="page" *transloco="let t">
      <app-site-header />
      <main class="main lost">
        <div class="copy">
          <span class="code" aria-hidden="true">404</span>
          <h1 class="display">{{ t("notFound.title") }}</h1>
          <p class="lede">{{ t("notFound.body") }}</p>
          <div>
            <a class="button" routerLink="/">{{ t("notFound.home") }}</a>
          </div>
        </div>
        <div class="slab" aria-hidden="true"><span></span></div>
      </main>
      <app-site-footer />
    </div>
  `,
})
export class NotFoundPage {
  constructor() {
    inject(ThemeService).setAccent(null);
    inject(Title).setTitle("Tournament Leaderboard");
  }
}
