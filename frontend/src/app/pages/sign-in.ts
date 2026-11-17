import {
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from "@angular/core";
import { Title } from "@angular/platform-browser";
import { Router, RouterLink } from "@angular/router";
import { TranslocoDirective } from "@jsverse/transloco";

import { initials } from "../core/organization";
import { displayName, SessionService } from "../core/session";
import { ThemeService } from "../core/theme";
import { SiteFooter } from "../layout/site-footer";
import { SiteHeader } from "../layout/site-header";

/**
 * Google sign-in. Signed out, it offers "Continue with Google"; signed in, it lists the person's
 * organisations. Coming back from Google, it returns to the page sign-in started from.
 */
@Component({
  imports: [RouterLink, SiteFooter, SiteHeader, TranslocoDirective],
  selector: "app-sign-in",
  styleUrls: ["../layout/page.css", "./sign-in.css"],
  templateUrl: "./sign-in.html",
})
export class SignInPage {
  /** The page to return to, from the header's Sign in link. */
  readonly from = input<string>();

  /** Present when Google or the server refused the sign-in. */
  readonly error = input<string>();

  protected readonly session = inject(SessionService);
  protected readonly initials = initials;
  protected readonly name = computed(() => {
    const me = this.session.me();

    return me ? displayName(me) : "";
  });

  protected readonly signingOut = signal(false);

  readonly #router = inject(Router);

  constructor() {
    inject(ThemeService).setAccent(null);
    inject(Title).setTitle("Tournament Leaderboard");

    effect(() => {
      if (this.session.me()) {
        const back = this.session.takeReturn();

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
