import { DOCUMENT } from "@angular/common";
import { HttpClient, httpResource } from "@angular/common/http";
import { Component, computed, inject, signal } from "@angular/core";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import { firstValueFrom } from "rxjs";

import { CurrentSeason } from "../../results/current-season";
import { LanguageService } from "../../shared/language";
import type { MemberRole } from "../../shared/session";
import { SessionService } from "../../shared/session";
import { AdminAction } from "../action";
import type { NewInvite, Team, TeamMember } from "../api";
import { INVITE_MINUTES } from "../api";

/** An invite just made, with the message to paste: its code is never shown again. */
interface Created {
  invite: NewInvite;
  message: string;
}

/** A member's name: their Google name, else the start of their email. */
export const memberName = (member: TeamMember): string =>
  member.name?.trim() || member.email.split("@")[0] || member.email;

/** The page a code opens, on this site. */
export const joinLink = (origin: string, code: string): string =>
  `${origin}/join/${code}`;

/**
 * The Members screen: who belongs to the organisation and their role, the admin invites still
 * waiting, and ownership. Any admin invites admins, changes roles and removes members; only the
 * owner hands ownership on, and the owner can't be removed or made a player.
 */
@Component({
  imports: [TranslocoDirective],
  selector: "app-members-admin",
  styleUrl: "./members.css",
  templateUrl: "./members.html",
})
export class MembersAdminPage {
  protected readonly current = inject(CurrentSeason);
  protected readonly language = inject(LanguageService);
  protected readonly session = inject(SessionService);
  protected readonly expiries = INVITE_MINUTES;
  protected readonly memberName = memberName;

  protected readonly team = httpResource<Team>(() => {
    const api = this.current.adminApi();

    return api ? `${api}/members` : undefined;
  });

  /** The invite just made; its message can be copied until the page is left. */
  protected readonly created = signal<Created | null>(null);

  /** Whether the signed-in person owns the organisation. */
  protected readonly isOwner = computed(() => {
    const slug = this.current.organization()?.slug;

    return (
      this.session
        .me()
        ?.memberships.some(
          (membership) => membership.slug === slug && membership.owner
        ) ?? false
    );
  });

  /** The admins who could become the owner. */
  protected readonly otherAdmins = computed(() =>
    this.team.hasValue()
      ? this.team
          .value()
          .members.filter((member) => member.role === "ADMIN" && !member.owner)
      : []
  );

  protected readonly action = new AdminAction((status) =>
    this.#transloco.translate(
      {
        400: "adminMembers.invalid",
        403: "adminMembers.ownerOnly",
        409: "adminMembers.conflict",
      }[status] ?? "adminMembers.failed"
    )
  );

  readonly #http = inject(HttpClient);
  readonly #transloco = inject(TranslocoService);
  readonly #document = inject(DOCUMENT);
  readonly #api = this.current.adminApi;

  /** How long an invite lasts, in words: "30 minutes", "12 hours". */
  protected expiry(minutes: number): string {
    return minutes < 60
      ? this.#transloco.translate("adminMembers.minutes", { count: minutes })
      : this.#transloco.translate(
          this.language.plural("adminMembers.hours", minutes / 60),
          { count: minutes / 60 }
        );
  }

  /** Makes an admin invite and shows its message, once. */
  protected async invite(event: Event, form: HTMLFormElement): Promise<void> {
    event.preventDefault();
    const api = this.#api();
    const org = this.current.organization();
    const data = new FormData(form);
    const email = data.get("email")?.toString().trim() ?? "";
    const expiresInMinutes = Number(data.get("expiry")?.toString() ?? "1440");

    if (!api || !org) {
      return;
    }

    if (!email.includes("@")) {
      this.action.refuse(this.#transloco.translate("adminMembers.badEmail"));

      return;
    }

    const done = await this.action.run(async () => {
      const invite = await firstValueFrom(
        this.#http.post<NewInvite>(`${api}/invites`, {
          email,
          expiresInMinutes,
        })
      );

      this.created.set({
        invite,
        message: this.#transloco.translate("adminMembers.message", {
          code: invite.code,
          date: this.language.moment(invite.expiresAt),
          email: invite.email,
          inviter: this.session.name(),
          link: joinLink(this.#document.location.origin, invite.code),
          org: org.name,
        }),
      });
      this.team.reload();

      return this.#transloco.translate("adminMembers.invited", { email });
    });

    if (done) {
      form.reset();
    }
  }

  /** Puts the invite's message on the clipboard. */
  protected async copy(message: string): Promise<void> {
    await this.action.run(async () => {
      await this.#document.defaultView?.navigator.clipboard.writeText(message);

      return this.#transloco.translate("adminMembers.copied");
    });
  }

  protected async revoke(id: string, email: string): Promise<void> {
    await this.#change(
      () => this.#http.delete(`${this.#api()}/invites/${id}`),
      this.#transloco.translate("adminMembers.revoked", { email })
    );
  }

  protected async setRole(member: TeamMember, role: MemberRole): Promise<void> {
    await this.#change(
      () =>
        this.#http.put(`${this.#api()}/members/${member.userId}/role`, {
          role,
        }),
      this.#transloco.translate(
        role === "ADMIN" ? "adminMembers.madeAdmin" : "adminMembers.madePlayer",
        { name: memberName(member) }
      )
    );
  }

  protected async remove(member: TeamMember): Promise<void> {
    const name = memberName(member);

    if (
      !this.#document.defaultView?.confirm(
        this.#transloco.translate("adminMembers.removeConfirm", { name })
      )
    ) {
      return;
    }

    await this.#change(
      () => this.#http.delete(`${this.#api()}/members/${member.userId}`),
      this.#transloco.translate("adminMembers.removed", { name })
    );
  }

  /** Hands ownership to another admin, after asking; the owner stays an admin. */
  protected async transfer(userId: string): Promise<void> {
    const next = this.otherAdmins().find((member) => member.userId === userId);

    if (
      !next ||
      !this.#document.defaultView?.confirm(
        this.#transloco.translate("adminMembers.transferConfirm", {
          name: memberName(next),
        })
      )
    ) {
      return;
    }

    await this.#change(
      () => this.#http.post(`${this.#api()}/members/${userId}/ownership`, null),
      this.#transloco.translate("adminMembers.transferred", {
        name: memberName(next),
      })
    );
    await this.session.load();
  }

  async #change(
    request: () => ReturnType<HttpClient["delete"]>,
    done: string
  ): Promise<void> {
    if (!this.#api()) {
      return;
    }

    await this.action.run(async () => {
      await firstValueFrom(request());
      this.team.reload();

      return done;
    });
  }
}
