import { HttpClient, httpResource } from "@angular/common/http";
import {
  Component,
  computed,
  inject,
  linkedSignal,
  signal,
} from "@angular/core";
import { Router } from "@angular/router";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import { firstValueFrom } from "rxjs";

import type { Bonus, Rules, Stat } from "../../results/api";
import { CurrentSeason } from "../../results/current-season";
import type { FormulaTerm } from "../../results/stats";
import { formulaOf } from "../../results/stats";
import { LanguageService } from "../../shared/language";
import { AdminAction } from "../action";

/** One stat's rule as typed: whether it's recorded, and its points as text. */
export interface StatDraft {
  recorded: boolean;
  points: string;
}

/** One bonus as typed: its points and, for an "at least" bonus, its threshold. */
export interface BonusDraft {
  points: string;
  threshold: string;
}

/** A season's rules as typed on the Scoring screen; numbers stay text until they're saved. */
export interface RulesDraft {
  stats: Partial<Record<Stat, StatDraft>>;
  kdaPoints: string;
  pointsPerWin: string;
  bonuses: Record<string, BonusDraft>;
}

/** What `PUT …/admin/seasons/{n}/rules` takes. */
export interface RulesInput {
  stats: { stat: Stat; recorded: boolean; points: number }[];
  kdaPoints: number;
  pointsPerWin: number;
  bonuses: { code: string; points: number; threshold: number | null }[];
}

/** What's wrong with the typed rules, before they're sent. */
export type RulesProblem =
  | { kind: "stat"; stat: Stat }
  | { kind: "kda" }
  | { kind: "win" }
  | { kind: "bonus"; code: string }
  | { kind: "threshold"; code: string };

/** The API's limits. */
export const RULE_LIMITS = { maxPoints: 1000, maxThreshold: 40 };

const DECIMAL = /^-?\d{1,4}(?<decimals>\.\d{1,2})?$/u;
const WHOLE = /^\d{1,4}$/u;

/**
 * A typed number, within `min` and `max`, with up to two decimals unless `whole`; `invalid`
 * otherwise. A comma is a decimal point, as in French.
 */
export const parseRuleNumber = (
  text: string,
  limits: { min: number; max: number; whole?: boolean }
): number | "invalid" => {
  const typed = text.trim().replace(",", ".");
  const pattern = limits.whole ? WHOLE : DECIMAL;

  if (!pattern.test(typed)) {
    return "invalid";
  }

  const value = Number(typed);

  return value >= limits.min && value <= limits.max ? value : "invalid";
};

/** The rules as the form starts: what's saved. */
export const draftOf = (rules: Rules): RulesDraft => {
  const stats: Partial<Record<Stat, StatDraft>> = {};

  for (const rule of rules.stats) {
    stats[rule.stat] = { points: String(rule.points), recorded: rule.recorded };
  }

  const bonuses: Record<string, BonusDraft> = {};

  for (const bonus of rules.bonuses) {
    bonuses[bonus.code] = {
      points: String(bonus.points),
      threshold: bonus.threshold === null ? "" : String(bonus.threshold),
    };
  }

  return {
    bonuses,
    kdaPoints: String(rules.kdaPoints),
    pointsPerWin: String(rules.pointsPerWin),
    stats,
  };
};

const statPoints = (draft: RulesDraft, stat: Stat): number | "invalid" =>
  parseRuleNumber(draft.stats[stat]?.points ?? "0", {
    max: RULE_LIMITS.maxPoints,
    min: -RULE_LIMITS.maxPoints,
  });

const bonusPoints = (draft: RulesDraft, code: string): number | "invalid" =>
  parseRuleNumber(draft.bonuses[code]?.points ?? "0", {
    max: RULE_LIMITS.maxPoints,
    min: 0,
    whole: true,
  });

const thresholdOf = (draft: RulesDraft, code: string): number | "invalid" =>
  parseRuleNumber(draft.bonuses[code]?.threshold ?? "", {
    max: RULE_LIMITS.maxThreshold,
    min: 0,
  });

