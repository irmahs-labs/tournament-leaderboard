import { redirect } from 'next/navigation';
import Link from 'next/link';
import { IconSwords, IconSkull, IconHeartHandshake, IconBomb, IconHammer, IconTrophy } from '@tabler/icons-react';
import { getMatches } from '@/services/matches';
import { getGameCounts } from '@/services/games';
import { getPlayerTotals } from '@/services/matchScores';
import { getDictionary } from '@/i18n/dictionary';
import { getLocale } from '@/i18n/locale';
import type { Match } from '@/types';

const statThClass =
  'text-[9px] font-semibold text-[var(--border-strong)] uppercase tracking-[.08em] pb-2 px-1.5 text-center border-b border-[var(--border)] whitespace-nowrap';

export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ tab?: string; currentSeason?: string }>;
}) {
  const { tab, currentSeason: currentSeasonParam } = await searchParams;
  const activeTab = tab ?? 'all';

  const locale = await getLocale();
  const t = getDictionary(locale);

  const { matches } = await getMatches();
  const maxSeason = matches.reduce((max: number, m: Match) => Math.max(max, m.match_season ?? 0), 0) || null;

  if (!currentSeasonParam && maxSeason) {
    const params = new URLSearchParams({ currentSeason: String(maxSeason) });
    if (tab) params.set('tab', tab);
    redirect(`/?${params.toString()}`);
  }

  const currentSeason = currentSeasonParam ? parseInt(currentSeasonParam) : maxSeason;
  const seasonMatches = matches.filter((m: Match) => m.match_season === currentSeason);

  const { byMatch: gamesByMatch, total: gamesTotal } = await getGameCounts(
    seasonMatches.map((m: Match) => m.id),
  );
  // Only matches that actually have games recorded.
  const playedMatches = seasonMatches.filter((m: Match) => (gamesByMatch.get(m.id) ?? 0) > 0);

  const activeMatch = playedMatches.find((m: Match) => String(m.match_number) === activeTab) ?? null;

  const [{ totals: seasonTotals }, perMatch] = await Promise.all([
    getPlayerTotals(playedMatches.map((m: Match) => m.id)),
    activeMatch ? getPlayerTotals([activeMatch.id]) : Promise.resolve(null),
  ]);
  const mvp = seasonTotals[0] ?? null;
  const runnersUp = seasonTotals.slice(1, 3); // silver, bronze
  const matchTotals = perMatch ? perMatch.totals : seasonTotals;
  const totalsError = perMatch ? perMatch.error : null;

  return (
    <div>
      {/* Hero */}
      <div className="flex items-start justify-between flex-wrap gap-4 px-7 py-8 border-b border-[var(--border)]">
        <div>
          <div className="text-[11px] font-semibold text-[var(--accent)] tracking-[.14em] uppercase mb-1.5">
            {currentSeason ? t.season(currentSeason) : t.seasonDash}
          </div>
          <div className="font-display font-bold text-[32px] text-[var(--text-strong)] leading-none tracking-[.02em]">
            {t.home.title}
          </div>
          <div className="text-[13px] text-[var(--text-muted)] mt-2">
            {t.home.cumulativeAllGames}
          </div>
        </div>

        <div className="flex gap-4 items-center">
          <div className="text-right">
            <div className="font-display font-bold text-[26px] text-[var(--text-strong)] leading-none">
              {playedMatches.length || '—'}
            </div>
            <div className="text-[11px] text-[var(--text-muted)] mt-1 tracking-[.06em] uppercase">{t.home.matchesPlayed}</div>
          </div>
          <div className="w-px bg-[var(--border)] self-stretch" />
          <div className="text-right">
            <div className="font-display font-bold text-[26px] text-[var(--text-strong)] leading-none">{gamesTotal || '—'}</div>
            <div className="text-[11px] text-[var(--text-muted)] mt-1 tracking-[.06em] uppercase">{t.home.gamesTotal}</div>
          </div>
        </div>
      </div>

      {/* Podium */}
      {mvp && (
        <div className="flex flex-wrap gap-3 px-7 py-5 border-b border-[var(--border)]">
          {/* MVP */}
          <div className="flex flex-1 min-w-[280px] flex-wrap items-center gap-4 rounded-xl border border-[var(--gold-30)] bg-[var(--gold-tint)] px-5 py-4">
            <IconTrophy size={28} className="shrink-0 text-[var(--gold)]" />
            <div>
              <div className="text-[10px] font-semibold uppercase tracking-[.14em] text-[var(--gold)]">
                {currentSeason ? t.home.seasonMvp(currentSeason) : t.home.mvp}
              </div>
              <div className="mt-1 font-display text-[22px] font-bold leading-none text-[var(--text-strong)]">{mvp.username}</div>
              <div className="mt-1 text-[11px] text-[var(--text-muted)]">{t.home.mvpSubtitle}</div>
            </div>
            <div className="ml-auto flex gap-5">
              {([
                [t.home.points, mvp.total, true],
                [t.home.kills, mvp.kills, false],
                ['ACS', mvp.acs, false],
                [t.home.wins, mvp.wins, false],
              ] as const).map(([lbl, val, gold]) => (
                <div key={lbl} className="text-right">
                  <div
                    className={`font-display text-[20px] font-bold leading-none ${gold ? 'text-[var(--gold)]' : 'text-[var(--text-strong)]'}`}
                  >
                    {val}
                  </div>
                  <div className="mt-1 text-[10px] uppercase tracking-[.06em] text-[var(--text-muted)]">{lbl}</div>
                </div>
              ))}
            </div>
          </div>

          {/* Silver / bronze */}
          {runnersUp.map((p, i) => {
            const meta = i === 0
              ? { label: t.home.second, text: 'text-[var(--silver)]', border: 'border-[var(--silver-26)]', bg: 'bg-[var(--silver-tint)]' }
              : { label: t.home.third, text: 'text-[var(--bronze)]', border: 'border-[var(--bronze-26)]', bg: 'bg-[var(--bronze-tint)]' };
            return (
              <div
                key={p.player_id}
                className={`flex w-[170px] flex-col justify-center rounded-xl border px-4 py-4 ${meta.border} ${meta.bg}`}
              >
                <div className={`text-[10px] font-semibold uppercase tracking-[.14em] ${meta.text}`}>{meta.label}</div>
                <div className="mt-1 font-display text-[16px] font-bold leading-tight text-[var(--text-strong)]">{p.username}</div>
                <div className="mt-1.5 flex items-baseline gap-1">
                  <span className="font-display text-[18px] font-bold text-[var(--gold)]">{p.total}</span>
                  <span className="text-[10px] uppercase tracking-[.06em] text-[var(--text-muted)]">{t.home.pts}</span>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Match tabs */}
      <div className="flex px-7 border-b border-[var(--border)] overflow-x-auto">
        <Link
          href={`/?currentSeason=${currentSeason}`}
          className={`text-[12px] font-medium px-[18px] py-3 border-b-2 whitespace-nowrap tracking-[.04em] ${
            activeTab === 'all' ? 'text-[var(--accent)] border-[var(--accent)]' : 'text-[var(--text-muted)] border-transparent hover:text-[var(--text-subtle)]'
          }`}
        >
          {t.home.allMatches}
        </Link>
        {playedMatches.map((m: Match) => (
          <Link
            key={m.id}
            href={`/?currentSeason=${currentSeason}&tab=${m.match_number}&matchId=${m.id}&matchSeason=${m.match_season ?? currentSeason}`}
            className={`text-[12px] font-medium px-[18px] py-3 border-b-2 whitespace-nowrap tracking-[.04em] ${
              activeTab === String(m.match_number) ? 'text-[var(--accent)] border-[var(--accent)]' : 'text-[var(--text-muted)] border-transparent hover:text-[var(--text-subtle)]'
            }`}
          >
            {t.match(m.match_number)}
          </Link>
        ))}
      </div>

      {/* Leaderboard table */}
      <div className="px-7 py-6">
          <div className="flex items-center gap-3 mb-3.5">
            <div className="font-display text-[13px] font-semibold tracking-[.1em] uppercase text-[var(--text-muted)]">
              {activeMatch ? t.match(activeMatch.match_number) : t.home.globalLeaderboard}
            </div>
            {activeMatch && (
              <Link
                href={`/matches/${activeMatch.match_season ?? currentSeason}/${activeMatch.id}`}
                className="text-[11px] font-medium text-[var(--accent)] hover:text-[var(--text-strong)] tracking-[.04em] transition-colors"
              >
                {t.home.matchDetail}
              </Link>
            )}
          </div>
          {totalsError ? (
            <p className="text-[var(--accent)] text-sm">{totalsError}</p>
          ) : (
            <div className="overflow-x-auto">
              <div className="text-[11px] text-[var(--text-faint)] mb-2.5">{t.home.cumulativeAllGames}</div>
              <table className="border-collapse min-w-[720px] w-full">
                <thead>
                  <tr>
                    <th className={`${statThClass} text-left`} colSpan={2}></th>
                    <th className={`${statThClass}`} colSpan={8}>{t.home.statistiques}</th>
                    <th className={`${statThClass} border-l border-[var(--border)]`} colSpan={6}>{t.home.bonuses}</th>
                    <th className={`${statThClass} border-l border-[var(--border)]`}>{t.home.total}</th>
                  </tr>
                  <tr className="[&>th]:pt-2">
                    <th className={`${statThClass} text-left w-6`}>#</th>
                    <th className={`${statThClass} text-left w-36 pl-0`}>{t.home.player}</th>
                    {['ACS', 'K', 'D', 'A', 'Eco', 'FB', 'Pl.', 'Def.'].map((h) => (
                      <th key={h} className={`${statThClass} w-9`}>{h}</th>
                    ))}
                    <th className={`${statThClass} w-8 border-l border-[var(--border)]`}><IconSwords size={12} className="mx-auto" /></th>
                    <th className={`${statThClass} w-8`}><IconSkull size={12} className="mx-auto" /></th>
                    <th className={`${statThClass} w-8`}><IconHeartHandshake size={12} className="mx-auto" /></th>
                    <th className={`${statThClass} w-8`}><IconBomb size={12} className="mx-auto" /></th>
                    <th className={`${statThClass} w-8`}><IconHammer size={12} className="mx-auto" /></th>
                    <th className={`${statThClass} w-8`}><IconTrophy size={12} className="mx-auto" /></th>
                    <th className={`${statThClass} w-12 border-l border-[var(--border)]`}>Pts</th>
                  </tr>
                </thead>
                <tbody>
                  {matchTotals.map((p, i) => (
                    <tr key={p.player_id} className="border-b border-[var(--surface)] hover:bg-[var(--surface)]">
                      <td className="py-2 px-1.5 text-left">
                        <span className={`font-display font-bold text-[13px] w-5 inline-block text-center ${['text-[var(--gold)]', 'text-[var(--silver)]', 'text-[var(--bronze)]'][i] ?? 'text-[var(--border-strong)]'}`}>
                          {i + 1}
                        </span>
                      </td>
                      <td className="py-2 pl-0 pr-1.5 text-left text-[12px] font-medium text-[var(--text-secondary)]">{p.username}</td>
                      <td className="py-2 px-1.5 text-center text-[12px] font-semibold font-display text-[var(--text)]">{p.acs}</td>
                      <td className="py-2 px-1.5 text-center text-[12px] font-semibold font-display text-[var(--text)]">{p.kills}</td>
                      <td className="py-2 px-1.5 text-center text-[12px] text-[var(--text-faint)]">{p.deaths}</td>
                      <td className="py-2 px-1.5 text-center text-[12px] font-semibold font-display text-[var(--text)]">{p.assists}</td>
                      <td className="py-2 px-1.5 text-center text-[12px] text-[var(--text-subtle)]">{p.econ_rating}</td>
                      <td className="py-2 px-1.5 text-center text-[12px] font-semibold font-display text-[var(--text)]">{p.first_bloods}</td>
                      <td className="py-2 px-1.5 text-center text-[12px] text-[var(--text-subtle)]">{p.plants}</td>
                      <td className="py-2 px-1.5 text-center text-[12px] text-[var(--text-subtle)]">{p.defuses}</td>
                      {[p.bonus_fb, p.bonus_death, p.bonus_assist, p.bonus_plant, p.bonus_defuse].map((count, idx) => (
                        <td key={idx} className={`py-2 px-1.5 text-center text-[12px] font-semibold font-display ${idx === 0 ? 'border-l border-[var(--border)]' : ''} ${count > 0 ? 'text-[var(--gold)]' : 'text-[var(--border)]'}`}>
                          {count > 0 ? count : '—'}
                        </td>
                      ))}
                      <td className={`py-2 px-1.5 text-center text-[12px] font-semibold font-display ${p.wins > 0 ? 'text-[var(--green)]' : 'text-[var(--border)]'}`}>
                        {p.wins > 0 ? p.wins : '—'}
                      </td>
                      <td className="py-2 px-1.5 text-center border-l border-[var(--border)] text-[12px] font-bold font-display text-[var(--text-strong)]">
                        {p.total}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
      </div>
    </div>
  );
}
