import { HttpClient, httpResource } from "@angular/common/http";
import { Component, computed, inject, input, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import type { Observable } from "rxjs";
import { firstValueFrom } from "rxjs";

import { CurrentSeason } from "../../results/current-season";
import { splitRiotId } from "../../results/stats";
import { LanguageService } from "../../shared/language";
import { AdminAction } from "../action";
import type { AdminPlayer, AttendanceStatus, Roster, RosterLine } from "../api";
import { playersMatching } from "../api";

/** The roster's lists, in the order they're shown. */
export const ROSTER_LISTS = [
  "confirmed",
  "waiting",
  "declined",
  "noAnswer",
] as const;

export type RosterList = (typeof ROSTER_LISTS)[number];

/** Where a player can be moved from each list. */
export const MOVES: Readonly<Record<RosterList, readonly AttendanceStatus[]>> =
  {
    confirmed: ["WAITING", "DECLINED"],
    declined: ["CONFIRMED", "WAITING"],
    noAnswer: ["CONFIRMED", "WAITING", "DECLINED"],
    waiting: ["CONFIRMED", "DECLINED"],
  };

/** The players not on the roster yet whose Riot ID or an alias has the search, ignoring case. */
export const notOnRoster = (
  players: readonly AdminPlayer[],
  roster: Roster,
  search: string
): AdminPlayer[] =>
  playersMatching(
    players,
    new Set(
      ROSTER_LISTS.flatMap((list) => roster[list].map((line) => line.playerId))
    ),
    search
  );

/**
 * The admin Match roster screen: who's in, the waiting list in order, who can't make it and who
 * hasn't answered, with how each answer came in. Admins move anyone, write down players without an
 * account, change the places and lock the roster.
 */
@Component({
  imports: [RouterLink, TranslocoDirective],
  selector: "app-roster-admin",
  styleUrl: "./roster.css",
  templateUrl: "./roster.html",
})
export class RosterAdminPage {
  /** From the address: /org/:slug/admin/matches/:match/roster. */
  readonly match = input.required<string>();

  protected readonly current = inject(CurrentSeason);
  protected readonly language = inject(LanguageService);
  protected readonly lists = ROSTER_LISTS;
  protected readonly moves = MOVES;
  protected readonly splitRiotId = splitRiotId;

  readonly #api = computed(() => {
    const admin = this.current.adminApi();

    return admin
      ? `${admin}/matches/${encodeURIComponent(this.match())}`
      : undefined;
  });

  protected readonly roster = httpResource<Roster>(() => {
    const api = this.#api();

    return api ? `${api}/roster` : undefined;
  });

  readonly #players = httpResource<AdminPlayer[]>(() => {
    const admin = this.current.adminApi();

    return admin ? `${admin}/players` : undefined;
  });

  protected readonly search = signal("");

  /** Whom the admin can write down, once they search. */
  protected readonly candidates = computed(() =>
    this.search().trim() === "" ||
    !this.#players.hasValue() ||
    !this.roster.hasValue()
      ? []
      : notOnRoster(
          this.#players.value(),
          this.roster.value(),
          this.search()
        ).slice(0, 8)
  );

  protected readonly action = new AdminAction((status) =>
    this.#transloco.translate(
      {
        400: "adminRoster.invalid",
        409: "adminRoster.conflict",
      }[status] ?? "adminRoster.failed"
    )
  );

  readonly #http = inject(HttpClient);
  readonly #transloco = inject(TranslocoService);

  /** How the answer came in: "In the app", "By Camille", "Moved up". */
  protected how(line: RosterLine): string {
    switch (line.source) {
      case "APP": {
        return this.#transloco.translate("adminRoster.inApp");
      }

      case "ADMIN": {
        return this.#transloco.translate("adminRoster.byAdmin", {
          name:
            line.answeredBy ??
            this.#transloco.translate("adminSignUps.anAdmin"),
        });
      }

      case "WAITING_LIST": {
        return this.#transloco.translate("adminRoster.movedUp");
      }

      default: {
        return "";
      }
    }
  }

  protected async move(
    line: { playerId: string; riotId: string | null },
    status: AttendanceStatus
  ): Promise<void> {
    await this.#change(
      (api) =>
        this.#http.put<Roster>(
          `${api}/roster/${encodeURIComponent(line.playerId)}`,
          { status }
        ),
      `adminRoster.moved.${status}`,
      line.riotId
    );
    this.search.set("");
  }

  protected async clear(line: RosterLine): Promise<void> {
    await this.#change(
      (api) =>
        this.#http.delete<Roster>(
          `${api}/roster/${encodeURIComponent(line.playerId)}`
        ),
      "adminRoster.cleared",
      line.riotId
    );
  }

  /** Saves the places through the match's plan, keeping the rest of it. */
  protected async savePlaces(roster: Roster, value: string): Promise<void> {
    const places = Number(value);
    const admin = this.current.adminApi();

    if (!Number.isInteger(places) || places < 2 || places > 40) {
      this.action.refuse(
        this.#transloco.translate("adminSchedule.problems.places")
      );

      return;
    }

    if (!admin) {
      return;
    }

    await this.action.run(async () => {
      await firstValueFrom(
        this.#http.put(
          `${admin}/seasons/${roster.seasonNumber}/schedule/${roster.number}`,
          {
            cancelled: null,
            date: roster.date,
            location: roster.location,
            places,
            replyBy: roster.replyBy,
            startTime: roster.startTime,
          }
        )
      );
      this.roster.reload();

      return this.#transloco.translate("adminRoster.placesSaved");
    });
  }

  protected async setLocked(locked: boolean): Promise<void> {
    await this.#change(
      (api) =>
        locked
          ? this.#http.post<Roster>(`${api}/lock`, null)
          : this.#http.delete<Roster>(`${api}/lock`),
      locked ? "adminRoster.lockedDone" : "adminRoster.unlockedDone",
      null
    );
  }

  async #change(
    request: (api: string) => Observable<Roster>,
    done: string,
    riotId: string | null
  ): Promise<void> {
    const api = this.#api();

    if (!api) {
      return;
    }

    await this.action.run(async () => {
      this.roster.set(await firstValueFrom(request(api)));

      return this.#transloco.translate(done, {
        name: splitRiotId(riotId ?? "").name,
      });
    });
  }
}
