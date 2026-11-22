import { Component, inject, output } from "@angular/core";
import type { OnInit } from "@angular/core";
import { RouterLink } from "@angular/router";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";

import { LanguageService } from "../shared/language";
import type { Notice } from "../shared/notices";
import { NoticesService } from "../shared/notices";

/** The text's key: a change has one per change, an answered claim one per answer. */
export const keyOf = (notice: Notice): string => {
  if (notice.kind === "ADMIN_CHANGE") {
    return `notices.changes.${notice.details.change ?? ""}`;
  }

  if (notice.kind !== "CLAIM_ANSWERED") {
    return `notices.kinds.${notice.kind}`;
  }

  if (notice.details.accepted) {
    return "notices.kinds.CLAIM_ACCEPTED";
  }

  return notice.details.reason
    ? "notices.kinds.CLAIM_DECLINED_BECAUSE"
    : "notices.kinds.CLAIM_DECLINED";
};

/** Where a notice takes its reader: the page it's about, in its organisation. */
export const linkOf = (notice: Notice): string[] | null => {
  const org = ["/org", notice.organizationSlug];

  switch (notice.kind) {
    case "MATCH_PLANNED":
    case "MATCH_MOVED":
    case "MATCH_CANCELLED":
    case "REMINDER":
    case "MOVED_UP": {
      return [...org, "me", "seasons"];
    }

    case "CLAIM_ANSWERED": {
      return [...org, "claim"];
    }

    case "CLAIM_RECEIVED": {
      return [...org, "admin", "claims"];
    }

    case "ADMIN_CHANGE": {
      return [...org, "admin"];
    }

    default: {
      return null;
    }
  }
};

/**
 * The signed-in person's notices from every organisation they belong to, newest first, in a panel
 * the header's bell opens over the page. Opening it marks them read; following a link closes it.
 */
@Component({
  imports: [RouterLink, TranslocoDirective],
  selector: "app-notices-panel",
  styleUrl: "./notices.css",
  templateUrl: "./notices.html",
})
export class NoticesPanel implements OnInit {
  /** Emitted when a link is followed, so the header closes the panel. */
  readonly closed = output();

  protected readonly notices = inject(NoticesService);
  protected readonly language = inject(LanguageService);
  protected readonly linkOf = linkOf;

  readonly #transloco = inject(TranslocoService);

  ngOnInit(): void {
    void this.#loadAndMarkRead();
  }

  async #loadAndMarkRead(): Promise<void> {
    const loaded = await this.notices.load();

    if (loaded && loaded.unread > 0) {
      await this.notices.markRead();
    }
  }

  /** What the notice says, in the language on screen. */
  protected text(notice: Notice): string {
    const { details } = notice;

    const params = {
      by: details.by ?? this.#transloco.translate("notices.someone"),
      game: String(details.game ?? ""),
      match: String(details.match ?? ""),
      member: details.member ?? details.email ?? "",
      reason: details.reason ?? "",
      riotId: details.riotId ?? "",
      season: details.season ?? "",
      when:
        this.language.when({
          date: details.date ?? null,
          location: details.location ?? null,
          startTime: details.startTime ?? null,
        }) || this.#transloco.translate("notices.noDate"),
    } satisfies Record<string, string>;

    return this.#transloco.translate(keyOf(notice), params);
  }
}
