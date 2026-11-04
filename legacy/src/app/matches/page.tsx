import Link from 'next/link';
import { getMatches } from '@/services/matches';
import { getDictionary } from '@/i18n/dictionary';
import { getLocale } from '@/i18n/locale';
import type { Match } from '@/types';

export default async function Page() {
  const locale = await getLocale();
  const t = getDictionary(locale);
  const { matches, error } = await getMatches();

  const season = matches.reduce((max, m) => Math.max(max, m.match_season ?? 0), 0) || null;
  const seasonMatches = matches.filter((m: Match) => m.match_season === season);

  return (
    <div>
      <div className="px-7 py-8 border-b border-[var(--border)]">
        <div className="text-[11px] font-semibold text-[var(--accent)] tracking-[.14em] uppercase mb-1.5">
          {season ? t.season(season) : t.seasonDash}
        </div>
        <div className="font-display font-bold text-[32px] text-[var(--text-strong)] leading-none tracking-[.02em]">
          {t.matchesPage.title}
        </div>
      </div>

      <div className="px-7 py-6">
        {error ? (
          <p className="text-[var(--accent)] text-sm">{error}</p>
        ) : (
          <div className="grid grid-cols-3 max-[768px]:grid-cols-1 gap-3">
            {seasonMatches.map((m: Match) => {
              const done = m.match_date !== null;
              return (
                <Link
                  key={m.id}
                  href={`/matches/${m.match_season ?? season}/${m.id}`}
                  className="block bg-[var(--surface)] border border-[var(--border)] hover:border-[var(--border-strong)] rounded-lg px-4 py-3.5 transition-colors"
                >
                  <div className="flex items-center justify-between mb-2.5">
                    <span className="font-display font-bold text-[14px] text-[var(--text-strong)] tracking-[.04em]">
                      {t.match(m.match_number)}
                    </span>
                    <span className={`text-[10px] font-semibold tracking-[.08em] uppercase px-2 py-0.5 rounded-sm ${
                      done
                        ? 'text-[var(--green)] bg-[var(--green-12)] border border-[var(--green-22)]'
                        : 'text-[var(--text-muted)] bg-[var(--border)] border border-[var(--border-strong)]'
                    }`}>
                      {done ? t.matchesPage.done : t.matchesPage.upcoming}
                    </span>
                  </div>
                  <div className="flex gap-1 mb-2">
                    {[0, 1, 2].map((g) => (
                      <div key={g} className={`flex-1 h-[3px] rounded-sm ${done ? 'bg-[var(--accent)]' : 'bg-[var(--border)]'}`} />
                    ))}
                  </div>
                  <div className="text-[11px] text-[var(--text-faint)]">{m.match_date ?? t.matchesPage.tbd}</div>
                </Link>
              );
            })}
            {seasonMatches.length === 0 && (
              <p className="text-[13px] text-[var(--text-muted)]">{t.matchesPage.noMatches}</p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
