import { Component, computed, inject, input } from "@angular/core";
import { Router, RouterLink, RouterLinkActive } from "@angular/router";
import { TranslocoDirective } from "@jsverse/transloco";

import type { Language } from "../core/language";
import { LANGUAGES, LanguageService } from "../core/language";
import type { Organization } from "../core/organization";
import { initials } from "../core/organization";
import { CurrentSeason } from "../core/seasons";
import { displayName, SessionService } from "../core/session";
import { ThemeService } from "../core/theme";

/**
 * The top bar: the organisation's logo and pages when there is one, the platform's name
 * otherwise; then the season picker, the language and theme switches, and sign-in.
 */
@Component({
  imports: [RouterLink, RouterLinkActive, TranslocoDirective],
  selector: "app-site-header",
  styleUrl: "./site-header.css",
  templateUrl: "./site-header.html",
})
export class SiteHeader {
  readonly organization = input<Organization | null>(null);

  protected readonly theme = inject(ThemeService);
  protected readonly language = inject(LanguageService);
  protected readonly languages = LANGUAGES;
  protected readonly initials = computed(() =>
    initials(this.organization()?.name ?? "")
  );

  protected readonly session = inject(SessionService);
  protected readonly current = inject(CurrentSeason);

  protected readonly name = computed(() => {
    const me = this.session.me();

    return me ? displayName(me) : "";
  });

  readonly #router = inject(Router);

  /** The page to come back to after signing in. */
  protected here(): string {
    return this.#router.url;
  }

  /** Shows another season on the same page. */
  protected pickSeason(value: string): void {
    void this.#router.navigate([], {
      queryParams: { season: value },
      queryParamsHandling: "merge",
    });
  }

  protected choose(lang: Language): void {
    this.language.choose(lang);
  }
}
