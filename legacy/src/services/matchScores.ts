import { cookies } from 'next/headers';
import { createClient } from '@/lib/supabase/server';
import { POINTS_PER_BONUS, POINTS_PER_WIN } from '@/constants/scoring';

export type MatchPlayerTotal = {
  player_id: string;
  username: string;
  acs: number;
  kills: number;
  deaths: number;
  assists: number;
  econ_rating: number;
  first_bloods: number;
  plants: number;
  defuses: number;
  wins: number;
  bonus_fb: number;
  bonus_death: number;
  bonus_assist: number;
  bonus_plant: number;
  bonus_defuse: number;
  bonus_count: number;
  total: number;
};

/**
 * Aggregates every player's stats + total score across the games of the given match(es).
 * Same scoring formula as the match detail / per-game pages:
 *   acs + kills + assists + econ_rating + first_bloods + plants + defuses
 *   + bonus_count * POINTS_PER_BONUS - deaths + wins * POINTS_PER_WIN
 *
 * Pass one match id for a single-match table, or many for a season-wide leaderboard.
 */
export async function getPlayerTotals(
  matchIds: number[],
): Promise<{ totals: MatchPlayerTotal[]; error: string | null }> {
  if (matchIds.length === 0) return { totals: [], error: null };

  const supabase = createClient(await cookies());

  const { data: games, error: gamesError } = await supabase
    .from('games')
    .select('id')
    .in('match_id', matchIds);

  if (gamesError) return { totals: [], error: gamesError.message };

  const gameIds = (games ?? []).map((g) => g.id);
  if (gameIds.length === 0) return { totals: [], error: null };

  const { data, error } = await supabase
    .from('game_stats')
    .select('*, players!inner(username)')
    .in('game_id', gameIds);

  if (error) return { totals: [], error: error.message };

  const byPlayer = new Map<string, MatchPlayerTotal>();
  for (const row of (data ?? []) as any[]) {
    let e = byPlayer.get(row.player_id);
    if (!e) {
      e = {
        player_id: row.player_id,
        username: row.players?.username ?? '—',
        acs: 0, kills: 0, deaths: 0, assists: 0, econ_rating: 0,
        first_bloods: 0, plants: 0, defuses: 0, wins: 0,
        bonus_fb: 0, bonus_death: 0, bonus_assist: 0, bonus_plant: 0, bonus_defuse: 0,
        bonus_count: 0, total: 0,
      };
      byPlayer.set(row.player_id, e);
    }
    e.acs += row.acs;
    e.kills += row.kills;
    e.deaths += row.deaths;
    e.assists += row.assists;
    e.econ_rating += row.econ_rating;
    e.first_bloods += row.first_bloods;
    e.plants += row.plants;
    e.defuses += row.defuses;
    e.wins += row.win ? 1 : 0;
    e.bonus_fb += row.bonus_fb ? 1 : 0;
    e.bonus_death += row.bonus_death ? 1 : 0;
    e.bonus_assist += row.bonus_assist ? 1 : 0;
    e.bonus_plant += row.bonus_plant ? 1 : 0;
    e.bonus_defuse += row.bonus_defuse ? 1 : 0;
  }

  const totals = [...byPlayer.values()].map((e) => {
    e.bonus_count = e.bonus_fb + e.bonus_death + e.bonus_assist + e.bonus_plant + e.bonus_defuse;
    e.total =
      e.acs + e.kills + e.assists + e.econ_rating + e.first_bloods + e.plants + e.defuses +
      e.bonus_count * POINTS_PER_BONUS - e.deaths + e.wins * POINTS_PER_WIN;
    return e;
  }).sort((a, b) => b.total - a.total);

  return { totals, error: null };
}

export function getMatchPlayerTotals(matchId: number) {
  return getPlayerTotals([matchId]);
}
