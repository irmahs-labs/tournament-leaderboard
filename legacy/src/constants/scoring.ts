export const POINTS_PER_BONUS = 10;
export const POINTS_PER_WIN = 50;

/** The five per-game bonus flags, in display order. */
export const BONUS_KEYS = ['bonus_fb', 'bonus_death', 'bonus_assist', 'bonus_plant', 'bonus_defuse'] as const;
export type BonusKey = (typeof BONUS_KEYS)[number];

export type ScoreInput = {
  acs: number;
  kills: number;
  deaths: number;
  assists: number;
  econ_rating: number;
  first_bloods: number;
  plants: number;
  defuses: number;
  /** number of bonuses earned (0–5 for one game, more when aggregated) */
  bonus_count: number;
  /** games won (0 or 1 for a single game) */
  wins: number;
};

/**
 * The single source of truth for a player's point total.
 *   acs + kills + assists + econ_rating + first_bloods + plants + defuses
 *   + bonus_count * POINTS_PER_BONUS - deaths + wins * POINTS_PER_WIN
 */
export function computeScore(s: ScoreInput): number {
  return (
    s.acs +
    s.kills +
    s.assists +
    s.econ_rating +
    s.first_bloods +
    s.plants +
    s.defuses +
    s.bonus_count * POINTS_PER_BONUS -
    s.deaths +
    s.wins * POINTS_PER_WIN
  );
}