/** The first thing wrong with the typed rules; nothing when they can be saved. */
export const problemOf = (
  draft: RulesDraft,
  rules: Rules
): RulesProblem | null => {
  const badStat = rules.stats.find(
    (rule) => rule.countable && statPoints(draft, rule.stat) === "invalid"
  );

  if (badStat) {
    return { kind: "stat", stat: badStat.stat };
  }

  if (
    parseRuleNumber(draft.kdaPoints, {
      max: RULE_LIMITS.maxPoints,
      min: 0,
    }) === "invalid"
  ) {
    return { kind: "kda" };
  }

  if (
    parseRuleNumber(draft.pointsPerWin, {
      max: RULE_LIMITS.maxPoints,
      min: 0,
      whole: true,
    }) === "invalid"
  ) {
    return { kind: "win" };
  }

  for (const bonus of rules.bonuses) {
    if (bonusPoints(draft, bonus.code) === "invalid") {
      return { code: bonus.code, kind: "bonus" };
    }

    if (
      bonus.award === "AT_LEAST" &&
      thresholdOf(draft, bonus.code) === "invalid"
    ) {
      return { code: bonus.code, kind: "threshold" };
    }
  }

  return null;
};

const numberOr = (value: number | "invalid", fallback: number): number =>
  value === "invalid" ? fallback : value;

/** The request for the typed rules; check `problemOf` first. */
export const inputOf = (draft: RulesDraft, rules: Rules): RulesInput => ({
  bonuses: rules.bonuses.map((bonus) => ({
    code: bonus.code,
    points: numberOr(bonusPoints(draft, bonus.code), bonus.points),
    threshold:
      bonus.award === "AT_LEAST"
        ? numberOr(thresholdOf(draft, bonus.code), bonus.threshold ?? 0)
        : null,
  })),
  kdaPoints: Number(draft.kdaPoints.trim().replace(",", ".")),
  pointsPerWin: Number(draft.pointsPerWin.trim()),
  stats: rules.stats.map((rule) => ({
    points: rule.countable ? numberOr(statPoints(draft, rule.stat), 0) : 0,
    recorded: draft.stats[rule.stat]?.recorded ?? rule.recorded,
    stat: rule.stat,
  })),
});

/** The rules as typed, for the formula preview: a number that isn't valid yet keeps its saved value. */
export const previewOf = (draft: RulesDraft, rules: Rules): Rules => ({
  bonuses: rules.bonuses.map((bonus) => ({
    ...bonus,
    points: numberOr(bonusPoints(draft, bonus.code), bonus.points),
  })),
  kdaPoints: numberOr(
    parseRuleNumber(draft.kdaPoints, { max: RULE_LIMITS.maxPoints, min: 0 }),
    rules.kdaPoints
  ),
  pointsPerWin: numberOr(
    parseRuleNumber(draft.pointsPerWin, {
      max: RULE_LIMITS.maxPoints,
      min: 0,
      whole: true,
    }),
    rules.pointsPerWin
  ),
  stats: rules.stats.map((rule) => ({
    ...rule,
    points: rule.countable
      ? numberOr(statPoints(draft, rule.stat), rule.points)
      : rule.points,
    recorded: draft.stats[rule.stat]?.recorded ?? rule.recorded,
  })),
});

/** How an admin's own bonus is won; MVP and SVP are the platform's. */
export type CustomAward = "HIGHEST" | "LOWEST" | "AT_LEAST" | "MANUAL";

export const CUSTOM_AWARDS: readonly CustomAward[] = [
  "HIGHEST",
  "LOWEST",
  "AT_LEAST",
  "MANUAL",
];

export const MAX_BONUS_LABEL = 40;

/** A new bonus as typed. */
export interface NewBonusDraft {
  label: string;
  award: CustomAward;
  /** Empty until one is picked; a ticked bonus has none. */
  stat: Stat | "";
  threshold: string;
  points: string;
}

export const EMPTY_BONUS: NewBonusDraft = {
  award: "HIGHEST",
  label: "",
  points: "10",
  stat: "",
  threshold: "",
};

export type NewBonusProblem = "label" | "stat" | "threshold" | "points";

/** The first thing wrong with a new bonus, checked as the API checks it. */
export const newBonusProblem = (
  draft: NewBonusDraft
): NewBonusProblem | null => {
  const label = draft.label.trim();

  if (label === "" || label.length > MAX_BONUS_LABEL) {
    return "label";
  }

  if (draft.award !== "MANUAL" && draft.stat === "") {
    return "stat";
  }

  if (
    draft.award === "AT_LEAST" &&
    parseRuleNumber(draft.threshold, {
      max: RULE_LIMITS.maxThreshold,
      min: 0,
    }) === "invalid"
  ) {
    return "threshold";
  }

  return parseRuleNumber(draft.points, {
    max: RULE_LIMITS.maxPoints,
    min: 0,
    whole: true,
  }) === "invalid"
    ? "points"
    : null;
};

