import { HttpClient, httpResource } from "@angular/common/http";
import { Component, computed, inject, linkedSignal } from "@angular/core";
import { Router } from "@angular/router";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import { firstValueFrom } from "rxjs";

import type { Rules, Stat, StatRule } from "../../results/api";
import { CurrentSeason } from "../../results/current-season";
import { AdminAction } from "../action";

/** How a stat counts towards points, as a key of `adminScoring.effects`. */
export const effectOf = (rule: StatRule): string => {
  if (!rule.countable) {
    return "shownOnly";
  }

  if (rule.effect > 0) {
    return "adds";
  }

  return rule.effect < 0 ? "subtracts" : "notCounted";
};

/**
 * The Record switches of the Scoring screen: which stats the season asks for in games entered from
 * now on. Games already saved keep their values and their points. The rest of the screen (effects,
 * bonuses, points per win) comes with the rules editor.
 */
@Component({
  imports: [TranslocoDirective],
  selector: "app-scoring-admin",
  styleUrl: "./scoring.css",
  templateUrl: "./scoring.html",
})
export class ScoringAdminPage {
  protected readonly current = inject(CurrentSeason);

  protected readonly rules = httpResource<Rules>(() => {
    const api = this.current.api();

    return api ? `${api}/rules` : undefined;
  });

  /** The stats switched on, as on screen; starts from the season's rules. */
  protected readonly recorded = linkedSignal<ReadonlySet<Stat>>(
    () =>
      new Set(
        this.rules.hasValue()
          ? this.rules
              .value()
              .stats.filter((rule) => rule.recorded)
              .map((rule) => rule.stat)
          : []
      )
  );

  /** Whether the switches differ from what's saved. */
  protected readonly changed = computed(() => {
    if (!this.rules.hasValue()) {
      return false;
    }

    const on = this.recorded();

    return this.rules
      .value()
      .stats.some((rule) => rule.recorded !== on.has(rule.stat));
  });

  protected readonly action = new AdminAction((status) =>
    this.#transloco.translate(
      status === 400 ? "adminScoring.invalid" : "adminScoring.failed"
    )
  );

  protected readonly effectOf = effectOf;

  readonly #http = inject(HttpClient);
  readonly #router = inject(Router);
  readonly #transloco = inject(TranslocoService);

  protected toggle(stat: Stat, on: boolean): void {
    this.recorded.update((recorded) => {
      const next = new Set(recorded);

      if (on) {
        next.add(stat);
      } else {
        next.delete(stat);
      }

      return next;
    });
  }

  protected pickSeason(value: string): void {
    void this.#router.navigate([], {
      queryParams: { season: value },
      queryParamsHandling: "merge",
    });
  }

  /** Saves the switches: exactly these stats are asked for from now on. */
  protected async save(): Promise<void> {
    const org = this.current.organization();
    const season = this.current.season();

    if (!org || !season || !this.rules.hasValue()) {
      return;
    }

    const on = this.recorded();

    const stats = this.rules
      .value()
      .stats.map((rule) => rule.stat)
      .filter((stat) => on.has(stat));

    await this.action.run(async () => {
      const saved = await firstValueFrom(
        this.#http.put<Rules>(
          `/api/orgs/${encodeURIComponent(org.slug)}/admin/seasons/${season.number}/recorded-stats`,
          { stats }
        )
      );

      this.rules.set(saved);

      return this.#transloco.translate("adminScoring.saved", {
        season: season.name,
      });
    });
  }
}
