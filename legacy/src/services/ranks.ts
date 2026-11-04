import { cookies } from 'next/headers';
import { createClient } from '@/lib/supabase/server';

export type RankRef = {
  id: number;
  rank_name: string;
};

/** Ranks are reference data (used to populate the rank dropdown on /admin/players). */
export async function getRanks(): Promise<{ ranks: RankRef[]; error: string | null }> {
  const supabase = createClient(await cookies());
  const { data, error } = await supabase
    .from('ranks')
    .select('id, rank_name')
    .order('rank_value', { ascending: true });
  return {
    ranks: (data ?? []) as RankRef[],
    error: error?.message ?? null,
  };
}
