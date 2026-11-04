import { cookies } from 'next/headers';
import { createClient } from '@/lib/supabase/server';
import type { GameStat } from '@/types';

type StatWithUsername = GameStat & { username: string };

export async function getGameStatsBySeasonByMatchByGame(
  matchId: number,
  gameNumber: number,
): Promise<{ stats: StatWithUsername[]; error: string | null }> {
  const supabase = createClient(await cookies());

  const { data: gameData, error: gameError } = await supabase
    .from('games')
    .select('id')
    .eq('match_id', matchId)
    .eq('game_number', gameNumber)
    .single();

  if (gameError || !gameData) {
    return { stats: [], error: gameError?.message ?? 'Game not found' };
  }

  const { data, error } = await supabase
    .from('game_stats')
    .select('*, players!inner(username)')
    .eq('game_id', gameData.id)
    .order('id');

  const stats: StatWithUsername[] = (data ?? []).map((row: any) => ({
    ...row,
    username: row.players?.username ?? '—',
    players: undefined,
  }));

  return { stats, error: error?.message ?? null };
}
