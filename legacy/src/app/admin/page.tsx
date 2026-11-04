import { redirect } from 'next/navigation';
import { cookies } from 'next/headers';
import { createClient } from '@/lib/supabase/server';
import { getAdminUser } from '@/lib/supabase/auth';
import { getMatches } from '@/services/matches';
import { getPlayers } from '@/services/players';
import { getDictionary } from '@/i18n/dictionary';
import { getLocale } from '@/i18n/locale';
import AdminNav from '@/components/AdminNav';
import ScoreEntry from '@/components/ScoreEntry';

export default async function AdminPage() {
  const user = await getAdminUser();
  if (!user) redirect('/');

  const [{ matches }, { players, error: playersError }, locale] = await Promise.all([
    getMatches(),
    getPlayers(),
    getLocale(),
  ]);
  const t = getDictionary(locale);

  async function signOut() {
    'use server';
    const supabase = createClient(await cookies());
    await supabase.auth.signOut();
    redirect('/');
  }

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-4 px-7 py-8 border-b border-[var(--border)]">
        <div>
          <div className="mb-1.5 text-[11px] font-semibold uppercase tracking-[.14em] text-[var(--accent)]">{t.admin.overline}</div>
          <h1 className="font-display text-[28px] font-bold leading-none tracking-[.02em] text-[var(--text-strong)]">
            {t.admin.pageTitle}
          </h1>
          <div className="mt-2 text-[13px] text-[var(--text-muted)]">{t.admin.signedInAs(user.email ?? '')}</div>
        </div>

        <form action={signOut}>
          <button
            type="submit"
            className="rounded-md border border-[var(--border)] px-3.5 py-2 text-[12px] font-semibold uppercase tracking-[.04em] text-[var(--text-subtle)] transition-colors hover:border-[var(--border-strong)] hover:text-[var(--text-strong)]"
          >
            {t.admin.signOut}
          </button>
        </form>
      </div>

      <div className="px-7 py-7">
        <AdminNav current="score" locale={locale} />

        <div className="rounded-b-xl rounded-tr-xl border border-[var(--border)] bg-[var(--surface)] p-7">
          <ScoreEntry matches={matches} players={players} playersError={playersError} locale={locale} />
        </div>
      </div>
    </div>
  );
}
