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
  /** How many games it has saved. */
  games: number;
  /** How many admins it has. With no game and one admin at most, it's empty. */
  admins: number;
}

const API = "/api/platform/organizations";

/** Whether deleting it takes nothing from anyone else: no game saved, one admin at most. */
export const isEmpty = (organization: PlatformOrganization): boolean =>
  organization.games === 0 && organization.admins <= 1;

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
 * members, the one on the home page, suspending or reactivating one, and deleting one nobody uses
 * yet once its name is typed.
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

  /**
   * Deletes an organisation once the platform admin types its name; one that isn't empty asks
   * again, saying what goes with it.
   */
  protected async remove(organization: PlatformOrganization): Promise<void> {
    const typed = this.#document.defaultView?.prompt(
      this.#transloco.translate("platform.deleteConfirm", {
        name: organization.name,
      })
    );

    if (typed === null || typed === undefined) {
      return;
    }

    if (typed.trim() !== organization.name) {
      this.action.refuse(
        this.#transloco.translate("platform.deleteMismatch", {
          name: organization.name,
        })
      );

      return;
    }

    const notEmpty = !isEmpty(organization);

    if (
      notEmpty &&
      !this.#document.defaultView?.confirm(
        this.#transloco.translate("platform.deleteNotEmpty", {
          admins: String(organization.admins),
          games: String(organization.games),
          name: organization.name,
        })
      )
    ) {
      return;
    }

    await this.action.run(async () => {
      await firstValueFrom(
        this.#http.delete(`${API}/${encodeURIComponent(organization.slug)}`, {
          body: { name: typed.trim(), notEmpty },
        })
      );
      this.list.reload();

      return this.#transloco.translate("platform.deleted", {
        name: organization.name,
      });
    });
  }

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
