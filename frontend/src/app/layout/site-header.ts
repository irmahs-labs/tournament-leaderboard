import { DOCUMENT } from "@angular/common";
import { Component, computed, inject, input } from "@angular/core";
import { Router, RouterLink, RouterLinkActive } from "@angular/router";
import { TranslocoDirective } from "@jsverse/transloco";

import { CurrentSeason } from "../results/current-season";
import type { Language } from "../shared/language";
import { LANGUAGES, LanguageService } from "../shared/language";
import { OnlineService } from "../shared/online";
import type { Organization } from "../shared/organization";
import { initials } from "../shared/organization";
import { canAdmin, SessionService } from "../shared/session";
import { ThemeService } from "../shared/theme";

/**
 * The top bar: the organisation's logo and pages when there is one, the platform's name
 * otherwise; then how many pages are open, the season picker, the language and theme switches, and sign-in.
 */
@Component({
  host: { "(document:click)": "closeMenus($event)" },
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

  protected readonly online = inject(OnlineService);
  protected readonly session = inject(SessionService);
  protected readonly current = inject(CurrentSeason);

  readonly #router = inject(Router);
  readonly #document = inject(DOCUMENT);

  /** Moves the focus past the header to the page's content, for keyboard and screen reader users. */
  protected skipToContent(event: Event): void {
    event.preventDefault();
    this.#document.querySelector<HTMLElement>("#main")?.focus();
  }

  /** Whether the signed-in person runs this organisation, so the header offers its admin pages. */
  protected readonly admin = computed(() => {
    const org = this.organization();

    return org !== null && canAdmin(this.session.me(), org.slug);
  });

  /** Whether the signed-in person joined this organisation, so the header offers My seasons. */
  protected readonly member = computed(() => {
    const org = this.organization();

    return (
      org !== null &&
      (this.session
        .me()
        ?.memberships.some((membership) => membership.slug === org.slug) ??
        false)
    );
  });

  /** Whether the page is one of the organisation's admin pages. */
  protected inAdmin(): boolean {
    const org = this.organization();

    return (
      org !== null &&
      this.#router.url.startsWith(`/org/${encodeURIComponent(org.slug)}/admin`)
    );
  }

  /** Closes an open menu when the click lands outside it, as menus do. */
  protected closeMenus(event: Event): void {
    for (const menu of this.#document.querySelectorAll<HTMLDetailsElement>(
      "app-site-header details[open]"
    )) {
      if (!(event.target instanceof Node && menu.contains(event.target))) {
        menu.open = false;
      }
    }
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

  /** English or French: the one button switches to the other. */
  protected toggleLanguage(): void {
    const next: Language = this.language.active === "en" ? "fr" : "en";

    this.language.choose(next);
  }
}
