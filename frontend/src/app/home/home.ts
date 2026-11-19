import { DOCUMENT } from "@angular/common";
import {
  HttpClient,
  HttpErrorResponse,
  httpResource,
} from "@angular/common/http";
import type { AfterViewInit } from "@angular/core";
import { Component, computed, inject, signal } from "@angular/core";
import { Router, RouterLink } from "@angular/router";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import { firstValueFrom } from "rxjs";

import { accentOrDefault, onAccent } from "../shared/colour";
import type { Organization } from "../shared/organization";
import { fullName, initials } from "../shared/organization";
import { SessionService } from "../shared/session";
import { readStored, store } from "../shared/storage";

/** What `POST /api/orgs` answers. */
export interface Founded {
  slug: string;
  name: string;
}

/** The form typed before leaving for Google, kept for the way back. */
const DRAFT_KEY = "foundingDraft";

/** Addresses the API keeps for the platform. */
const RESERVED = new Set([
  "admin",
  "api",
  "app",
  "help",
  "join",
  "new",
  "platform",
  "settings",
  "sign-in",
  "support",
]);

export const NAME_LIMIT = 60;
export const SLUG_LIMIT = 40;

/** An address made from a name: "Lyon Game Bar" gives "lyon-game-bar", accents dropped. */
export const slugFrom = (name: string): string =>
  name
    .normalize("NFD")
    .replaceAll(/[̀-ͯ]/gu, "")
    .toLowerCase()
    .replaceAll(/[^a-z0-9]+/gu, "-")
    .replaceAll(/^-+|-+$/gu, "")
    .slice(0, SLUG_LIMIT)
    .replace(/-+$/u, "");

export type FoundingProblem = "name" | "slug" | "reserved";

/** The first thing wrong with the form, checked as the API checks it. */
export const foundingProblem = (
  name: string,
  slug: string
): FoundingProblem | null => {
  if (name.trim() === "" || name.trim().length > NAME_LIMIT) {
    return "name";
  }

  if (!/^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$/u.test(slug) || slug.includes("--")) {
    return "slug";
  }

  return RESERVED.has(slug) ? "reserved" : null;
};

/** An organisation's initials square, in its own colour with text that reads on it. */
export const badge = (org: Organization) => {
  const accent = accentOrDefault(org.accentColor);

  return { background: accent, color: onAccent(accent) };
};

/**
 * The platform's home page: what it does, a form to create an organisation (signing in with
 * Google first when needed), and the most recently active organisation as a live example.
 */
@Component({
  imports: [RouterLink, TranslocoDirective],
  selector: "app-home",
  styleUrl: "./home.css",
  templateUrl: "./home.html",
})
export class HomePage implements AfterViewInit {
  protected readonly session = inject(SessionService);
  protected readonly fullName = fullName;
  protected readonly initials = initials;
  protected readonly nameLimit = NAME_LIMIT;
  protected readonly slugLimit = SLUG_LIMIT;

  /** Nothing (204) before any organisation has played a game. */
  protected readonly featured = httpResource<Organization | null>(
    () => "/api/featured"
  );

  protected readonly name = signal("");
  protected readonly slug = signal("");
  /** Whether the address was typed by hand; until then it follows the name. */
  protected readonly slugTyped = signal(false);
  protected readonly busy = signal(false);
  protected readonly problem = signal<string | null>(null);

  protected readonly host = computed(() => this.#document.location.host);

  readonly #http = inject(HttpClient);
  readonly #router = inject(Router);
  readonly #transloco = inject(TranslocoService);
  readonly #document = inject(DOCUMENT);

  constructor() {
    const draft = readStored(DRAFT_KEY, "sessionStorage");

    store(DRAFT_KEY, null, "sessionStorage");

    if (draft) {
      const [name = "", slug = ""] = draft.split("\n");

      this.name.set(name);
      this.slug.set(slug);
      this.slugTyped.set(true);
    }
  }

  /** Back from Google with a draft, or opened at #create: shows the form. */
  ngAfterViewInit(): void {
    if (this.name() !== "" || this.#document.location.hash === "#create") {
      this.#document.querySelector("#create")?.scrollIntoView();
    }
  }

  protected readonly badge = badge;

  protected typeName(value: string): void {
    this.name.set(value);

    if (!this.slugTyped()) {
      this.slug.set(slugFrom(value));
    }
  }

  protected typeSlug(value: string): void {
    this.slugTyped.set(true);
    this.slug.set(value.trim().toLowerCase());
  }

  /** Creates the organisation, or signs in with Google first and comes back to the form. */
  protected async found(event: Event): Promise<void> {
    event.preventDefault();
    const name = this.name().trim();
    const slug = this.slug();
    const problem = foundingProblem(name, slug);

    if (problem) {
      this.problem.set(this.#transloco.translate(`home.problem.${problem}`));

      return;
    }

    // Asked first, so a click while the session is still loading doesn't send someone to Google.
    if (!(await this.session.known())) {
      store(DRAFT_KEY, `${name}\n${slug}`, "sessionStorage");
      this.session.signIn("/#create");

      return;
    }

    if (this.busy()) {
      return;
    }

    this.busy.set(true);
    this.problem.set(null);

    try {
      const made = await firstValueFrom(
        this.#http.post<Founded>("/api/orgs", { name, slug })
      );

      await this.session.load();
      await this.#router.navigate(["/o", made.slug, "admin", "seasons"]);
    } catch (error) {
      const status = error instanceof HttpErrorResponse ? error.status : 0;

      this.problem.set(
        this.#transloco.translate(
          {
            400: "home.problem.slug",
            403: "home.problem.limit",
            409: "home.problem.taken",
          }[status] ?? "home.problem.failed"
        )
      );
    } finally {
      this.busy.set(false);
    }
  }
}
