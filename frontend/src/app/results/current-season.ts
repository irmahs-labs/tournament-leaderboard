import { httpResource } from "@angular/common/http";
import { computed, Injectable, signal } from "@angular/core";

import type { Organization } from "../shared/organization";
import type { Season } from "./api";

/**
 * The organisation on screen, its seasons, and the one picked in the header (`?season=` in the
 * address; the most recent one otherwise).
 */
@Injectable({ providedIn: "root" })
export class CurrentSeason {
  readonly #organization = signal<Organization | null>(null);
  readonly #requested = signal<number | null>(null);
  /** Only the slug, so that showing the same organisation again doesn't ask for its seasons. */
  readonly #slug = computed(() => this.#organization()?.slug);
  readonly #list = httpResource<Season[]>(() => {
    const slug = this.#slug();

    return slug ? `/api/orgs/${encodeURIComponent(slug)}/seasons` : undefined;
  });

  readonly organization = this.#organization.asReadonly();

  /** The address of the organisation's admin API, such as `/api/orgs/alsace-arena/admin`. */
  readonly adminApi = computed(() => {
    const slug = this.#slug();

    return slug ? `/api/orgs/${encodeURIComponent(slug)}/admin` : undefined;
  });

  /** Newest first, as the header's picker lists them; the API lists them oldest first. */
  readonly seasons = computed<Season[]>(() => {
    const newestFirst: Season[] = [];

    for (const season of this.#list.hasValue() ? this.#list.value() : []) {
      newestFirst.unshift(season);
    }

    return newestFirst;
  });

  /** Whether the list has arrived (or failed), so "no season yet" can be told apart from loading. */
  readonly settled = computed(
    () => this.#list.status() === "resolved" || this.#list.status() === "error"
  );

  readonly failed = computed(() => this.#list.status() === "error");

  readonly season = computed<Season | null>(
    () =>
      this.seasons().find((season) => season.number === this.#requested()) ??
      this.seasons()[0] ??
      null
  );

  /** The address of the picked season's API, such as `/api/orgs/alsace-arena/seasons/3`. */
  readonly api = computed(() => {
    const org = this.#organization();
    const season = this.season();

    return org && season
      ? `/api/orgs/${encodeURIComponent(org.slug)}/seasons/${season.number}`
      : undefined;
  });

  /** Asks for the seasons again, after one is started or edited. */
  reload(): void {
    this.#list.reload();
  }

  /** Called by the organisation's shell whenever the organisation or `?season=` changes. */
  use(organization: Organization, requested: number | null): void {
    this.show(organization);
    this.pick(requested);
  }

  /**
   * Shows the organisation an address names, or the same one again after its admins changed how
   * it looks (its seasons' address doesn't change, so they aren't asked for again).
   */
  show(organization: Organization): void {
    this.#organization.set(organization);
  }

  /** The season asked for in the address, or nothing for the most recent one. */
  pick(requested: number | null): void {
    this.#requested.set(requested);
  }
}
