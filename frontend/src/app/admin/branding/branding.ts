import { DOCUMENT } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { Component, computed, inject, signal } from "@angular/core";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import { firstValueFrom } from "rxjs";

import { CurrentSeason } from "../../results/current-season";
import {
  accentOrDefault,
  contrast,
  DEFAULT_ACCENT,
  onAccent,
  PALETTES,
  readableAccent,
} from "../../shared/colour";
import { LanguageService } from "../../shared/language";
import type { Organization, SocialSite } from "../../shared/organization";
import { initials, SOCIAL_SITES } from "../../shared/organization";
import { ThemeService } from "../../shared/theme";
import { AdminAction } from "../action";

/** The limits the API checks, so the form says what's wrong before sending. */
export const BRANDING_LIMITS = {
  maxDescription: 200,
  maxLink: 200,
  maxLogoBytes: 256 * 1024,
  maxName: 60,
  maxPlayers: 40,
  minPlayers: 2,
} as const;

/** The sites each link must be on; the website can be anywhere. */
const SITE_HOSTS: Record<SocialSite, string[]> = {
  discord: ["discord.gg", "discord.com"],
  instagram: ["instagram.com"],
  tiktok: ["tiktok.com"],
  twitch: ["twitch.tv"],
  website: [],
  youtube: ["youtube.com", "youtu.be"],
};

/** A few accents to start from: the template's red, then colours that read in both themes. */
export const SWATCHES = [
  DEFAULT_ACCENT,
  "#2F5BFF",
  "#12A36B",
  "#F5A623",
  "#9B5CF6",
  "#E5488F",
] as const;

const HEX = /^#[0-9a-f]{6}$/iu;

/** The branding as typed in the form. */
export interface BrandingFields {
  name: string;
  tournamentName: string;
  description: string;
  defaultLocale: string;
  accentColor: string;
  playersPerMatch: string;
  socialLinks: Record<SocialSite, string>;
}

export type BrandingProblem =
  | "name"
  | "accent"
  | "players"
  | `link.${SocialSite}`;

/** Whether an address is an https one on the site (or anywhere, for the website). */
export const linkAllowed = (site: SocialSite, address: string): boolean => {
  const trimmed = address.trim();

  if (trimmed === "") {
    return true;
  }

  if (trimmed.length > BRANDING_LIMITS.maxLink || !URL.canParse(trimmed)) {
    return false;
  }

  const url = new URL(trimmed);
  const host = url.hostname.toLowerCase();
  const hosts = SITE_HOSTS[site];

  return (
    url.protocol === "https:" &&
    host !== "" &&
    url.username === "" &&
    url.password === "" &&
    (hosts.length === 0 ||
      hosts.some((allowed) => host === allowed || host.endsWith(`.${allowed}`)))
  );
};

/** The first thing wrong with the form, checked as the API checks it; nothing when it can go. */
export const problemOf = (fields: BrandingFields): BrandingProblem | null => {
  const name = fields.name.trim();
  const accent = fields.accentColor.trim();
  const players = Number(fields.playersPerMatch.trim());

  if (name === "" || name.length > BRANDING_LIMITS.maxName) {
    return "name";
  }

  if (accent !== "" && !HEX.test(accent)) {
    return "accent";
  }

  if (
    !Number.isInteger(players) ||
    players < BRANDING_LIMITS.minPlayers ||
    players > BRANDING_LIMITS.maxPlayers
  ) {
    return "players";
  }

  const wrong = SOCIAL_SITES.find(
    (site) => !linkAllowed(site, fields.socialLinks[site])
  );

  return wrong ? `link.${wrong}` : null;
};

const orNull = (text: string): string | null => text.trim() || null;

/** One value per site. */
const perSite = <T>(value: (site: SocialSite) => T): Record<SocialSite, T> => ({
  discord: value("discord"),
  instagram: value("instagram"),
  tiktok: value("tiktok"),
  twitch: value("twitch"),
  website: value("website"),
  youtube: value("youtube"),
});

/** What `PUT …/admin/branding` takes, from a form that passed `problemOf`. */
export const brandingInput = (
  fields: BrandingFields
): Omit<Organization, "slug" | "logo"> => ({
  accentColor: orNull(fields.accentColor)?.toUpperCase() ?? null,
  defaultLocale: fields.defaultLocale,
  description: orNull(fields.description),
  name: fields.name.trim(),
  playersPerMatch: Number(fields.playersPerMatch.trim()),
  socialLinks: perSite((site) => orNull(fields.socialLinks[site])),
  tournamentName: orNull(fields.tournamentName),
});

/** The image types the API accepts, as the file picker offers them. */
export const LOGO_TYPES = "image/png,image/webp,image/svg+xml";

/**
 * The Branding screen: the organisation's names, language, logo, accent colour, places per match
 * and social links. Saving updates every page at once, without a reload.
 */
