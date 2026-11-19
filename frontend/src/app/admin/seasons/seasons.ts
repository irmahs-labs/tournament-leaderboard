import { HttpClient } from "@angular/common/http";
import { Component, computed, inject, signal } from "@angular/core";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import { firstValueFrom } from "rxjs";

import type { Season } from "../../results/api";
import { CurrentSeason } from "../../results/current-season";
import { LanguageService } from "../../shared/language";
import { AdminAction } from "../action";
import type { NewSeason, SeasonInput } from "../api";
import { SEASON_LIMITS } from "../api";

/** A season's details as typed in a form. */
export interface SeasonFields {
  name: string;
  startsOn: string;
  endsOn: string;
  matchCount: string;
  minGames: string;
  maxGames: string;
}

/** What's wrong with a season's details, before they're sent. */
export type SeasonProblem = "name" | "matches" | "games" | "dates";

const whole = (text: string): number => {
  const value = Number(text.trim());

  return text.trim() !== "" && Number.isInteger(value) ? value : Number.NaN;
};

/**
 * The first thing wrong with a season's details, checked as the API checks them; nothing when
 * they can be sent. A new season may leave its name empty: it's then "Season N".
 */
export const problemOf = (
  fields: SeasonFields,
  nameRequired: boolean
): SeasonProblem | null => {
  const name = fields.name.trim();
  const matches = whole(fields.matchCount);
  const fewest = whole(fields.minGames);
  const most = whole(fields.maxGames);

  if ((nameRequired && !name) || name.length > SEASON_LIMITS.maxName) {
    return "name";
  }

  if (!(matches >= 1 && matches <= SEASON_LIMITS.maxMatches)) {
    return "matches";
  }

  if (!(fewest >= 1 && most <= SEASON_LIMITS.maxGames && fewest <= most)) {
    return "games";
  }

  return fields.startsOn && fields.endsOn && fields.endsOn < fields.startsOn
    ? "dates"
    : null;
};

/** The details as the API takes them; check `problemOf` first. */
export const detailsOf = (fields: SeasonFields) => ({
  endsOn: fields.endsOn || null,
  matchCount: whole(fields.matchCount),
  maxGames: whole(fields.maxGames),
  minGames: whole(fields.minGames),
  name: fields.name.trim(),
  startsOn: fields.startsOn || null,
});

/**
 * The organisation's seasons: each one's dates, matches and games per match, edited in its row
 * (and closed or reopened there), and a form to start the next one with copied or default rules.
 */
@Component({
  imports: [TranslocoDirective],
  selector: "app-seasons-admin",
  styleUrl: "./seasons.css",
  templateUrl: "./seasons.html",
})
export class SeasonsAdminPage {
  protected readonly current = inject(CurrentSeason);
  protected readonly language = inject(LanguageService);
  protected readonly limits = SEASON_LIMITS;

  /** The season whose row is open for editing. */
  protected readonly editing = signal<number | null>(null);

  protected readonly action = new AdminAction((status) =>
    this.#transloco.translate(
      {
        400: "adminSeasons.invalid",
        409: "adminSeasons.tooSmall",
      }[status] ?? "adminSeasons.failed"
    )
  );

  /** The newest season, whose rules a new one copies unless told otherwise. */
  protected readonly newest = computed(() => this.current.seasons()[0] ?? null);

  readonly #http = inject(HttpClient);
  readonly #transloco = inject(TranslocoService);

  readonly #api = computed(() => {
    const admin = this.current.adminApi();

    return admin ? `${admin}/seasons` : undefined;
  });

  /** "Jan 2026 – Aug 2026", or a dash for dates not set. */
  protected dates(season: Season): string {
    const from = season.startsOn ? this.language.month(season.startsOn) : "";
    const to = season.endsOn ? this.language.month(season.endsOn) : "";

    return from || to ? `${from || "…"} – ${to || "…"}` : "–";
  }

  /** Starts the next season from the form; the form empties once it's done. */
  protected async start(event: Event, form: HTMLFormElement): Promise<void> {
    event.preventDefault();
    const data = new FormData(form);
    const field = (name: string): string => data.get(name)?.toString() ?? "";

    const fields: SeasonFields = {
      endsOn: field("endsOn"),
      matchCount: field("matchCount"),
      maxGames: field("maxGames"),
      minGames: field("minGames"),
      name: field("name"),
      startsOn: field("startsOn"),
    };

    const api = this.#api();

    if (!api || !this.#check(fields, false)) {
      return;
    }

    const copy = whole(field("copyRulesFrom"));
    const details = detailsOf(fields);

    const input: NewSeason = {
      ...details,
      copyRulesFrom: Number.isNaN(copy) ? null : copy,
      name: details.name || null,
    };

    const done = await this.action.run(async () => {
      const season = await firstValueFrom(this.#http.post<Season>(api, input));
      this.current.reload();

      return this.#transloco.translate("adminSeasons.started", {
        name: season.name,
      });
    });

    if (done) {
      form.reset();
    }
  }

  /** Saves an edited row, closing or reopening the season with it. */
  protected async save(
    season: Season,
    fields: SeasonFields,
    finished: boolean
  ): Promise<void> {
    const api = this.#api();

    if (!api || !this.#check(fields, true)) {
      return;
    }

    const input: SeasonInput = { ...detailsOf(fields), finished };

    const done = await this.action.run(async () => {
      const saved = await firstValueFrom(
        this.#http.put<Season>(`${api}/${season.number}`, input)
      );

      this.current.reload();

      return this.#transloco.translate("adminSeasons.saved", {
        name: saved.name,
      });
    });

    if (done) {
      this.editing.set(null);
    }
  }

  #check(fields: SeasonFields, nameRequired: boolean): boolean {
    const problem = problemOf(fields, nameRequired);

    if (problem) {
      this.action.refuse(
        this.#transloco.translate(`adminSeasons.problems.${problem}`, {
          ...this.limits,
        })
      );
    }

    return problem === null;
  }
}
