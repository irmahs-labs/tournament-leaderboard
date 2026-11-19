import { httpResource } from "@angular/common/http";
import { Component, computed, inject, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { TranslocoDirective } from "@jsverse/transloco";

import { LanguageService } from "../../shared/language";
import { RankPill } from "../../shared/rank-pill";
import type { Leaderboard, PublicPlayer } from "../api";
import { CurrentSeason } from "../current-season";
import { splitRiotId } from "../stats";
import type { PlayerSort } from "./players-list";
import { cardsOf, PLAYER_SORTS, shownCards, TIERS } from "./players-list";

/**
 * The public Players page: everyone who played the season picked in the header, with their rank,
 * points and averages, to search, sort and filter by rank. Each links to their profile.
 */
@Component({
  imports: [RankPill, RouterLink, TranslocoDirective],
  selector: "app-players",
  styleUrl: "./players.css",
  templateUrl: "./players.html",
})
export class PlayersPage {
  protected readonly current = inject(CurrentSeason);
  protected readonly language = inject(LanguageService);
  protected readonly sorts = PLAYER_SORTS;
  protected readonly tiers = TIERS;
  protected readonly splitRiotId = splitRiotId;

  protected readonly board = httpResource<Leaderboard>(() => {
    const api = this.current.api();

    return api ? `${api}/leaderboard` : undefined;
  });

  readonly #players = httpResource<PublicPlayer[]>(() => {
    const org = this.current.organization();

    return org
      ? `/api/orgs/${encodeURIComponent(org.slug)}/players`
      : undefined;
  });

  protected readonly search = signal("");
  protected readonly sortBy = signal<PlayerSort>("points");
  protected readonly tier = signal("");

  protected readonly cards = computed(() =>
    this.board.hasValue()
      ? shownCards(
          cardsOf(
            this.board.value().standings,
            this.#players.hasValue() ? this.#players.value() : []
          ),
          { search: this.search(), sort: this.sortBy(), tier: this.tier() }
        )
      : []
  );

  protected pickSort(value: string): void {
    this.sortBy.set(PLAYER_SORTS.find((each) => each === value) ?? "points");
  }

  /** Wins over games, as a whole percentage. */
  protected winRate(wins: number, games: number): string {
    return games === 0
      ? "–"
      : `${this.language.number((wins / games) * 100)} %`;
  }
}