@Component({
  imports: [TranslocoDirective],
  selector: "app-branding-admin",
  styleUrl: "./branding.css",
  templateUrl: "./branding.html",
})
export class BrandingAdminPage {
  protected readonly current = inject(CurrentSeason);
  protected readonly language = inject(LanguageService);
  protected readonly limits = BRANDING_LIMITS;
  protected readonly sites = SOCIAL_SITES;
  protected readonly swatches = SWATCHES;
  protected readonly logoTypes = LOGO_TYPES;
  protected readonly initials = initials;

  /** The accent as typed, for the preview and the contrast lines. */
  protected readonly accent = signal(
    this.current.organization()?.accentColor ?? ""
  );

  protected readonly locale = signal(
    this.current.organization()?.defaultLocale ?? "en"
  );

  /** The accent the preview uses: the one typed when it's a whole code, else the red. */
  protected readonly shownAccent = computed(() =>
    accentOrDefault(this.accent().trim())
  );

  /** How the accent reads: button text on it, and as text on cards in the theme on screen. */
  protected readonly ratios = computed(() => {
    const accent = this.shownAccent();
    const { surface2, text } = PALETTES[this.#theme.theme()];

    return {
      button: contrast(accent, onAccent(accent)),
      raw: contrast(accent, surface2),
      text: contrast(readableAccent(accent, surface2, text), surface2),
    };
  });

  protected readonly onAccent = computed(() => onAccent(this.shownAccent()));

  protected readonly action = new AdminAction((status) =>
    this.#transloco.translate(
      status === 400 ? "adminBranding.invalid" : "adminBranding.failed"
    )
  );

  /** The logo's own changes, said next to it. */
  protected readonly logoAction = new AdminAction((status) =>
    this.#transloco.translate(
      {
        413: "adminBranding.logoTooLarge",
        415: "adminBranding.logoType",
      }[status] ?? "adminBranding.failed"
    )
  );

  readonly #http = inject(HttpClient);
  readonly #transloco = inject(TranslocoService);
  readonly #theme = inject(ThemeService);
  readonly #document = inject(DOCUMENT);
  readonly #api = this.current.adminApi;

  /** The address of the organisation's pages, as players see it. */
  protected address(org: Organization): string {
    return `${this.#document.location.host}/org/${org.slug}`;
  }

  protected ratio(value: number): string {
    return this.language.number(Math.floor(value * 10) / 10, 1);
  }

  protected pickAccent(hex: string, field: HTMLInputElement): void {
    field.value = hex;
    this.accent.set(hex);
  }

  /** Saves the form's branding; the header, footer and accent follow at once. */
  protected async save(event: Event, form: HTMLFormElement): Promise<void> {
    event.preventDefault();
    const api = this.#api();
    const fields = this.#fields(new FormData(form));
    const problem = problemOf(fields);

    if (!api) {
      return;
    }

    if (problem) {
      this.action.refuse(
        this.#transloco.translate(`adminBranding.problem.${problem}`)
      );

      return;
    }

    await this.action.run(async () => {
      this.current.show(
        await firstValueFrom(
          this.#http.put<Organization>(`${api}/branding`, brandingInput(fields))
        )
      );

      return this.#transloco.translate("adminBranding.saved");
    });
  }

  /** Sends the picked image as it is; the API tells PNG, WebP and SVG apart by their content. */
  protected async upload(field: HTMLInputElement): Promise<void> {
    const file = field.files?.[0];
    const api = this.#api();

    field.value = "";

    if (!file || !api) {
      return;
    }

    if (file.size > BRANDING_LIMITS.maxLogoBytes) {
      this.logoAction.refuse(
        this.#transloco.translate("adminBranding.logoTooLarge")
      );

      return;
    }

    await this.logoAction.run(async () => {
      this.current.show(
        await firstValueFrom(
          this.#http.put<Organization>(`${api}/logo`, file, {
            headers: {
              "Content-Type": file.type || "application/octet-stream",
            },
          })
        )
      );

      return this.#transloco.translate("adminBranding.logoSaved");
    });
  }

  protected async removeLogo(): Promise<void> {
    const api = this.#api();

    if (!api) {
      return;
    }

    await this.logoAction.run(async () => {
      this.current.show(
        await firstValueFrom(this.#http.delete<Organization>(`${api}/logo`))
      );

      return this.#transloco.translate("adminBranding.logoRemoved");
    });
  }

  #fields(data: FormData): BrandingFields {
    const text = (name: string): string => data.get(name)?.toString() ?? "";

    return {
      accentColor: text("accentColor"),
      defaultLocale: this.locale(),
      description: text("description"),
      name: text("name"),
      playersPerMatch: text("playersPerMatch"),
      socialLinks: perSite(text),
      tournamentName: text("tournamentName"),
    };
  }
}
