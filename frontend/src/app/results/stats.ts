import type { Rules, Stat, Standing } from "./api";

/** A column of a results table: an average stat, or the KDA ratio standing in for K, D and A. */
export type Column = Stat | "KDA";

const IN_KDA: ReadonlySet<Stat> = new Set(["KILLS", "DEATHS", "ASSISTS"]);

/** Stats whose averages are large numbers or percentages, shown without decimals. */
const WHOLE: ReadonlySet<Column> = new Set([
  "PERFORMANCE_SCORE",
  "ACS",
  "ADR",
  "DAMAGE_DELTA",
  "KAST_PCT",
  "ECON_RATING",
  "HEADSHOT_PCT",
]);

/**
 * The leaderboard's stat columns, in the stats' order: one average per stat a game recorded,
 * with kills, deaths and assists folded into a single KDA where kills come.
 */
export const columnsFor = (stats: readonly Stat[]): Column[] => {
  const columns: Column[] = [];

  for (const stat of stats) {
    if (!IN_KDA.has(stat)) {
      columns.push(stat);
    } else if (!columns.includes("KDA")) {
      columns.push("KDA");
    }
  }

  return columns;
};

/**
 * Per-round averages, percentages and the KDA ratio: adding them up over games means nothing, so
 * they stay averages when a table shows totals.
 */
const NOT_ADDED: ReadonlySet<Column> = new Set([
  "ADR",
  "DAMAGE_DELTA",
  "KAST_PCT",
  "HEADSHOT_PCT",
  "KDA",
  "PLACE",
]);

/** Whether a column can show a total over several games. */
export const addsUp = (column: Column): boolean => !NOT_ADDED.has(column);

/** How many decimals a column shows: none for totals and large numbers, two otherwise. */
export const decimalsOf = (column: Column, total = false): number =>
  total || WHOLE.has(column) ? 0 : 2;

/**
 * A standing's value for a column, its total over the games when `total` and the column adds up;
 * nothing when the player has no game recording it.
 */
export const valueOf = (
  standing: Standing,
  column: Column,
  total = false
): number | undefined => {
  if (column === "KDA") {
    return standing.kda;
  }

  return total && addsUp(column)
    ? standing.totals[column]
    : standing.averages[column];
};

/** One pill of "How points are counted", such as "+ Kills", "− Deaths" or "+ 50 per win". */
export interface FormulaTerm {
  text: string;
  win: boolean;
}

/**
 * The bonuses someone can win this season: a bonus worked out from a stat that isn't recorded,
 * such as Most honourable without Honours, can't be won, so it isn't shown.
 */
export const winnableBonuses = (rules: Rules): Rules["bonuses"] =>
  rules.bonuses.filter(
    (bonus) =>
      !rules.stats.some((rule) => rule.stat === bonus.stat && !rule.recorded)
  );

/** Translates a key of public/i18n, as Transloco's `translate` does. */
type Translate = (key: string, params?: Record<string, string>) => string;

/**
 * The season's points formula, pill by pill: each recorded stat that counts, then the bonuses
 * (one pill when they're all worth the same), then the points per win.
 */
export const formulaOf = (
  rules: Rules,
  t: Translate,
  format: (value: number) => string
): FormulaTerm[] => {
  const terms: { points: number; text: string; win?: boolean }[] = [];

  for (const rule of rules.stats) {
    if (rule.recorded && rule.countable && rule.points !== 0) {
      const name = t(`stats.long.${rule.stat}`);
      const size = Math.abs(rule.points);
      terms.push({
        points: rule.points,
        text: size === 1 ? name : `${format(size)} × ${name}`,
      });
    }
  }

  if (rules.kdaPoints !== 0) {
    terms.push({
      points: rules.kdaPoints,
      text: `${format(rules.kdaPoints)} × ${t("stats.short.KDA")}`,
    });
  }

  const bonuses = winnableBonuses(rules);
  const [first] = bonuses;

  if (first && bonuses.every((bonus) => bonus.points === first.points)) {
    terms.push({
      points: first.points,
      text: t("leaderboard.perBonus", { points: format(first.points) }),
    });
  } else {
    for (const bonus of bonuses) {
      terms.push({
        points: bonus.points,
        text: `${format(bonus.points)} ${bonus.label}`,
      });
    }
  }

  if (rules.pointsPerWin !== 0) {
    terms.push({
      points: rules.pointsPerWin,
      text: t("leaderboard.perWin", { points: format(rules.pointsPerWin) }),
      win: true,
    });
  }

  return terms.map((term, index) => ({
    text:
      index === 0 && term.points > 0
        ? term.text
        : `${term.points < 0 ? "−" : "+"} ${term.text}`,
    win: term.win ?? false,
  }));
};