/** What `POST …/rules/bonuses` takes, for a new bonus that passed `newBonusProblem`. */
export const newBonusInput = (draft: NewBonusDraft) => ({
  award: draft.award,
  label: draft.label.trim(),
  points: Number(draft.points.trim()),
  stat: draft.award === "MANUAL" || draft.stat === "" ? null : draft.stat,
  threshold:
    draft.award === "AT_LEAST"
      ? Number(draft.threshold.trim().replace(",", "."))
      : null,
});

/**
 * The Scoring screen: for one season, which stats are recorded and what each stat, each game's
 * KDA ratio, a win and each bonus are worth. A change applies to the whole season at once; a
 * finished season's rules can't change.
 */
@Component({
  imports: [TranslocoDirective],
  selector: "app-scoring-admin",
  styleUrl: "./scoring.css",
  templateUrl: "./scoring.html",
})
export class ScoringAdminPage {
  protected readonly current = inject(CurrentSeason);
  protected readonly language = inject(LanguageService);
  protected readonly limits = RULE_LIMITS;

  protected readonly rules = httpResource<Rules>(() => {
    const api = this.current.api();

    return api ? `${api}/rules` : undefined;
  });

  /** The rules as typed; starts from what's saved. */
  protected readonly draft = linkedSignal<RulesDraft | null>(() =>
    this.rules.hasValue() ? draftOf(this.rules.value()) : null
  );

  /** Whether the form differs from what's saved. */
  protected readonly changed = computed(() => {
    const draft = this.draft();

    return (
      this.rules.hasValue() &&
      draft !== null &&
      JSON.stringify(draft) !== JSON.stringify(draftOf(this.rules.value()))
    );
  });

  /** A finished season's rules can't change: reopen it on the Seasons screen first. */
  protected readonly locked = computed(
    () => this.current.season()?.finished ?? false
  );

