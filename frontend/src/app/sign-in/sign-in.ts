import { Component, effect, inject, input, signal } from "@angular/core";
import { Router, RouterLink } from "@angular/router";
import { TranslocoDirective } from "@jsverse/transloco";

import { initials } from "../shared/organization";
import { SessionService, takeReturn } from "../shared/session";

/**
 * Google sign-in. Signed out, it offers "Continue with Google"; signed in, it lists the person's
 * organisations. Coming back from Google, it returns to the page sign-in started from.
 */
@Component({
  imports: [RouterLink, TranslocoDirective],
  selector: "app-sign-in",
  styleUrl: "./sign-in.css",
  templateUrl: "./sign-in.html",
})
export class SignInPage {
  /** The page to return to, from the header's Sign in link. */
  readonly from = input<string>();

  /** Present when Google or the server refused the sign-in. */
  readonly error = input<string>();

  protected readonly session = inject(SessionService);
  protected readonly initials = initials;

  protected readonly signingOut = signal(false);

  readonly #router = inject(Router);

  constructor() {
    effect(() => {
      if (this.session.me()) {
        const back = takeReturn();

        if (back) {
          void this.#router.navigateByUrl(back, { replaceUrl: true });
        }
      }
    });
  }

  protected continueWithGoogle(): void {
    this.session.signIn(this.from() ?? null);
  }

  protected async signOut(): Promise<void> {
    this.signingOut.set(true);

    try {
      await this.session.signOut();
    } finally {
      this.signingOut.set(false);
    }
  }
}
