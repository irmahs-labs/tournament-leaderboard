import { Component, computed, inject, input } from "@angular/core";
import { RouterLink, RouterLinkActive } from "@angular/router";
import { TranslocoDirective } from "@jsverse/transloco";

import type { Language } from "../core/language";
import { LANGUAGES, LanguageService } from "../core/language";
import type { Organization } from "../core/organization";
import { initials } from "../core/organization";
import { ThemeService } from "../core/theme";

/**
 * The top bar: the organisation's logo and pages when there is one, the platform's name
 * otherwise; then the language and theme switches.
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

  protected choose(lang: Language): void {
    this.language.choose(lang);
  }
}
