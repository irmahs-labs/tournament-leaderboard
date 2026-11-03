import Link from 'next/link';
import { IconSwords, IconSkull, IconHeartHandshake, IconBomb, IconHammer, IconTrophy, IconChevronRight } from '@tabler/icons-react';
import { getGames } from '@/services/games';
import { getMatchById } from '@/services/matches';
import { getPlayerTotals } from '@/services/matchScores';
import { getDictionary } from '@/i18n/dictionary';
import { getLocale } from '@/i18n/locale';
import StatLeaders from '@/components/StatLeaders';

const rankColors = ['text-[var(--gold)]', 'text-[var(--silver)]', 'text-[var(--bronze)]'];

const thClass =
  'text-[9px] font-semibold text-[var(--border-strong)] uppercase tracking-[.08em] pb-2 px-1.5 text-center border-b border-[var(--border)] whitespace-nowrap';

export default async function Page({
  params,
}: {
  params: Promise<{ season: string; matchId: string }>;
}) {
  const { season, matchId: matchIdStr } = await params;
  const matchSeason = parseInt(season);
  const matchId = parseInt(matchIdStr);
  const locale = await getLocale();
  const t = getDictionary(locale);

  const [{ games }, { match }, { totals: displayPlayers }] = await Promise.all([
    getGames(matchId),
    getMatchById(matchId),
    getPlayerTotals([matchId]),
  ]);

  const matchNumber = match?.match_number ?? matchId;
  const basePath = `/matches/${matchSeason}/${matchId}`;

  const mvp = displayPlayers[0] ?? null;
  const leaderName = (
    key: 'kills' | 'assists' | 'deaths' | 'first_bloods' | 'plants' | 'defuses',
    dir: 'max' | 'min',
  ) => {
    if (displayPlayers.length === 0) return '—';
    return displayPlayers.reduce((best, p) =>
      dir === 'max' ? (p[key] > best[key] ? p : best) : p[key] < best[key] ? p : best,
    ).username;
  };
  const categories = [
    { label: t.categories.leastDeaths, name: leaderName('deaths', 'min') },
    { label: t.categories.mostKills, name: leaderName('kills', 'max') },
    { label: t.categories.mostAssists, name: leaderName('assists', 'max') },
    { label: t.categories.mostFirstBloods, name: leaderName('first_bloods', 'max') },
    { label: t.categories.mostPlants, name: leaderName('plants', 'max') },
    { label: t.categories.mostDefuses, name: leaderName('defuses', 'max') },
  ];

  return (
    <div>
      {/* Breadcrumb */}
      <div className="flex items-center gap-2 px-6 py-2.5 border-b border-[var(--border)] text-[12px] text-[var(--text-faint)]">
        <Link href="/" className="hover:text-[var(--text-secondary)] transition-colors">{t.nav.leaderboard}</Link>
        <IconChevronRight size={11} />
        <span className="text-[var(--text-secondary)]">{t.match(matchNumber)}</span>
      </div>

      {/* Header */}
      <div className="px-6 pt-5 pb-4 border-b border-[var(--border)]">
        <div className="text-[10px] font-semibold text-[var(--accent)] tracking-[.14em] uppercase mb-1">
          {t.match(matchNumber)}
        </div>
        <div className="font-display font-bold text-[24px] text-[var(--text-strong)] tracking-[.02em]">
          {t.match(matchNumber)}
        </div>
        <div className="flex items-center gap-3 text-[11px] text-[var(--text-faint)] mt-1">
          {games.length > 0 && <span>{t.gamesPlayed(games.length)}</span>}
        </div>
      </div>

      {/* Most Valuable Player */}
      <div className="px-6 py-5 border-b border-[var(--border)]">
        {mvp ? (
          <div className="flex flex-wrap items-center gap-4 rounded-xl border border-[var(--gold-30)] bg-[var(--gold-tint)] px-5 py-4">
            <IconTrophy size={28} className="shrink-0 text-[var(--gold)]" />
            <div>
              <div className="text-[10px] font-semibold uppercase tracking-[.14em] text-[var(--gold)]">
                {t.matchDetail.matchMvpTitle(matchNumber)}
              </div>
              <div className="mt-1 font-display text-[22px] font-bold leading-none text-[var(--text-strong)]">{mvp.username}</div>
            </div>
            <div className="ml-auto flex gap-5">
              {([
                [t.home.points, mvp.total, true],
                [t.matchDetail.kda, `${mvp.kills} / ${mvp.deaths} / ${mvp.assists}`, false],
                [t.matchDetail.acs, mvp.acs, false],
                [t.home.wins, mvp.wins, false],
              ] as const).map(([lbl, val, gold]) => (
                <div key={lbl} className="text-right">
                  <div className={`font-display text-[18px] font-bold leading-none ${gold ? 'text-[var(--gold)]' : 'text-[var(--text-strong)]'}`}>
                    {val}
                  </div>
                  <div className="mt-1 text-[10px] uppercase tracking-[.06em] text-[var(--text-muted)]">{lbl}</div>
                </div>
              ))}
            </div>
          </div>
        ) : (
          <p className="text-[var(--text-muted)] text-sm">{t.matchDetail.noStats}</p>
        )}
      </div>

      {/* Category leaders */}
      {mvp && <StatLeaders items={categories} />}

      {/* Game tabs */}
      <div className="flex px-6 border-b border-[var(--border)] overflow-x-auto">
        <Link
          href={basePath}
          className="text-[12px] font-medium px-4 py-3 border-b-2 whitespace-nowrap tracking-[.03em] text-[var(--accent)] border-[var(--accent)]"
        >
          {t.matchDetail.allGamesTotal}
        </Link>
        {games.map((g) => (
          <Link
            key={g.id}
            href={`${basePath}/${g.game_number}`}
            className="text-[12px] font-medium px-4 py-3 border-b-2 whitespace-nowrap tracking-[.03em] text-[var(--text-muted)] border-transparent hover:text-[var(--text-subtle)]"
          >
            {t.game(g.game_number)}
          </Link>
        ))}
      </div>

      <div className="px-6 pt-4 pb-8">
        <div className="font-display text-[11px] font-semibold tracking-[.1em] uppercase text-[var(--text-faint)] mb-2.5">
          {t.home.cumulativeAllGames}
        </div>

        {displayPlayers.length === 0 ? (
          <p className="text-[var(--text-muted)] text-sm">{t.matchDetail.noStats}</p>
        ) : (
          <>
            <div className="flex flex-wrap gap-x-5 gap-y-2 mb-3.5 px-3.5 py-2.5 bg-[var(--surface)] border border-[var(--border)] rounded-lg">
              {[
                { Icon: IconSwords, label: t.matchDetail.firstBloodBonus, green: false },
                { Icon: IconSkull, label: t.matchDetail.leastDeathsBonus, green: false },
                { Icon: IconHeartHandshake, label: t.matchDetail.mostAssistsBonus, green: false },
                { Icon: IconBomb, label: t.matchDetail.mostPlantsBonus, green: false },
                { Icon: IconHammer, label: t.matchDetail.mostDefusesBonus, green: false },
                { Icon: IconTrophy, label: t.matchDetail.victory, green: true },
              ].map(({ Icon, label, green }) => (
                <div key={label} className="flex items-center gap-1.5 text-[11px] text-[var(--text-muted)]">
                  <Icon size={14} className={green ? 'text-[var(--green)]' : 'text-[var(--gold)]'} />
                  {label}
                </div>
              ))}
            </div>
            <div className="overflow-x-auto">
            <table className="border-collapse min-w-[820px] w-full">
              <thead>
                <tr>
                  <th className={`${thClass} text-left`} colSpan={2}></th>
                  <th className={`${thClass} border-b border-[var(--border)]`} colSpan={8}>{t.home.statistiques}</th>
                  <th className={`${thClass} border-l border-[var(--border)]`} colSpan={6}>{t.home.bonuses}</th>
                  <th className={`${thClass} border-l border-[var(--border)]`}>{t.home.total}</th>
                </tr>
                <tr className="[&>th]:pt-2">
                  <th className={`${thClass} text-left w-6`}>#</th>
                  <th className={`${thClass} text-left w-36 pl-0`}>{t.home.player}</th>
                  {['ACS', 'K', 'D', 'A', 'Eco', 'FB', 'Pl.', 'Def.'].map((h) => (
                    <th key={h} className={`${thClass} w-9`}>{h}</th>
                  ))}
                  <th className={`${thClass} w-8 border-l border-[var(--border)]`}><IconSwords size={12} className="mx-auto" /></th>
                  <th className={`${thClass} w-8`}><IconSkull size={12} className="mx-auto" /></th>
                  <th className={`${thClass} w-8`}><IconHeartHandshake size={12} className="mx-auto" /></th>
                  <th className={`${thClass} w-8`}><IconBomb size={12} className="mx-auto" /></th>
                  <th className={`${thClass} w-8`}><IconHammer size={12} className="mx-auto" /></th>
                  <th className={`${thClass} w-8`}><IconTrophy size={12} className="mx-auto" /></th>
                  <th className={`${thClass} w-12 border-l border-[var(--border)]`}>Pts</th>
                </tr>
              </thead>
              <tbody>
                {displayPlayers.map((player, i) => (
                  <tr key={player.player_id} className="border-b border-[var(--surface)] hover:bg-[var(--surface)]">
                    <td className="py-2 px-1.5 text-left">
                      <span className={`font-display font-bold text-[13px] w-5 inline-block text-center ${rankColors[i] ?? 'text-[var(--border-strong)]'}`}>
                        {i + 1}
                      </span>
                    </td>
                    <td className="py-2 pl-0 pr-1.5 text-left">
                      <span className="font-medium text-[12px] text-[var(--text-secondary)]">{player.username}</span>
                    </td>
                    <td className="py-2 px-1.5 text-center text-[12px] font-semibold font-display text-[var(--text)]">{player.acs}</td>
                    <td className="py-2 px-1.5 text-center text-[12px] font-semibold font-display text-[var(--text)]">{player.kills}</td>
                    <td className="py-2 px-1.5 text-center text-[12px] text-[var(--text-faint)]">{player.deaths}</td>
                    <td className="py-2 px-1.5 text-center text-[12px] font-semibold font-display text-[var(--text)]">{player.assists}</td>
                    <td className="py-2 px-1.5 text-center text-[12px] text-[var(--text-subtle)]">{player.econ_rating}</td>
                    <td className="py-2 px-1.5 text-center text-[12px] font-semibold font-display text-[var(--text)]">{player.first_bloods}</td>
                    <td className="py-2 px-1.5 text-center text-[12px] text-[var(--text-subtle)]">{player.plants}</td>
                    <td className="py-2 px-1.5 text-center text-[12px] text-[var(--text-subtle)]">{player.defuses}</td>
                    {([
                      player.bonus_fb,
                      player.bonus_death,
                      player.bonus_assist,
                      player.bonus_plant,
                      player.bonus_defuse,
                    ] as number[]).map((count, idx) => (
                      <td key={idx} className={`py-2 px-1.5 text-center text-[12px] font-semibold font-display ${idx === 0 ? 'border-l border-[var(--border)]' : ''} ${count > 0 ? 'text-[var(--gold)]' : 'text-[var(--border)]'}`}>
                        {count > 0 ? count : '—'}
                      </td>
                    ))}
                    <td className={`py-2 px-1.5 text-center text-[12px] font-semibold font-display ${player.wins > 0 ? 'text-[var(--green)]' : 'text-[var(--border)]'}`}>
                      {player.wins > 0 ? player.wins : '—'}
                    </td>
                    <td className="py-2 px-1.5 text-center border-l border-[var(--border)] text-[12px] font-bold font-display text-[var(--text-strong)]">
                      {player.total}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          </>
        )}
      </div>
    </div>
  );
}
