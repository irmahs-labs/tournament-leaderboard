import { HttpClient } from "@angular/common/http";
import { Injectable, inject } from "@angular/core";
import type { ResolveFn } from "@angular/router";
import { RedirectCommand, Router } from "@angular/router";
import type { Observable } from "rxjs";
import { catchError, of } from "rxjs";

/** The sites an organisation can link to, in the order the footer shows them. */
export const SOCIAL_SITES = [
  "discord",
  "instagram",
  "twitch",
  "youtube",
  "tiktok",
  "website",
] as const;

export type SocialSite = (typeof SOCIAL_SITES)[number];

/** Each one an https address, or nothing to hide it. */
export type SocialLinks = Record<SocialSite, string | null>;

/** What every page of an organisation needs first, from `GET /api/orgs/{slug}`. */
export interface Organization {
  slug: string;
  name: string;
  tournamentName: string | null;
  description: string | null;
  defaultLocale: string;
  /** #RRGGBB, or nothing for the template's red. */
  accentColor: string | null;
  playersPerMatch: number;
  socialLinks: SocialLinks;
  /** The logo's address, which changes with it; nothing shows the initials. */
  logo: string | null;
}

@Injectable({ providedIn: "root" })
export class OrganizationService {
  readonly #http = inject(HttpClient);

  load(slug: string): Observable<Organization> {
    return this.#http.get<Organization>(
      `/api/orgs/${encodeURIComponent(slug)}`
    );
  }
}

/** Loads the organisation an address names; an unknown or suspended one shows Not found. */
export const organizationResolver: ResolveFn<Organization> = (route) => {
  const router = inject(Router);

  return inject(OrganizationService)
    .load(route.paramMap.get("slug") ?? "")
    .pipe(
      catchError(() =>
        of(
          new RedirectCommand(router.parseUrl("/not-found"), {
            skipLocationChange: true,
          })
        )
      )
    );
};

/** The two letters of the logo square: the first letters of the first two words. */
export const initials = (name: string): string =>
  name
    .split(/\s+/u)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0]?.toUpperCase() ?? "")
    .join("");

/** The organisation with its tournament, such as "Alsace Arena · Spike Tournament". */
export const fullName = (org: Organization): string =>
  org.tournamentName ? `${org.name} · ${org.tournamentName}` : org.name;
