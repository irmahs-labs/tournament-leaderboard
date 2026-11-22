import { HttpClient, httpResource } from "@angular/common/http";
import { Component, computed, inject } from "@angular/core";
import { RouterLink } from "@angular/router";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import { firstValueFrom } from "rxjs";

import { AdminAction } from "../admin/action";
import { CurrentSeason } from "../results/current-season";
import { LanguageService } from "../shared/language";
import type { MyMatch, MyMatches } from "./api";

/** The upcoming matches, grouped by season in the order they come. */
export const bySeason = (
  matches: readonly MyMatch[]
): { name: string; number: number; matches: MyMatch[] }[] => {
  const seasons: { name: string; number: number; matches: MyMatch[] }[] = [];

  for (const match of matches) {
    const season = seasons.find((each) => each.number === match.seasonNumber);

    if (season) {
      season.matches.push(match);
    } else {
      seasons.push({
        matches: [match],
        name: match.seasonName,
        number: match.seasonNumber,
      });
    }
  }

  return seasons;
};

/**
 * My matches: the upcoming matches of every season under way, each with "I'm in" or "Can't make
 * it". There's nothing to sign up for: every player of the organisation answers match by match.
 */
@Component({
  imports: [RouterLink, TranslocoDirective],
  selector: "app-my-matches",
  styleUrl: "./my-matches.css",
  templateUrl: "./my-matches.html",
})
export class MyMatchesPage {
  protected readonly current = inject(CurrentSeason);
  protected readonly language = inject(LanguageService);

  /** The organisation's API, such as `/api/orgs/alsace-arena`. */
  readonly #org = computed(() => {
    const org = this.current.organization();

    return org ? `/api/orgs/${encodeURIComponent(org.slug)}` : undefined;
  });

  protected readonly mine = httpResource<MyMatches>(() => {
    const org = this.#org();

    return org ? `${org}/me/matches` : undefined;
  });

  protected readonly seasons = computed(() =>
    this.mine.hasValue() ? bySeason(this.mine.value().matches) : []
  );

  protected readonly action = new AdminAction((status) =>
    this.#transloco.translate(
      status === 409 ? "myMatches.conflict" : "myMatches.failed"
    )
  );

  readonly #http = inject(HttpClient);
  readonly #transloco = inject(TranslocoService);

  /** "I'm in" or "Can't make it". */
  protected async answer(match: MyMatch, answer: "YES" | "NO"): Promise<void> {
    const org = this.#org();

    if (!org) {
      return;
    }

    await this.action.run(async () => {
      const after = await firstValueFrom(
        this.#http.put<MyMatches>(`${org}/matches/${match.id}/attendance`, {
          answer,
        })
      );

      this.mine.set(after);
      const mine = after.matches.find((each) => each.id === match.id);

      return this.#transloco.translate(
        `myMatches.answered.${mine?.status ?? "DECLINED"}`,
        { number: match.number, position: mine?.position ?? "" }
      );
    });
  }
}
