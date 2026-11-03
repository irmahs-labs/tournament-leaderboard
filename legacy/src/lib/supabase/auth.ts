import { cookies } from 'next/headers';
import type { User } from '@supabase/supabase-js';
import { createClient } from './server';

/**
 * Returns the signed-in user only if their `profiles.is_admin` flag is set,
 * otherwise null. Use in Server Components / actions to gate the admin area.
 */
export async function getAdminUser(): Promise<User | null> {
  const supabase = createClient(await cookies());

  const { data: { user } } = await supabase.auth.getUser();
  if (!user) return null;

  const { data: profile } = await supabase
    .from('profiles')
    .select('is_admin')
    .eq('id', user.id)
    .single();

  return profile?.is_admin ? user : null;
}
