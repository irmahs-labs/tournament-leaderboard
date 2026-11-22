import { HttpClient, httpResource } from "@angular/common/http";
import { Component, computed, inject } from "@angular/core";
import { RouterLink } from "@angular/router";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import type { Observable } from "rxjs";
import { firstValueFrom } from "rxjs";

import { AdminAction } from "../admin/action";
import { CurrentSeason } from "../results/current-season";
import { LanguageService } from "../shared/language";
import type { MyMatch, MyMatches, MySeason, MySeasons } from "./api";

/**
 * My seasons: the organisation's seasons not over yet, signing up for one and withdrawing. Signing
 * up needs a player profile, claimed or created on the claim page.
 */
@Component({
  imports: [RouterLink, TranslocoDirective],
  selector: "app-my-seasons",
  styleUrl: "./my-seasons.css",
  templateUrl: "./my-seasons.html",
})
export class MySeasonsPage {
  protected readonly current = inject(CurrentSeason);
  protected readonly language = inject(LanguageService);

  /** The organisation's API, such as `/api/orgs/alsace-arena`. */
  readonly #org = computed(() => {
    const org = this.current.organization();

    return org ? `/api/orgs/${encodeURIComponent(org.slug)}` : undefined;
  });

  protected readonly mine = httpResource<MySeasons>(() => {
    const org = this.#org();

    return org ? `${org}/me/seasons` : undefined;
  });

  /** The upcoming matches of the seasons they're signed up for. */
  protected readonly matches = httpResource<MyMatches>(() => {
    const org = this.#org();
    const state = this.mine.hasValue() ? this.mine.value() : null;

    return org && state?.player ? `${org}/me/matches` : undefined;
  });

  protected readonly action = new AdminAction((status) =>
    this.#transloco.translate(
      status === 409 ? "mySeasons.conflict" : "mySeasons.failed"
    )
  );

  readonly #http = inject(HttpClient);
  readonly #transloco = inject(TranslocoService);

  protected async signUp(season: MySeason): Promise<void> {
    await this.#change(
      season,
      (api) => this.#http.post<MySeasons>(api, null),
      "mySeasons.signedUp"
    );
  }

  protected async withdraw(season: MySeason): Promise<void> {
    await this.#change(
      season,
      (api) => this.#http.delete<MySeasons>(api),
      "mySeasons.withdrawn"
    );
  }

  /** A season's upcoming matches. */
  protected matchesOf(season: MySeason): MyMatch[] {
    return this.matches.hasValue()
      ? this.matches
          .value()
          .matches.filter((match) => match.seasonNumber === season.number)
      : [];
  }

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

      this.matches.set(after);
      const mine = after.matches.find((each) => each.id === match.id);

      return this.#transloco.translate(
        `mySeasons.answered.${mine?.status ?? "DECLINED"}`,
        { number: match.number, position: mine?.position ?? "" }
      );
    });
  }

  /** The season's dates, such as "1 Sept – 15 Dec 2026", or nothing. */
  protected dates(season: MySeason): string {
    return [season.startsOn, season.endsOn]
      .flatMap((day) => (day === null ? [] : [this.language.day(day)]))
      .join(" – ");
  }

  async #change(
    season: MySeason,
    request: (api: string) => Observable<MySeasons>,
    done: string
  ): Promise<void> {
    const org = this.#org();

    if (!org) {
      return;
    }

    await this.action.run(async () => {
      this.mine.set(
        await firstValueFrom(
          request(`${org}/seasons/${season.number}/registration`)
        )
      );

      this.matches.reload();

      return this.#transloco.translate(done, { name: season.name });
    });
  }
}
