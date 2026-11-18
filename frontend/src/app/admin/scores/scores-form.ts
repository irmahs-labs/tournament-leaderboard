import type { GameLine, Stat } from "../../results/api";

/** One player picked for a line, by id; or a Riot ID typed for a player not on the list yet. */
export type LinePlayer =
  | { kind: "known"; playerId: string }
  | { kind: "new"; riotId: string }
  | { kind: "none" };

/** One line of the Enter scores form, as typed. */
export interface Row {
  /** Stable across edits, for the table's tracking. */
  key: number;
  player: LinePlayer;
  /** Each recorded stat as typed, so a half-typed number stays on screen. */
  stats: Partial<Record<Stat, string>>;
  win: boolean;
  /**
   * The bonuses the line wins, by code: the manual ones ticked, and the automatic ones the server
   * worked out, which go when a stat changes until they're checked again or saved.
   */
  bonuses: string[];
  /** The line's points once saved; nothing while it has unsaved changes. */
  points: number | null;
}

/** What `PUT …/games/{g}` takes for one line. */
export interface LineInput {
  playerId: string | null;
  riotId: string | null;
  stats: Partial<Record<Stat, number>>;
  win: boolean;
  manualBonuses: string[];
}

/** What `GET` and `PUT …/admin/seasons/{n}/matches/{m}/games/{g}` answer. */
export interface GameScores {
  season: number;
  matchNumber: number;
  gameNumber: number;
  matchDate: string | null;
  saved: boolean;
  lines: GameLine[];
}

/** `GET …/games/{g}/previous-roster`: the players of the latest saved game before this one. */
export interface PreviousRoster {
  matchNumber: number | null;
  gameNumber: number | null;
  players: { playerId: string; riotId: string }[];
}

/** How precisely a stat is typed: whole numbers, one decimal (may be negative), or a percentage. */
type Precision = "whole" | "tenths" | "signedTenths" | "percent";

const PRECISION: Partial<Record<Stat, Precision>> = {
  ADR: "tenths",
  DAMAGE_DELTA: "signedTenths",
  HEADSHOT_PCT: "percent",
  KAST_PCT: "percent",
};

/** The API's limits: smallint for counts, numeric(5,1) for averages. */
const MAX_WHOLE = 32_767;
const MAX_TENTHS = 9999.9;

/** The value a stat box holds: a number, nothing when it's empty, or `invalid`. */
export const parseStat = (
  stat: Stat,
  text: string | undefined
): number | null | "invalid" => {
  // A comma is a decimal point in French.
  const typed = (text ?? "").trim().replace(",", ".");

  if (typed === "") {
    return null;
  }

  const precision = PRECISION[stat] ?? "whole";

  const pattern = {
    percent: /^\d{1,3}(?<hundredths>\.\d{1,2})?$/u,
    signedTenths: /^-?\d{1,4}(?<tenths>\.\d)?$/u,
    tenths: /^\d{1,4}(?<decimal>\.\d)?$/u,
    whole: /^\d{1,5}$/u,
  }[precision];

  if (!pattern.test(typed)) {
    return "invalid";
  }

  const value = Number(typed);

  const max = {
    percent: 100,
    signedTenths: MAX_TENTHS,
    tenths: MAX_TENTHS,
    whole: MAX_WHOLE,
  }[precision];

  return Math.abs(value) <= max ? value : "invalid";
};

const NOBODY: LinePlayer = { kind: "none" };

let nextKey = 0;

/** An empty line, or one for a player carried over from the previous game. */
export const emptyRow = (player: LinePlayer = NOBODY): Row => {
  nextKey += 1;

  return {
    bonuses: [],
    key: nextKey,
    player,
    points: null,
    stats: {},
    win: false,
  };
};

/** Every stat, in the order the pages show them. */
export const STATS: readonly Stat[] = [
  "PERFORMANCE_SCORE",
  "ACS",
  "ADR",
  "DAMAGE_DELTA",
  "KAST_PCT",
  "KILLS",
  "DEATHS",
  "ASSISTS",
  "ECON_RATING",
  "FIRST_BLOODS",
  "PLANTS",
  "DEFUSES",
  "HEADSHOT_PCT",
  "CLUTCHES",
];

/** A saved line as a row, with its stats as text. */
export const rowFrom = (line: GameLine): Row => {
  const typed: Partial<Record<Stat, string>> = {};

  for (const stat of STATS) {
    const value = line.stats[stat];

    if (value !== undefined) {
      typed[stat] = String(value);
    }
  }

  return {
    ...emptyRow({ kind: "known", playerId: line.playerId }),
    bonuses: line.bonuses,
    points: line.points,
    stats: typed,
    win: line.win,
  };
};

/** Whether a row will be saved: it names a player. */
export const isUsed = (row: Row): boolean =>
  row.player.kind === "known" ||
  (row.player.kind === "new" && row.player.riotId.trim() !== "");

/** What's wrong with the form before it's sent; nothing when it can be saved. */
export type Problem =
  | { kind: "empty" }
  | { kind: "invalid"; row: number; stat: Stat }
  | { kind: "twice"; row: number };

export const problemOf = (
  rows: readonly Row[],
  stats: readonly Stat[]
): Problem | null => {
  const seen = new Set<string>();
  let used = 0;

  for (const [index, row] of rows.entries()) {
    if (isUsed(row)) {
      used += 1;

      const who =
        row.player.kind === "known"
          ? row.player.playerId
          : `new:${row.player.kind === "new" ? row.player.riotId.trim().toLowerCase() : ""}`;

      if (seen.has(who)) {
        return { kind: "twice", row: index + 1 };
      }

      seen.add(who);

      const bad = stats.find(
        (stat) => parseStat(stat, row.stats[stat]) === "invalid"
      );

      if (bad) {
        return { kind: "invalid", row: index + 1, stat: bad };
      }
    }
  }

  return used === 0 ? { kind: "empty" } : null;
};

/** The numbers a row holds, for the request; empty boxes are left out. */
export const statsOf = (
  row: Row,
  stats: readonly Stat[]
): Partial<Record<Stat, number>> => {
  const values: Partial<Record<Stat, number>> = {};

  for (const stat of stats) {
    const value = parseStat(stat, row.stats[stat]);

    if (value !== null && value !== "invalid") {
      values[stat] = value;
    }
  }

  return values;
};

/** The lines to send: the rows that name a player, in order. Check `problemOf` first. */
export const linesOf = (
  rows: readonly Row[],
  stats: readonly Stat[],
  manualCodes: ReadonlySet<string>
): LineInput[] =>
  rows.flatMap((row) =>
    isUsed(row)
      ? [
          {
            manualBonuses: row.bonuses.filter((code) => manualCodes.has(code)),
            playerId: row.player.kind === "known" ? row.player.playerId : null,
            riotId: row.player.kind === "new" ? row.player.riotId.trim() : null,
            stats: statsOf(row, stats),
            win: row.win,
          },
        ]
      : []
  );