  protected readonly action = new AdminAction((status) =>
    this.#transloco.translate(
      {
        400: "adminScoring.invalid",
        409: "adminScoring.finished",
      }[status] ?? "adminScoring.failed"
    )
  );

  protected readonly customAwards = CUSTOM_AWARDS;
  protected readonly maxBonusLabel = MAX_BONUS_LABEL;

  /** The bonus being added, as typed. */
  protected readonly newBonus = signal<NewBonusDraft>(EMPTY_BONUS);

  /** The stats a new bonus can compare: the ones the season records. */
  protected readonly recordedStats = computed<Stat[]>(() =>
    this.rules.hasValue()
      ? this.rules
          .value()
          .stats.filter((rule) => rule.recorded)
          .map((rule) => rule.stat)
      : []
  );

  readonly #http = inject(HttpClient);
  readonly #router = inject(Router);
  readonly #transloco = inject(TranslocoService);

  /** The formula as typed, pill by pill, in the language on screen (re-read when it changes). */
  protected formula(): FormulaTerm[] {
    const draft = this.draft();

    return draft && this.rules.hasValue()
      ? formulaOf(
          previewOf(draft, this.rules.value()),
          (key, params) => this.#transloco.translate(key, params),
          (value) => this.language.points(value, 2)
        )
      : [];
  }

  protected pickNewBonusAward(value: string): void {
    const award = CUSTOM_AWARDS.find((each) => each === value);

    if (award) {
      this.typeNewBonus({ award });
    }
  }

  protected pickNewBonusStat(value: string): void {
    this.typeNewBonus({
      stat: this.recordedStats().find((each) => each === value) ?? "",
    });
  }

  protected typeNewBonus(change: Partial<NewBonusDraft>): void {
    this.newBonus.update((draft) => ({ ...draft, ...change }));
  }

  /** Adds the new bonus; the season's saved games are worked out again with it. */
  protected async addBonus(event: Event): Promise<void> {
    event.preventDefault();
    const api = this.#api();
    const draft = this.newBonus();
    const problem = newBonusProblem(draft);

    if (!api) {
      return;
    }

    if (problem) {
      this.action.refuse(
        this.#transloco.translate(`adminScoring.newBonusProblems.${problem}`, {
          max: String(MAX_BONUS_LABEL),
          maxPoints: String(RULE_LIMITS.maxPoints),
          maxThreshold: String(RULE_LIMITS.maxThreshold),
        })
      );

      return;
    }

    const done = await this.action.run(async () => {
      this.rules.set(
        await firstValueFrom(
          this.#http.post<Rules>(`${api}/bonuses`, newBonusInput(draft))
        )
      );

      return this.#transloco.translate("adminScoring.bonusAdded", {
        label: draft.label.trim(),
      });
    });

    if (done) {
      this.newBonus.set(EMPTY_BONUS);
    }
  }

  /** Removes a bonus after asking: every award of it goes too. */
  protected async removeBonus(bonus: Bonus): Promise<void> {
    const api = this.#api();
    const season = this.current.season();

    if (
      !api ||
      !season ||
      // oxlint-disable-next-line no-alert -- a plain confirmation is enough for an admin action
      !confirm(
        this.#transloco.translate("adminScoring.removeConfirm", {
          code: bonus.code,
          season: season.name,
        })
      )
    ) {
      return;
    }

    await this.action.run(async () => {
      this.rules.set(
        await firstValueFrom(
          this.#http.delete<Rules>(
            `${api}/bonuses/${encodeURIComponent(bonus.code)}`
          )
        )
      );

      return this.#transloco.translate("adminScoring.bonusRemoved", {
        code: bonus.code,
      });
    });
  }

  protected statDraft(stat: Stat): StatDraft {
    return this.draft()?.stats[stat] ?? { points: "0", recorded: false };
  }

  protected bonusDraft(code: string): BonusDraft {
    return this.draft()?.bonuses[code] ?? { points: "0", threshold: "" };
  }

  protected toggle(stat: Stat, recorded: boolean): void {
    this.#updateStat(stat, { recorded });
  }

  protected typeStatPoints(stat: Stat, points: string): void {
    this.#updateStat(stat, { points });
  }

  protected typeKda(kdaPoints: string): void {
    this.draft.update((draft) => draft && { ...draft, kdaPoints });
  }

  protected typeWin(pointsPerWin: string): void {
    this.draft.update((draft) => draft && { ...draft, pointsPerWin });
  }

  protected typeBonus(code: string, change: Partial<BonusDraft>): void {
    this.draft.update(
      (draft) =>
        draft && {
          ...draft,
          bonuses: {
            ...draft.bonuses,
            [code]: { ...this.bonusDraft(code), ...change },
          },
        }
    );
  }

  protected pickSeason(value: string): void {
    void this.#router.navigate([], {
      queryParams: { season: value },
      queryParamsHandling: "merge",
    });
  }

  /** Saves the rules; the whole season's points follow at once. */
  protected async save(): Promise<void> {
    const draft = this.draft();
    const api = this.#api();
    const season = this.current.season();

    if (!draft || !api || !season || !this.rules.hasValue()) {
      return;
    }

    const rules = this.rules.value();
    const problem = problemOf(draft, rules);

    if (problem) {
      this.action.refuse(this.#explain(problem));

      return;
    }

    await this.action.run(async () => {
      this.rules.set(
        await firstValueFrom(this.#http.put<Rules>(api, inputOf(draft, rules)))
      );

      return this.#transloco.translate("adminScoring.saved", {
        season: season.name,
      });
    });
  }

  /** Replaces the season's rules with the platform's, after asking. */
  protected async useDefaults(): Promise<void> {
    const api = this.#api();
    const season = this.current.season();

    if (
      !api ||
      !season ||
      // oxlint-disable-next-line no-alert -- a plain confirmation is enough for an admin action
      !confirm(
        this.#transloco.translate("adminScoring.defaultsConfirm", {
          season: season.name,
        })
      )
    ) {
      return;
    }

    await this.action.run(async () => {
      this.rules.set(
        await firstValueFrom(
          this.#http.post<Rules>(`${api}/platform-defaults`, null)
        )
      );

      return this.#transloco.translate("adminScoring.defaultsDone", {
        season: season.name,
      });
    });
  }

  #updateStat(stat: Stat, change: Partial<StatDraft>): void {
    this.draft.update(
      (draft) =>
        draft && {
          ...draft,
          stats: {
            ...draft.stats,
            [stat]: { ...this.statDraft(stat), ...change },
          },
        }
    );
  }

  #api(): string | undefined {
    const admin = this.current.adminApi();
    const season = this.current.season();

    return admin && season
      ? `${admin}/seasons/${season.number}/rules`
      : undefined;
  }

  #explain(problem: RulesProblem): string {
    const limits = { ...this.limits };

    switch (problem.kind) {
      case "stat": {
        return this.#transloco.translate("adminScoring.problems.stat", {
          ...limits,
          stat: this.#transloco.translate(`stats.long.${problem.stat}`),
        });
      }

      case "bonus": {
        return this.#transloco.translate("adminScoring.problems.bonus", {
          ...limits,
          code: problem.code,
        });
      }

      case "threshold": {
        return this.#transloco.translate("adminScoring.problems.threshold", {
          ...limits,
          code: problem.code,
        });
      }

      default: {
        return this.#transloco.translate(
          `adminScoring.problems.${problem.kind}`,
          limits
        );
      }
    }
  }
}
