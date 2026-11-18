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

export const decimalsOf = (column: Column): number =>
  WHOLE.has(column) ? 0 : 2;

/** A standing's value for a column; nothing when the player has no game recording it. */
export const valueOf = (
  standing: Standing,
  column: Column
): number | undefined =>
  column === "KDA" ? standing.kda : standing.averages[column];

/** One pill of "How points are counted", such as "+ Kills", "− Deaths" or "+ 50 per win". */
export interface FormulaTerm {
  text: string;
  win: boolean;
}

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
    if (rule.recorded && rule.countable && rule.effect !== 0) {
      const name = t(`stats.long.${rule.stat}`);
      const size = Math.abs(rule.effect);
      terms.push({
        points: rule.effect,
        text: size === 1 ? name : `${format(size)} × ${name}`,
      });
    }
  }

  const [first] = rules.bonuses;

  if (first && rules.bonuses.every((bonus) => bonus.points === first.points)) {
    terms.push({
      points: first.points,
      text: t("leaderboard.perBonus", { points: format(first.points) }),
    });
  } else {
    for (const bonus of rules.bonuses) {
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

/** The player's name without the #tag, and the tag. */
export const splitRiotId = (riotId: string): { name: string; tag: string } => {
  const at = riotId.lastIndexOf("#");

  return at > 0
    ? { name: riotId.slice(0, at), tag: riotId.slice(at) }
    : { name: riotId, tag: "" };
};
