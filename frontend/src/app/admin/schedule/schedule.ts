import { HttpClient, httpResource } from "@angular/common/http";
import { Component, computed, inject, signal } from "@angular/core";
import { Router, RouterLink } from "@angular/router";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import { firstValueFrom } from "rxjs";

import { CurrentSeason } from "../../results/current-season";
import { LanguageService } from "../../shared/language";
import { AdminAction } from "../action";
import type { PlanInput, Schedule, ScheduledMatch } from "../api";

/** The fields of a match's plan, as typed. */
export interface PlanFields {
  date: string;
  startTime: string;
  location: string;
  replyBy: string;
  places: string;
}

/** The longest place the API takes. */
export const MAX_LOCATION = 200;

/** What's wrong with a plan before it's sent, as a translation key; nothing when it's fine. */
export const planProblem = (fields: PlanFields): string | null => {
  const places = Number(fields.places);

  if (!Number.isInteger(places) || places < 2 || places > 40) {
    return "adminSchedule.problems.places";
  }

  if (fields.location.trim().length > MAX_LOCATION) {
    return "adminSchedule.problems.location";
  }

  if (fields.date && fields.replyBy && fields.replyBy > fields.date) {
    return "adminSchedule.problems.replyBy";
  }

  return null;
};

/** The request for a plan: empty fields are nothing. */
export const planInput = (
  fields: PlanFields,
  cancelled: boolean | null
): PlanInput => ({
  cancelled,
  date: fields.date || null,
  location: fields.location.trim() || null,
  places: Number(fields.places),
  replyBy: fields.replyBy || null,
  startTime: fields.startTime || null,
});

/**
 * The admin Schedule screen: every match of the season, planned or not, with when and where it's
 * played, its reply-by date and places, how full it is, and a link to its roster.
 */
@Component({
  imports: [RouterLink, TranslocoDirective],
  selector: "app-schedule-admin",
  styleUrl: "./schedule.css",
  templateUrl: "./schedule.html",
})
export class ScheduleAdminPage {
  protected readonly current = inject(CurrentSeason);
  protected readonly language = inject(LanguageService);
  protected readonly maxLocation = MAX_LOCATION;

  /** The match being planned or edited. */
  protected readonly editing = signal<number | null>(null);

  readonly #api = computed(() => {
    const admin = this.current.adminApi();
    const season = this.current.season();

    return admin && season
      ? `${admin}/seasons/${season.number}/schedule`
      : undefined;
  });

  protected readonly schedule = httpResource<Schedule>(() => this.#api());

  protected readonly action = new AdminAction((status) =>
    this.#transloco.translate(
      {
        400: "adminSchedule.invalid",
        409: "adminSchedule.conflict",
      }[status] ?? "adminSchedule.failed"
    )
  );

  readonly #http = inject(HttpClient);
  readonly #transloco = inject(TranslocoService);
  readonly #router = inject(Router);

  /** When it's played: "Sat, Dec 5 · 8:30 PM", or nothing. */
  protected when(match: ScheduledMatch): string {
    return [
      match.date ? this.language.day(match.date) : null,
      match.startTime ? this.language.time(match.startTime) : null,
    ]
      .flatMap((part) => (part === null ? [] : [part]))
      .join(" · ");
  }

  protected pickSeason(value: string): void {
    this.editing.set(null);
    void this.#router.navigate([], {
      queryParams: { season: value },
      queryParamsHandling: "merge",
    });
  }

  protected async save(
    match: ScheduledMatch,
    fields: PlanFields
  ): Promise<void> {
    const problem = planProblem(fields);

    if (problem) {
      this.action.refuse(this.#transloco.translate(problem));

      return;
    }

    if (
      await this.#plan(match, planInput(fields, null), "adminSchedule.saved")
    ) {
      this.editing.set(null);
    }
  }

  /** Calls the match off, or puts it back on, keeping its plan. */
  protected async setCancelled(
    match: ScheduledMatch,
    cancelled: boolean
  ): Promise<void> {
    await this.#plan(
      match,
      {
        cancelled,
        date: match.date,
        location: match.location,
        places: match.places,
        replyBy: match.replyBy,
        startTime: match.startTime,
      },
      cancelled ? "adminSchedule.calledOff" : "adminSchedule.backOn"
    );
  }

  #plan(
    match: ScheduledMatch,
    input: PlanInput,
    done: string
  ): Promise<boolean> {
    const api = this.#api();

    if (!api) {
      return Promise.resolve(false);
    }

    return this.action.run(async () => {
      this.schedule.set(
        await firstValueFrom(
          this.#http.put<Schedule>(`${api}/${match.number}`, input)
        )
      );

      return this.#transloco.translate(done, { number: match.number });
    });
  }
}
