import { DOCUMENT } from "@angular/common";
import type { HttpErrorResponse } from "@angular/common/http";
import { HttpClient } from "@angular/common/http";
import { computed, Injectable, inject, signal } from "@angular/core";
import type { UrlTree } from "@angular/router";
import { Router } from "@angular/router";
import { catchError, firstValueFrom, map, of } from "rxjs";

import { readStored, store } from "./storage";

export type MemberRole = "ADMIN" | "PLAYER";

/** One organisation the signed-in person belongs to. */
export interface Membership {
  slug: string;
  name: string;
  role: MemberRole;
  owner: boolean;
}

/** The signed-in person, from `GET /api/me`. */
export interface Me {
  email: string;
  displayName: string | null;
  platformAdmin: boolean;
  memberships: Membership[];
}

const RETURN_KEY = "signInReturnTo";

/** Where sign-in starts: Spring sends the browser on to Google. */
export const GOOGLE_SIGN_IN = "/oauth2/authorization/google";

/**
 * An address inside this app that sign-in may go back to: a path, never another site or the
 * sign-in page itself.
 */
export const isSafeReturn = (path: string | null | undefined): path is string =>
  Boolean(path?.startsWith("/")) &&
  !path?.startsWith("//") &&
  !path?.startsWith("/\\") &&
  !path?.startsWith("/sign-in");

/** Whether someone may run an organisation: one of its admins, or a platform admin. */
export const canAdmin = (me: Me | null | undefined, slug: string): boolean =>
  Boolean(
    me &&
    (me.platformAdmin ||
      me.memberships.some(
        (membership) => membership.slug === slug && membership.role === "ADMIN"
      ))
  );

/** The name to show for someone: their Google name, else the start of their email. */
export const displayName = (me: Me): string =>
  me.displayName?.trim() || me.email.split("@")[0] || me.email;

/** Who is signed in. Signing in goes through Google and comes back to `/sign-in`. */
@Injectable({ providedIn: "root" })
export class SessionService {
  readonly #http = inject(HttpClient);
  readonly #document = inject(DOCUMENT);
  readonly #me = signal<Me | null | undefined>(undefined);

  /** The signed-in person; nothing when signed out; undefined until known. */
  readonly me = this.#me.asReadonly();

  /** The name to show for the signed-in person; empty when nobody is. */
  readonly name = computed(() => {
    const me = this.#me();

    return me ? displayName(me) : "";
  });

  #pending: Promise<Me | null> | null = null;

  /** Asks the server who is signed in; a question already on its way is shared. */
  load(): Promise<Me | null> {
    this.#pending ??= this.#fetch().finally(() => {
      this.#pending = null;
    });

    return this.#pending;
  }

  /** Who is signed in, asking the server only if that isn't known yet. */
  async known(): Promise<Me | null> {
    const me = this.#me();

    return me === undefined ? await this.load() : me;
  }

  async #fetch(): Promise<Me | null> {
    const me = await firstValueFrom(
      this.#http.get<Me>("/api/me").pipe(
        map((found): Me | null => found),
        catchError((error: HttpErrorResponse) => {
          if (error.status === 401) {
            return of(null);
          }

          throw error;
        })
      )
    );

    this.#me.set(me);

    return me;
  }

  /** Leaves for Google; back on `/sign-in`, the app returns to `returnTo`. */
  signIn(returnTo: string | null): void {
    // Without storage, the person lands on the sign-in page's list of organisations.
    if (isSafeReturn(returnTo)) {
      store(RETURN_KEY, returnTo, "sessionStorage");
    }

    this.#document.defaultView?.location.assign(GOOGLE_SIGN_IN);
  }

  async signOut(): Promise<void> {
    await firstValueFrom(this.#http.post("/api/logout", null));
    this.#me.set(null);
  }
}

/** The page to go back to after signing in, once: reading it forgets it. */
export const takeReturn = (): string | null => {
  const path = readStored(RETURN_KEY, "sessionStorage");
  store(RETURN_KEY, null, "sessionStorage");

  return isSafeReturn(path) ? path : null;
};

/**
 * For a guard: who is signed in, or the way to sign in that comes back to `url`. It injects, so a
 * guard calls it before its own first `await`.
 */
export const meOrSignIn = async (url: string): Promise<Me | UrlTree> => {
  const router = inject(Router);

  const me = await inject(SessionService)
    .known()
    .catch(() => null);

  return (
    me ?? router.createUrlTree(["/sign-in"], { queryParams: { from: url } })
  );
};
