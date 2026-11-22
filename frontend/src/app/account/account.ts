import { DOCUMENT } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { Component, inject, signal } from "@angular/core";
import { Router, RouterLink } from "@angular/router";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import { firstValueFrom } from "rxjs";

import { AdminAction } from "../admin/action";
import { initials } from "../shared/organization";
import type { Membership } from "../shared/session";
import { SessionService } from "../shared/session";

/** What has to be typed before an account can be deleted. */
export const DELETE_WORD = "DELETE";

/**
 * My account: who is signed in, their Riot ID and Discord (the same at every organisation), the
 * organisations they belong to (leaving any they don't own),
 * signing out, and deleting the account once they own no organisation.
 */
@Component({
  imports: [RouterLink, TranslocoDirective],
  selector: "app-account",
  styleUrl: "./account.css",
  templateUrl: "./account.html",
})
export class AccountPage {
  protected readonly session = inject(SessionService);
  protected readonly initials = initials;
  protected readonly deleteWord = DELETE_WORD;

  /** What's typed in the confirmation box. */
  protected readonly typed = signal("");

  protected readonly action = new AdminAction((status) =>
    this.#transloco.translate(
      status === 409 ? "account.ownerConflict" : "account.failed"
    )
  );

  /** Saving the Riot ID and Discord, with its own message under the form. */
  protected readonly identity = new AdminAction((status) =>
    this.#transloco.translate(
      {
        400: "account.identityInvalid",
        409: "account.identityTaken",
      }[status] ?? "account.failed"
    )
  );

  readonly #http = inject(HttpClient);
  readonly #router = inject(Router);
  readonly #transloco = inject(TranslocoService);
  readonly #document = inject(DOCUMENT);

  /** Whether they own an organisation, suspended ones too, which keeps the account from being deleted. */
  protected owns(): boolean {
    const me = this.session.me();

    return (
      me !== null &&
      me !== undefined &&
      [...me.memberships, ...me.suspended].some(
        (membership) => membership.owner
      )
    );
  }

  /** Changes the Riot ID and Discord on the account and on every linked profile. */
  protected async saveIdentity(
    event: Event,
    riotId: string,
    discord: string
  ): Promise<void> {
    event.preventDefault();
    await this.identity.run(async () => {
      await firstValueFrom(
        this.#http.put("/api/me/identity", {
          discordUsername: discord.trim() || null,
          riotId: riotId.trim(),
        })
      );
      await this.session.load();

      return this.#transloco.translate("account.identitySaved");
    });
  }

  /** Leaves an organisation after asking; its player record stays there, unlinked. */
  protected async leave(membership: Membership): Promise<void> {
    if (
      !this.#document.defaultView?.confirm(
        this.#transloco.translate("account.leaveConfirm", {
          name: membership.name,
        })
      )
    ) {
      return;
    }

    await this.action.run(async () => {
      await firstValueFrom(
        this.#http.delete(
          `/api/orgs/${encodeURIComponent(membership.slug)}/membership`
        )
      );
      await this.session.load();

      return this.#transloco.translate("account.left", {
        name: membership.name,
      });
    });
  }

  protected async signOut(): Promise<void> {
    await this.action.run(async () => {
      await this.session.signOut();
      await this.#router.navigateByUrl("/");

      return "";
    });
  }

  /** Deletes the account once DELETE is typed; the server signs them out. */
  protected async deleteAccount(event: Event): Promise<void> {
    event.preventDefault();

    if (this.typed().trim() !== DELETE_WORD) {
      return;
    }

    const done = await this.action.run(async () => {
      await firstValueFrom(this.#http.delete("/api/me"));

      return this.#transloco.translate("account.deleted");
    });

    if (done) {
      await this.session.load().catch(() => null);
      await this.#router.navigateByUrl("/");
    }
  }
}
