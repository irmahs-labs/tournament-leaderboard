import { redirect } from 'next/navigation';
import { getAdminUser } from '@/lib/supabase/auth';
import { getPlayers } from '@/services/players';
import { getRanks } from '@/services/ranks';
import { getDictionary } from '@/i18n/dictionary';
import { getLocale } from '@/i18n/locale';
import AdminNav from '@/components/AdminNav';
import PlayerManager from '@/components/PlayerManager';

export default async function AdminPlayersPage() {
  const user = await getAdminUser();
  if (!user) redirect('/');

  const [{ players, error: playersError }, { ranks }, locale] = await Promise.all([
    getPlayers(),
    getRanks(),
    getLocale(),
  ]);
  const t = getDictionary(locale);

  return (
    <div>
      <div className="px-7 py-8 border-b border-[var(--border)]">
        <div className="mb-1.5 text-[11px] font-semibold uppercase tracking-[.14em] text-[var(--accent)]">
          {t.admin.overline}
        </div>
        <h1 className="font-display text-[28px] font-bold leading-none tracking-[.02em] text-[var(--text-strong)]">
          {t.adminPlayers.pageTitle}
        </h1>
      </div>

      <div className="px-7 py-7">
        <AdminNav current="players" locale={locale} />

        <div className="rounded-b-xl rounded-tr-xl border border-[var(--border)] bg-[var(--surface)] p-7">
          {playersError ? (
            <p className="rounded-md border border-[var(--accent-33)] bg-[var(--accent-0f)] px-3 py-2 text-[12px] text-[var(--accent)]">
              {t.adminForm.playersErrorPrefix}
              {playersError}
            </p>
          ) : (
            <PlayerManager players={players} ranks={ranks} locale={locale} />
          )}
        </div>
      </div>
    </div>
  );
}
