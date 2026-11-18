import { DOCUMENT } from "@angular/common";
import { Component, computed, inject, input } from "@angular/core";
import { Router, RouterLink, RouterLinkActive } from "@angular/router";
import { TranslocoDirective } from "@jsverse/transloco";

import { CurrentSeason } from "../results/current-season";
import type { Language } from "../shared/language";
import { LANGUAGES, LanguageService } from "../shared/language";
import type { Organization } from "../shared/organization";
import { initials } from "../shared/organization";
import { SessionService } from "../shared/session";
import { ThemeService } from "../shared/theme";

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

  readonly #router = inject(Router);
  readonly #document = inject(DOCUMENT);

  /** Moves the focus past the header to the page's content, for keyboard and screen reader users. */
  protected skipToContent(event: Event): void {
    event.preventDefault();
    this.#document.querySelector<HTMLElement>("#main")?.focus();
  }

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
