import { httpResource } from "@angular/common/http";
import { Component, computed, inject } from "@angular/core";
import { RouterLink } from "@angular/router";
import { TranslocoDirective } from "@jsverse/transloco";

import { LanguageService } from "../../shared/language";
import type { MatchCard } from "../api";
import { CurrentSeason } from "../current-season";
import { splitRiotId } from "../stats";

/** Where a match stands: played, the next one to play, or further ahead. */
export type MatchStatus = "played" | "next" | "scheduled";

/**
 * The status of each match, in order. The first match without scores is the next one, unless the
 * season is over.
 */
export const statusesOf = (
  cards: readonly MatchCard[],
  finished: boolean
): MatchStatus[] => {
  const next = finished ? -1 : cards.findIndex((card) => card.games === 0);

  return cards.map((card, index) => {
    if (card.games > 0) {
      return "played";
    }

    return index === next ? "next" : "scheduled";
  });
};

/** Every match of the season picked in the header, played or only planned. */
@Component({
  imports: [RouterLink, TranslocoDirective],
  selector: "app-matches",
  styleUrl: "./matches.css",
  templateUrl: "./matches.html",
})
export class MatchesPage {
  protected readonly current = inject(CurrentSeason);
  protected readonly language = inject(LanguageService);

  protected readonly cards = httpResource<MatchCard[]>(() => {
    const api = this.current.api();

    return api ? `${api}/matches` : undefined;
  });

  protected readonly statuses = computed(() =>
    this.cards.hasValue()
      ? statusesOf(this.cards.value(), this.current.season()?.finished ?? false)
      : []
  );

  protected readonly splitRiotId = splitRiotId;
}
