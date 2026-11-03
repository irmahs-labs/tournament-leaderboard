import { cookies } from 'next/headers';
import { createClient } from '@/lib/supabase/server';
import type { Game } from '@/types';

export async function getGames(matchId: number): Promise<{ games: Game[]; error: string | null }> {
  const supabase = createClient(await cookies());
  const { data, error } = await supabase
    .from('games')
    .select('id, match_id, game_number')
    .eq('match_id', matchId)
    .order('game_number');
  return {
    games: (data ?? []) as Game[],
    error: error?.message ?? null,
  };
}

/** How many games each of the given matches has recorded, plus the grand total. */
export async function getGameCounts(
  matchIds: number[],
): Promise<{ byMatch: Map<number, number>; total: number }> {
  if (matchIds.length === 0) return { byMatch: new Map(), total: 0 };
  const supabase = createClient(await cookies());
  const { data } = await supabase.from('games').select('match_id').in('match_id', matchIds);

  const byMatch = new Map<number, number>();
  for (const row of data ?? []) {
    byMatch.set(row.match_id, (byMatch.get(row.match_id) ?? 0) + 1);
  }
  let total = 0;
  for (const n of byMatch.values()) total += n;
  return { byMatch, total };
}
