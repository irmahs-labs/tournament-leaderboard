import { Component, input } from "@angular/core";
import { RouterLink } from "@angular/router";
import { TranslocoDirective } from "@jsverse/transloco";

import { CONTACT, LEGAL_PAGES } from "../legal/legal-content";
import type { Organization } from "../shared/organization";
import { fullName } from "../shared/organization";

/** The organisation's name, then three lines: the credit, the legal pages, then GitHub and contact. */
@Component({
  imports: [RouterLink, TranslocoDirective],
  selector: "app-site-footer",
  styleUrl: "./site-footer.css",
  templateUrl: "./site-footer.html",
})
export class SiteFooter {
  readonly organization = input<Organization | null>(null);

  protected readonly fullName = fullName;
  protected readonly contact = CONTACT;
  protected readonly legalPages = LEGAL_PAGES;
}
