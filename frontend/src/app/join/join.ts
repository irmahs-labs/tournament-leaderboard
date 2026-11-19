import {
  HttpClient,
  HttpErrorResponse,
  httpResource,
} from "@angular/common/http";
import { Component, computed, inject, input, signal } from "@angular/core";
import { Router, RouterLink } from "@angular/router";
import { TranslocoDirective } from "@jsverse/transloco";
import { firstValueFrom } from "rxjs";

import { LanguageService } from "../shared/language";
import { initials } from "../shared/organization";
import type { MemberRole, Membership } from "../shared/session";
import { SessionService } from "../shared/session";

/** Whether a code can still be used, and if not, why. */
export type InviteState = "VALID" | "EXPIRED" | "USED" | "REVOKED";

/** `GET /api/invites/{code}`: what a code is for. */
export interface JoinView {
  organization: { slug: string; name: string; tournamentName: string | null };
  role: MemberRole;
  /** For a personal invite, the Google account it's for. */
  email: string | null;
  invitedBy: string | null;
  expiresAt: string;
  state: InviteState;
}

/** What the page shows once the code is known. */
export type JoinStep =
  | "signIn"
  | "accept"
  | "wrongAccount"
  | "expired"
  | "used"
  | "revoked"
  | "accepted";

/** Why a code can't be used, as the page's step. */
const GONE: Record<Exclude<InviteState, "VALID">, JoinStep> = {
  EXPIRED: "expired",
  REVOKED: "revoked",
  USED: "used",
};

/** The step for a code, the signed-in person, and whether they've just joined. */
export const stepOf = (
  invite: JoinView,
  email: string | null,
  joined: boolean
): JoinStep => {
  if (joined) {
    return "accepted";
  }

  if (invite.state !== "VALID") {
    return GONE[invite.state];
  }

  if (email === null) {
    return "signIn";
  }

  return invite.email === null ||
    invite.email.toLowerCase() === email.toLowerCase()
    ? "accept"
    : "wrongAccount";
};

/** A code as typed, ready for the address: "k7qm 4fxp" becomes "K7QM-4FXP". */
export const tidyCode = (typed: string): string => {
  const code = typed.replaceAll(/[\s-]/gu, "").toUpperCase();

  return code.length === 8 ? `${code.slice(0, 4)}-${code.slice(4)}` : code;
};

/**
 * The page an invite link opens, or where a code is typed: what the code is for, signing in with
 * the right Google account, and joining.
 */
@Component({
  imports: [RouterLink, TranslocoDirective],
  selector: "app-join",
  styleUrl: "./join.css",
  templateUrl: "./join.html",
})
export class JoinPage {
  /** The code, from the address; none on /join, where it's typed. */
  readonly code = input<string>();

  protected readonly session = inject(SessionService);
  protected readonly language = inject(LanguageService);
  protected readonly initials = initials;

  protected readonly invite = httpResource<JoinView>(() => {
    const code = this.code();

    return code ? `/api/invites/${encodeURIComponent(code)}` : undefined;
  });

  /** Why the code couldn't be read: not found, or too many tries. */
  protected readonly lookupError = computed(() => {
    const error = this.invite.error();

    return error instanceof HttpErrorResponse && error.status === 429
      ? "tooMany"
      : "unknown";
  });

  protected readonly joined = signal(false);
  protected readonly busy = signal(false);
  protected readonly failed = signal(false);

  protected readonly step = computed(() =>
    this.invite.hasValue()
      ? stepOf(
          this.invite.value(),
          this.session.me()?.email ?? null,
          this.joined()
        )
      : null
  );

  readonly #http = inject(HttpClient);
  readonly #router = inject(Router);

  constructor() {
    void this.session.known();
  }

  /** Opens the page of a typed code. */
  protected open(event: Event, typed: string): void {
    event.preventDefault();
    const code = tidyCode(typed);

    if (code) {
      void this.#router.navigate(["/join", code]);
    }
  }

  protected signIn(): void {
    const code = this.code();

    this.session.signIn(code ? `/join/${code}` : "/join");
  }

  /** Signs out, then in again with another Google account. */
  protected async switchAccount(): Promise<void> {
    await this.session.signOut();
    this.signIn();
  }

  protected async accept(): Promise<void> {
    const code = this.code();

    if (!code || this.busy()) {
      return;
    }

    this.busy.set(true);
    this.failed.set(false);

    try {
      await firstValueFrom(
        this.#http.post<Membership>(
          `/api/invites/${encodeURIComponent(code)}/accept`,
          null
        )
      );
      this.joined.set(true);
      await this.session.load();
    } catch {
      this.failed.set(true);
      this.invite.reload();
    } finally {
      this.busy.set(false);
    }
  }
}
