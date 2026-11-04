import { cookies } from 'next/headers';
import { createClient } from '@/lib/supabase/server';
import type { Match } from '@/types';

export async function getMatchById(matchId: number): Promise<{ match: Match | null; error: string | null }> {
  const supabase = createClient(await cookies());
  const { data, error } = await supabase
    .from('matches')
    .select('id, match_number, match_date, match_season')
    .eq('id', matchId)
    .single();
  return { match: (data ?? null) as Match | null, error: error?.message ?? null };
}

export async function getMatches(): Promise<{ matches: Match[]; error: string | null }> {
  const supabase = createClient(await cookies());
  const { data, error } = await supabase
    .from('matches')
    .select('id, match_number, match_date, match_season')
    .order('match_number');
  return {
    matches: (data ?? []) as Match[],
    error: error?.message ?? null,
  };
}
