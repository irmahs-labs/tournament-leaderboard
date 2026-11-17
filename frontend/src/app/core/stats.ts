import type { Bonus, Rules, Stat, Standing } from "./seasons";

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

export const formatNumber = (
  value: number | null | undefined,
  lang: string,
  decimals = 0
): string =>
  value === null || value === undefined
    ? "–"
    : new Intl.NumberFormat(lang, {
        maximumFractionDigits: decimals,
        minimumFractionDigits: decimals,
      }).format(value);

/** One pill of "How points are counted". */
export interface FormulaTerm {
  kind: "stat" | "bonus" | "bonuses" | "win";
  /** Negative when the term subtracts. */
  points: number;
  stat?: Stat;
  bonus?: Bonus;
}

/**
 * The season's points formula, term by term: each recorded stat that counts, then the bonuses
 * (one term when they're all worth the same), then the points per win.
 */
export const formulaOf = (rules: Rules): FormulaTerm[] => {
  const terms: FormulaTerm[] = rules.stats
    .filter((rule) => rule.recorded && rule.countable && rule.effect !== 0)
    .map((rule) => ({ kind: "stat", points: rule.effect, stat: rule.stat }));

  const [first] = rules.bonuses;

  if (first && rules.bonuses.every((bonus) => bonus.points === first.points)) {
    terms.push({ kind: "bonuses", points: first.points });
  } else {
    for (const bonus of rules.bonuses) {
      terms.push({ bonus, kind: "bonus", points: bonus.points });
    }
  }

  if (rules.pointsPerWin !== 0) {
    terms.push({ kind: "win", points: rules.pointsPerWin });
  }

  return terms;
};

/** The player's name without the #tag, and the tag. */
export const splitRiotId = (riotId: string): { name: string; tag: string } => {
  const at = riotId.lastIndexOf("#");

  return at > 0
    ? { name: riotId.slice(0, at), tag: riotId.slice(at) }
    : { name: riotId, tag: "" };
};
