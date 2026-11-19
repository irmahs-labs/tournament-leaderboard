import { Component, computed, input } from "@angular/core";
import { RouterLink } from "@angular/router";
import { TranslocoDirective } from "@jsverse/transloco";

import { CONTACT, LEGAL_PAGES } from "../legal/legal-content";
import type { Organization } from "../shared/organization";
import { fullName, SOCIAL_SITES } from "../shared/organization";

/** The organisation's name and social links, then three lines: the credit, the legal pages, then GitHub and contact. */
@Component({
  imports: [RouterLink, TranslocoDirective],
  selector: "app-site-footer",
  styleUrl: "./site-footer.css",
  templateUrl: "./site-footer.html",
})
export class SiteFooter {
  readonly organization = input<Organization | null>(null);

  protected readonly fullName = fullName;

  /** The organisation's social links that are set, in the usual order. */
  protected readonly links = computed(() => {
    const links = this.organization()?.socialLinks;

    return SOCIAL_SITES.flatMap((site) => {
      const address = links?.[site];

      return address ? [{ address, site }] : [];
    });
  });
  protected readonly contact = CONTACT;
  protected readonly legalPages = LEGAL_PAGES;
}
