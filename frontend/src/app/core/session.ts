import { DOCUMENT } from "@angular/common";
import type { HttpErrorResponse } from "@angular/common/http";
import { HttpClient } from "@angular/common/http";
import { Injectable, inject, signal } from "@angular/core";
import { catchError, firstValueFrom, map, of } from "rxjs";

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
  avatarUrl: string | null;
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

/** Who is signed in. Signing in goes through Google and comes back to `/sign-in`. */
@Injectable({ providedIn: "root" })
export class SessionService {
  readonly #http = inject(HttpClient);
  readonly #document = inject(DOCUMENT);
  readonly #me = signal<Me | null | undefined>(undefined);

  /** The signed-in person; nothing when signed out; undefined until known. */
  readonly me = this.#me.asReadonly();

  /** Asks the server who is signed in. */
  async load(): Promise<Me | null> {
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
    try {
      if (isSafeReturn(returnTo)) {
        this.#document.defaultView?.sessionStorage.setItem(
          RETURN_KEY,
          returnTo
        );
      }
    } catch {
      // Without storage, the person lands on the sign-in page's list of organisations.
    }

    this.#document.defaultView?.location.assign(GOOGLE_SIGN_IN);
  }

  async signOut(): Promise<void> {
    await firstValueFrom(this.#http.post("/api/logout", null));
    this.#me.set(null);
  }

  /** The page to go back to after signing in, once: reading it forgets it. */
  takeReturn(): string | null {
    try {
      const storage = this.#document.defaultView?.sessionStorage;
      const path = storage?.getItem(RETURN_KEY);

      storage?.removeItem(RETURN_KEY);

      return isSafeReturn(path) ? path : null;
    } catch {
      return null;
    }
  }
}

/** The name to show for someone: their Google name, else the start of their email. */
export const displayName = (me: Me): string =>
  me.displayName?.trim() || me.email.split("@")[0] || me.email;
