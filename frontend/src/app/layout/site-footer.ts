import { Component, input } from "@angular/core";
import { TranslocoDirective } from "@jsverse/transloco";

import type { Organization } from "../core/organization";

/** The organisation's name, then the platform's credit, source and contact. */
@Component({
  imports: [TranslocoDirective],
  selector: "app-site-footer",
  styleUrl: "./site-footer.css",
  templateUrl: "./site-footer.html",
})
export class SiteFooter {
  readonly organization = input<Organization | null>(null);

  protected readonly contact = "tournament-leaderboard@irmahs.dev";
}
