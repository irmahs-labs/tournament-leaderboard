import { Component, effect, inject, input } from "@angular/core";
import { toSignal } from "@angular/core/rxjs-interop";
import { Title } from "@angular/platform-browser";
import { ActivatedRoute, RouterOutlet } from "@angular/router";

import { CurrentSeason } from "../results/current-season";
import { LanguageService } from "../shared/language";
import type { Organization } from "../shared/organization";
import { fullName } from "../shared/organization";
import { ThemeService } from "../shared/theme";
import { SiteFooter } from "./site-footer";
import { SiteHeader } from "./site-header";

/**
 * Every page of one organisation: its header, its accent and language, its footer, and the
 * season picked in the header (`?season=`).
 */
@Component({
  imports: [RouterOutlet, SiteFooter, SiteHeader],
  selector: "app-org-shell",
  styleUrl: "./page.css",
  template: `
    <div class="page">
      <app-site-header [organization]="organization()" />
      <main class="main" id="main" tabindex="-1"><router-outlet /></main>
      <app-site-footer [organization]="organization()" />
    </div>
  `,
})
export class OrgShell {
  /** From the route's resolver. */
  readonly organization = input.required<Organization>();

  readonly #query = toSignal(inject(ActivatedRoute).queryParamMap);

  constructor() {
    const current = inject(CurrentSeason);
    effect(() => {
      const season = Number(this.#query()?.get("season"));
      current.use(
        this.organization(),
        Number.isInteger(season) && season > 0 ? season : null
      );
    });
    const theme = inject(ThemeService);
    const language = inject(LanguageService);
    const title = inject(Title);
    effect(() => {
      const org = this.organization();
      theme.setAccent(org.accentColor);
      language.useDefault(org.defaultLocale);
      title.setTitle(fullName(org));
    });
  }
}
