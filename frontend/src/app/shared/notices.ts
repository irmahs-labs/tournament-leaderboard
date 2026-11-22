import { HttpClient } from "@angular/common/http";
import { DestroyRef, effect, Injectable, inject, signal } from "@angular/core";
import { firstValueFrom } from "rxjs";

import { SessionService } from "./session";

export type NoticeKind =
  | "MATCH_PLANNED"
  | "MATCH_MOVED"
  | "MATCH_CANCELLED"
  | "REMINDER"
  | "MOVED_UP"
  | "CLAIM_ANSWERED"
  | "ADMIN_CHANGE"
  | "CLAIM_RECEIVED"
  | "ORGANIZATION_SUSPENDED"
  | "ORGANIZATION_REACTIVATED";

/**
 * What a notice is about; each kind fills in its own: a match's season, number, date and place;
 * who changed what; a claim's Riot ID and answer.
 */
export interface NoticeDetails {
  season?: string;
  match?: number;
  game?: number;
  date?: string | null;
  startTime?: string | null;
  location?: string | null;
  change?: string;
  by?: string | null;
  member?: string;
  email?: string;
  riotId?: string;
  accepted?: boolean;
  reason?: string | null;
}

/** One notice, as `GET /api/me/notifications` gives it. */
export interface Notice {
  id: number;
  kind: NoticeKind;
  organizationSlug: string;
  organizationName: string;
  details: NoticeDetails;
  at: string;
  read: boolean;
}

export interface Notices {
  unread: number;
  notices: Notice[];
}

/** How often the header's count is refreshed while someone is signed in. */
const EVERY = 60_000;

/** The signed-in person's notices: the header's count, and the Notices page. */
@Injectable({ providedIn: "root" })
export class NoticesService {
  readonly #http = inject(HttpClient);
  readonly #session = inject(SessionService);
  readonly #latest = signal<Notices | null>(null);

  /** How many notices aren't read yet; 0 while signed out. */
  readonly unread = signal(0);
  readonly latest = this.#latest.asReadonly();

  constructor() {
    effect(() => {
      if (this.#session.me()) {
        void this.load();
      } else {
        this.#latest.set(null);
        this.unread.set(0);
      }
    });

    const timer = setInterval(() => {
      if (this.#session.me()) {
        void this.load();
      }
    }, EVERY);

    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  /** Fetches the latest notices; a failure keeps what's shown. */
  async load(): Promise<Notices | null> {
    try {
      const notices = await firstValueFrom(
        this.#http.get<Notices>("/api/me/notifications")
      );

      this.#latest.set(notices);
      this.unread.set(notices.unread);

      return notices;
    } catch {
      return null;
    }
  }

  /** Marks every notice shown so far as read. */
  async markRead(): Promise<void> {
    await firstValueFrom(this.#http.post("/api/me/notifications/read", null));
    this.unread.set(0);
  }
}
