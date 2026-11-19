import { DOCUMENT } from "@angular/common";
import { HttpClient, httpResource } from "@angular/common/http";
import { Component, computed, inject, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import { firstValueFrom } from "rxjs";

import { AdminAction } from "../admin/action";
import { LanguageService } from "../shared/language";
import { initials } from "../shared/organization";

/** One organisation, from `GET /api/platform/organizations`. */
export interface PlatformOrganization {
  slug: string;
  name: string;
  ownerEmail: string | null;
  createdAt: string | null;
  lastActivityAt: string | null;
  members: number;
  status: "ACTIVE" | "SUSPENDED";
  featured: boolean;
}

const API = "/api/platform/organizations";

/** The organisations whose name, address or owner's email contain the search, ignoring case. */
export const matching = (
  organizations: PlatformOrganization[],
  search: string
): PlatformOrganization[] => {
  const wanted = search.trim().toLowerCase();

  return wanted === ""
    ? organizations
    : organizations.filter((organization) =>
        [organization.name, organization.slug, organization.ownerEmail ?? ""]
          .join(" ")
          .toLowerCase()
          .includes(wanted)
      );
};

/**
 * The platform admin's Organisations screen: every organisation with its owner, activity and
 * members, the one on the home page, and suspending or reactivating one. Nothing is deleted.
 */
@Component({
  imports: [RouterLink, TranslocoDirective],
  selector: "app-platform",
  styleUrl: "./platform.css",
  templateUrl: "./platform.html",
})
export class PlatformPage {
  protected readonly language = inject(LanguageService);
  protected readonly initials = initials;

  protected readonly list = httpResource<PlatformOrganization[]>(() => API);
  protected readonly search = signal("");

  protected readonly all = computed(() =>
    this.list.hasValue() ? this.list.value() : []
  );

  protected readonly shown = computed(() =>
    matching(this.all(), this.search())
  );

  protected readonly counts = computed(() => {
    const suspended = this.all().filter(
      (organization) => organization.status === "SUSPENDED"
    ).length;

    return {
      active: this.all().length - suspended,
      suspended,
      total: this.all().length,
    };
  });

  protected readonly featured = computed(() =>
    this.all().find((organization) => organization.featured)
  );

  protected readonly action = new AdminAction(() =>
    this.#transloco.translate("platform.failed")
  );

  readonly #http = inject(HttpClient);
  readonly #transloco = inject(TranslocoService);
  readonly #document = inject(DOCUMENT);

  /** Suspends after asking, or reactivates straight away. */
  protected async toggle(organization: PlatformOrganization): Promise<void> {
    const suspend = organization.status === "ACTIVE";

    if (
      suspend &&
      !this.#document.defaultView?.confirm(
        this.#transloco.translate("platform.suspendConfirm", {
          name: organization.name,
        })
      )
    ) {
      return;
    }

    await this.action.run(async () => {
      await firstValueFrom(
        this.#http.put(
          `${API}/${encodeURIComponent(organization.slug)}/status`,
          { status: suspend ? "SUSPENDED" : "ACTIVE" }
        )
      );
      this.list.reload();

      return this.#transloco.translate(
        suspend ? "platform.suspended" : "platform.reactivated",
        { name: organization.name }
      );
    });
  }
}
