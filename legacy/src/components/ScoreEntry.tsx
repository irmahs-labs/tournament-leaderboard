'use client';

import { useEffect, useMemo, useRef, useState, useTransition } from 'react';
import { IconSwords, IconSkull, IconHeartHandshake, IconBomb, IconHammer } from '@tabler/icons-react';
import { computeScore } from '@/constants/scoring';
import { SCOREBOARD_SCAN_ENABLED } from '@/constants/flags';
import {
  saveGameStats,
  loadGameStats,
  loadPreviousGamePlayers,
  deleteGameStats,
  type SaveRow,
} from '@/app/admin/actions';
import { getDictionary, type Locale } from '@/i18n/dictionary';
import type { Match } from '@/types';
import type { PlayerRef } from '@/services/players';

type ScannedPlayer = {
  name: string;
  combat_score: number;
  kills: number;
  deaths: number;
  assists: number;
  econ: number;
  first_bloods: number;
  plants: number;
  defuses: number;
};

type Row = {
  key: string;
  isNew: boolean;
  player_id: string;
  newUsername: string;
  acs: number;
  kills: number;
  deaths: number;
  assists: number;
  econ_rating: number;
  first_bloods: number;
  plants: number;
  defuses: number;
  win: boolean;
  bonus_fb: boolean;
  bonus_death: boolean;
  bonus_assist: boolean;
  bonus_plant: boolean;
  bonus_defuse: boolean;
};

const NUM_FIELDS = [
  ['acs', 'ACS'],
  ['kills', 'K'],
  ['deaths', 'D'],
  ['assists', 'A'],
  ['econ_rating', 'Eco'],
  ['first_bloods', 'FB'],
  ['plants', 'Pl.'],
  ['defuses', 'Def.'],
] as const;

const BONUS_KEYS = ['bonus_fb', 'bonus_death', 'bonus_assist', 'bonus_plant', 'bonus_defuse'] as const;

// Same icon set as the public leaderboard/match-detail pages, for the same bonuses.
const BONUS_ICONS = {
  bonus_fb: IconSwords,
  bonus_death: IconSkull,
  bonus_assist: IconHeartHandshake,
  bonus_plant: IconBomb,
  bonus_defuse: IconHammer,
} as const;

const label = 'text-[11px] font-semibold uppercase tracking-[.06em] text-[var(--text-muted)]';
const select =
  'rounded-md border border-[var(--border)] bg-[var(--surface)] px-2.5 py-2 text-[13px] text-[var(--text)] [color-scheme:var(--scheme)] focus:border-[var(--accent-55)] focus:outline-none disabled:opacity-40';
// Spinner arrows for type="number" are hidden globally in globals.css.
const cellInput =
  'w-12 rounded-md border border-[var(--border)] bg-[var(--surface)] px-1 py-1.5 text-center text-[12px] text-[var(--text)] [color-scheme:var(--scheme)] focus:outline-none';
const th =
  'px-1.5 pb-2 text-[9px] font-semibold uppercase tracking-[.08em] text-[var(--border-strong)] border-b border-[var(--border)] whitespace-nowrap';

function matchPlayer(name: string, players: PlayerRef[]): PlayerRef | null {
  const n = name.trim().toLowerCase();
  if (!n) return null;
  return players.find((p) => p.username.toLowerCase() === n) ?? null;
}

function emptyRow(): Row {
  return {
    key: crypto.randomUUID(),
    isNew: false,
    player_id: '',
    newUsername: '',
    acs: 0,
    kills: 0,
    deaths: 0,
    assists: 0,
    econ_rating: 0,
    first_bloods: 0,
    plants: 0,
    defuses: 0,
    win: false,
    bonus_fb: false,
    bonus_death: false,
    bonus_assist: false,
    bonus_plant: false,
    bonus_defuse: false,
  };
}

/** Ticks every bonus except victory: first blood / least deaths / most assists / most plants / most defuses. */
function withAutoBonuses(rows: Row[]): Row[] {
  if (rows.length === 0) return rows;
  const maxFirstBloods = Math.max(...rows.map((r) => r.first_bloods));
  const minDeaths = Math.min(...rows.map((r) => r.deaths));
  const maxAssists = Math.max(...rows.map((r) => r.assists));
  const maxPlants = Math.max(...rows.map((r) => r.plants));
  const maxDefuses = Math.max(...rows.map((r) => r.defuses));
  return rows.map((r) => ({
    ...r,
    bonus_fb: maxFirstBloods > 0 && r.first_bloods === maxFirstBloods,
    bonus_death: r.deaths === minDeaths,
    bonus_assist: maxAssists > 0 && r.assists === maxAssists,
    bonus_plant: maxPlants > 0 && r.plants === maxPlants,
    bonus_defuse: maxDefuses > 0 && r.defuses === maxDefuses,
  }));
}

function rowPoints(r: Row): number {
  return computeScore({
    acs: r.acs,
    kills: r.kills,
    deaths: r.deaths,
    assists: r.assists,
    econ_rating: r.econ_rating,
    first_bloods: r.first_bloods,
    plants: r.plants,
    defuses: r.defuses,
    bonus_count: BONUS_KEYS.filter((k) => r[k]).length,
    wins: r.win ? 1 : 0,
  });
}

export default function ScoreEntry({
  matches,
  players,
  playersError,
  locale,
}: {
  matches: Match[];
  players: PlayerRef[];
  playersError?: string | null;
  locale: Locale;
}) {
  const t = getDictionary(locale);
  const bonusLabels: Record<(typeof BONUS_KEYS)[number], string> = {
    bonus_fb: t.adminForm.bonusFirstBlood,
    bonus_death: t.adminForm.bonusLeastDeaths,
    bonus_assist: t.adminForm.bonusMostAssists,
    bonus_plant: t.adminForm.bonusMostPlants,
    bonus_defuse: t.adminForm.bonusMostDefuses,
  };
  const maxSeason = useMemo(
    () => matches.reduce((max, m) => Math.max(max, m.match_season ?? 0), 0) || 1,
    [matches],
  );
  const seasonOptions = useMemo(
    () => Array.from({ length: Math.max(maxSeason + 2, 12) }, (_, i) => i + 1),
    [maxSeason],
  );

  const [season, setSeason] = useState<number | ''>(maxSeason);
  const [matchNumber, setMatchNumber] = useState<number | ''>('');
  const [gameNumber, setGameNumber] = useState<number | ''>('');

  const [rows, setRows] = useState<Row[]>(() => Array.from({ length: 10 }, emptyRow));
  const [scanning, setScanning] = useState(false);
  const [scanError, setScanError] = useState<string | null>(null);
  const [saveMsg, setSaveMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [saving, startSaving] = useTransition();
  const [deleting, startDeleting] = useTransition();
  const [gameExists, setGameExists] = useState(false);
  const [loadingExisting, setLoadingExisting] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const incomplete = rows.filter((r) => (r.isNew ? !r.newUsername.trim() : !r.player_id)).length;
  const ready = season !== '' && matchNumber !== '' && gameNumber !== '' && rows.length > 0;

  // Selecting a full season/match/game loads whatever is already saved for it, so editing
  // starts from the real data instead of blank rows you'd silently overwrite on Save. When
  // there's nothing saved for this game yet, the roster (not the stats) carries over from the
  // nearest earlier game in the same match — a match's games are usually the same 10 players.
  useEffect(() => {
    if (season === '' || matchNumber === '' || gameNumber === '') return;
    let cancelled = false;
    setLoadingExisting(true);
    setSaveMsg(null);

    (async () => {
      const result = await loadGameStats({ season, matchNumber, gameNumber });
      if (cancelled) return;

      if (result.error) {
        setLoadingExisting(false);
        setGameExists(false);
        setSaveMsg({ ok: false, text: `${t.adminForm.loadFailedPrefix}${result.error}` });
        return;
      }

      if (result.rows.length > 0) {
        const loaded = result.rows.map((r) => ({ ...emptyRow(), ...r }));
        const padding = Array.from({ length: Math.max(0, 10 - loaded.length) }, emptyRow);
        setRows([...loaded, ...padding]);
        setGameExists(true);
        setLoadingExisting(false);
        return;
      }

      if (gameNumber > 1) {
        const roster = await loadPreviousGamePlayers({ season, matchNumber, beforeGameNumber: gameNumber });
        if (cancelled) return;
        if (!roster.error && roster.playerIds.length > 0) {
          const carried = roster.playerIds.map((pid) => ({ ...emptyRow(), player_id: pid }));
          const padding = Array.from({ length: Math.max(0, 10 - carried.length) }, emptyRow);
          setRows([...carried, ...padding]);
          setGameExists(false);
          setLoadingExisting(false);
          return;
        }
      }

      setRows(Array.from({ length: 10 }, emptyRow));
      setGameExists(false);
      setLoadingExisting(false);
    })();

    return () => {
      cancelled = true;
    };
  }, [season, matchNumber, gameNumber]);

  function patch(key: string, next: Partial<Row>) {
    setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...next } : r)));
    setSaveMsg(null);
  }

  function checkBonuses() {
    setRows((rs) => withAutoBonuses(rs));
    setSaveMsg(null);
  }

  function deleteGame() {
    if (season === '' || matchNumber === '' || gameNumber === '') return;
    const confirmed = window.confirm(t.adminForm.deleteConfirm(season, matchNumber, gameNumber));
    if (!confirmed) return;
    setSaveMsg(null);
    startDeleting(async () => {
      const result = await deleteGameStats({ season, matchNumber, gameNumber });
      if (result.ok) {
        setRows(Array.from({ length: 10 }, emptyRow));
        setGameExists(false);
        setSaveMsg({ ok: true, text: t.adminForm.deleted(season, matchNumber, gameNumber) });
      } else {
        setSaveMsg({ ok: false, text: result.error ?? t.adminForm.deleteFailed });
      }
    });
  }

  async function onFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;

    setScanning(true);
    setScanError(null);
    setSaveMsg(null);
    try {
      const body = new FormData();
      body.append('image', file);
      const res = await fetch('/api/admin/scan-scoreboard', { method: 'POST', body });
      const json = await res.json();
      if (!res.ok) throw new Error(json.error ?? 'Scan failed.');

      const scanned: ScannedPlayer[] = json.players ?? [];
      const built: Row[] = scanned.map((p) => {
        const hit = matchPlayer(p.name, players);
        return {
          ...emptyRow(),
          isNew: !hit,
          player_id: hit?.id ?? '',
          newUsername: hit ? '' : p.name,
          acs: p.combat_score ?? 0,
          kills: p.kills ?? 0,
          deaths: p.deaths ?? 0,
          assists: p.assists ?? 0,
          econ_rating: p.econ ?? 0,
          first_bloods: p.first_bloods ?? 0,
          plants: p.plants ?? 0,
          defuses: p.defuses ?? 0,
        };
      });
      setRows(withAutoBonuses(built));
    } catch (err) {
      setScanError(err instanceof Error ? err.message : 'Scan failed.');
    } finally {
      setScanning(false);
    }
  }

  function save() {
    setSaveMsg(null);
    const payload: SaveRow[] = rows.map((r) => ({
      player_id: r.isNew ? '' : r.player_id,
      new_player: r.isNew ? { username: r.newUsername.trim() } : undefined,
      acs: r.acs,
      kills: r.kills,
      deaths: r.deaths,
      assists: r.assists,
      econ_rating: r.econ_rating,
      first_bloods: r.first_bloods,
      plants: r.plants,
      defuses: r.defuses,
      win: r.win,
      bonus_fb: r.bonus_fb,
      bonus_death: r.bonus_death,
      bonus_assist: r.bonus_assist,
      bonus_plant: r.bonus_plant,
      bonus_defuse: r.bonus_defuse,
    }));
    startSaving(async () => {
      const result = await saveGameStats({
        season: Number(season),
        matchNumber: Number(matchNumber),
        gameNumber: Number(gameNumber),
        rows: payload,
      });
      if (result.ok) setGameExists(true);
      setSaveMsg(
        result.ok
          ? {
              ok: true,
              text: t.adminForm.saved(payload.length, Number(season), Number(matchNumber), Number(gameNumber)),
            }
          : { ok: false, text: result.error ?? t.adminForm.saveFailed },
      );
    });
  }

  return (
    <div className="flex flex-col gap-6">
      {playersError ? (
        <p className="rounded-md border border-[var(--accent-33)] bg-[var(--accent-0f)] px-3 py-2 text-[12px] text-[var(--accent)]">
          {t.adminForm.playersErrorPrefix}
          {playersError}
        </p>
      ) : players.length === 0 ? (
        <p className="rounded-md border border-[var(--gold-33)] bg-[var(--gold-0f)] px-3 py-2 text-[12px] text-[var(--gold)]">
          {t.adminForm.noPlayersPre}
          <code>players</code>
          {t.adminForm.noPlayersMid}
          <code>misc/admin-setup.sql</code>
          {t.adminForm.noPlayersPost}
        </p>
      ) : null}

      {/* Selectors */}
      <div className="flex flex-wrap items-end gap-4">
        <div className="flex flex-col gap-1.5">
          <span className={label}>{t.seasonLabel}</span>
          <select
            className={select}
            value={season}
            onChange={(e) => setSeason(e.target.value ? Number(e.target.value) : '')}
          >
            <option value="">–</option>
            {seasonOptions.map((s) => (
              <option key={s} value={s}>
                {t.season(s)}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1.5">
          <span className={label}>{t.matchLabel}</span>
          <select
            className={select}
            value={matchNumber}
            onChange={(e) => setMatchNumber(e.target.value ? Number(e.target.value) : '')}
          >
            <option value="">–</option>
            {[1, 2, 3, 4, 5, 6, 7, 8].map((n) => (
              <option key={n} value={n}>
                {t.match(n)}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1.5">
          <span className={label}>{t.gameLabel}</span>
          <select
            className={select}
            value={gameNumber}
            onChange={(e) => setGameNumber(e.target.value ? Number(e.target.value) : '')}
          >
            <option value="">–</option>
            {[1, 2, 3, 4, 5].map((g) => (
              <option key={g} value={g}>
                {t.game(g)}
              </option>
            ))}
          </select>
        </div>

        {SCOREBOARD_SCAN_ENABLED && (
          <div className="flex flex-col gap-1.5">
            <span className={label}>{t.adminForm.scorePicture}</span>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => fileRef.current?.click()}
                disabled={gameNumber === '' || scanning}
                className="rounded-md bg-[var(--accent)] px-3.5 py-2 text-[13px] font-semibold uppercase tracking-[.04em] text-white transition-colors hover:bg-[var(--accent-hover)] disabled:opacity-40"
              >
                {scanning ? t.adminForm.scanning : t.adminForm.uploadScan}
              </button>
            </div>
            <input ref={fileRef} type="file" accept="image/png,image/jpeg,image/webp,image/gif" hidden onChange={onFile} />
          </div>
        )}
      </div>

      {scanError && (
        <p className="rounded-md border border-[var(--accent-33)] bg-[var(--accent-0f)] px-3 py-2 text-[12px] text-[var(--accent)]">
          {scanError}
        </p>
      )}

      {rows.length > 0 && (
        <>
          <div className="flex flex-wrap gap-x-5 gap-y-2 px-3.5 py-2.5 bg-[var(--surface)] border border-[var(--border)] rounded-lg">
            {BONUS_KEYS.map((k) => {
              const Icon = BONUS_ICONS[k];
              return (
                <div key={k} className="flex items-center gap-1.5 text-[11px] text-[var(--text-muted)]">
                  <Icon size={14} className="text-[var(--gold)]" />
                  {bonusLabels[k]}
                </div>
              );
            })}
          </div>

          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[12px] text-[var(--text-muted)]">
            <span>{t.adminForm.rows(rows.length)}</span>
            {loadingExisting && <span className="text-[var(--gold)]">{t.adminForm.loadingExisting}</span>}
            {!loadingExisting && gameExists && (
              <span className="text-[var(--green)]">{t.adminForm.editingSaved}</span>
            )}
            {incomplete > 0 && (
              <span className="text-[var(--accent)]">{t.adminForm.needsPlayer(incomplete)}</span>
            )}
            <span>{t.adminForm.hintBonuses}</span>
            <span>{t.adminForm.hintReadonly}</span>
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-[1100px] border-collapse">
              <thead>
                <tr>
                  <th className={`${th} text-left`}>{t.adminForm.playerHeader}</th>
                  {NUM_FIELDS.map(([, h]) => (
                    <th key={h} className={`${th} w-14`}>
                      {h}
                    </th>
                  ))}
                  <th className={`${th} w-12 border-l border-[var(--border)]`}>{t.matchDetail.win}</th>
                  {BONUS_KEYS.map((k) => {
                    const Icon = BONUS_ICONS[k];
                    return (
                      <th key={k} className={`${th} w-14`} title={bonusLabels[k]}>
                        <Icon size={13} className="mx-auto text-[var(--gold)]" />
                      </th>
                    );
                  })}
                  <th className={`${th} w-14 border-l border-[var(--border)]`}>{t.adminForm.ptsHeader}</th>
                  <th className={th} />
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => {
                  const flagged = r.isNew ? !r.newUsername.trim() : !r.player_id;
                  // A player picked in another row drops out of this row's list.
                  const takenElsewhere = new Set(
                    rows.filter((other) => other.key !== r.key && !other.isNew && other.player_id)
                      .map((other) => other.player_id),
                  );
                  const availablePlayers = players.filter(
                    (p) => p.id === r.player_id || !takenElsewhere.has(p.id),
                  );
                  return (
                    <tr key={r.key} className="border-b border-[var(--surface)]">
                      <td className="py-1.5 pr-2 align-top">
                        <div className="flex items-start gap-2">
                          <label className="flex items-center gap-1 pt-2 text-[10px] text-[var(--text-muted)] whitespace-nowrap">
                            <input
                              type="checkbox"
                              checked={r.isNew}
                              onChange={(e) =>
                                patch(r.key, {
                                  isNew: e.target.checked,
                                  player_id: '',
                                  newUsername: '',
                                })
                              }
                            />
                            {t.adminForm.newLabel}
                          </label>
                          {r.isNew ? (
                            <input
                              value={r.newUsername}
                              placeholder={t.adminForm.usernamePlaceholder}
                              onChange={(e) => patch(r.key, { newUsername: e.target.value })}
                              className={`w-40 rounded-md border bg-[var(--surface)] px-2 py-1.5 text-[12px] text-[var(--text)] focus:outline-none ${
                                flagged ? 'border-[var(--accent-77)]' : 'border-[var(--border)]'
                              }`}
                            />
                          ) : (
                            <div className="flex items-center gap-1.5">
                              {flagged && <span title={t.adminForm.pickPlayerTitle}>⚠</span>}
                              <select
                                value={r.player_id}
                                onChange={(e) => patch(r.key, { player_id: e.target.value })}
                                className={`w-40 rounded-md border bg-[var(--surface)] px-2 py-1.5 text-[12px] text-[var(--text)] focus:outline-none ${
                                  flagged ? 'border-[var(--accent-77)]' : 'border-[var(--border)]'
                                }`}
                              >
                                <option value="">{t.adminForm.pickPlayer}</option>
                                {availablePlayers.map((p) => (
                                  <option key={p.id} value={p.id}>
                                    {p.username}
                                  </option>
                                ))}
                              </select>
                            </div>
                          )}
                        </div>
                      </td>
                      {NUM_FIELDS.map(([k]) => (
                        <td key={k} className="px-1 py-1.5 text-center">
                          <input
                            type="number"
                            value={r[k]}
                            onChange={(e) => patch(r.key, { [k]: Number(e.target.value) || 0 } as Partial<Row>)}
                            onFocus={(e) => e.target.select()}
                            className={cellInput}
                          />
                        </td>
                      ))}
                      <td className="border-l border-[var(--border)] px-1 py-1.5 text-center">
                        <input
                          type="checkbox"
                          checked={r.win}
                          onChange={(e) => patch(r.key, { win: e.target.checked })}
                        />
                      </td>
                      {BONUS_KEYS.map((k) => (
                        <td key={k} className="px-1 py-1.5 text-center">
                          <input type="checkbox" checked={r[k]} disabled readOnly />
                        </td>
                      ))}
                      <td className="border-l border-[var(--border)] px-1 py-1.5 text-center font-display text-[12px] font-bold text-[var(--text-strong)]">
                        {rowPoints(r)}
                      </td>
                      <td className="px-1 py-1.5 text-center">
                        <button
                          type="button"
                          onClick={() => {
                            setRows((rs) => rs.filter((x) => x.key !== r.key));
                            setSaveMsg(null);
                          }}
                          className="text-[var(--text-muted)] hover:text-[var(--accent)]"
                          aria-label={t.adminForm.removeRow}
                        >
                          ×
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <div className="flex flex-wrap items-center gap-4">
            <button
              type="button"
              onClick={checkBonuses}
              className="rounded-md border border-[var(--border)] px-3.5 py-2.5 text-[13px] font-medium text-[var(--text-subtle)] transition-colors hover:border-[var(--border-strong)] hover:text-[var(--text-strong)]"
            >
              {t.adminForm.calculateBonuses}
            </button>
            <div className="w-px self-stretch bg-[var(--border)]" />
            <button
              type="button"
              onClick={save}
              disabled={!ready || saving || deleting}
              className="rounded-md bg-[var(--accent)] px-4 py-2.5 text-[13px] font-semibold uppercase tracking-[.04em] text-white transition-colors hover:bg-[var(--accent-hover)] disabled:opacity-40"
            >
              {saving ? t.adminForm.saving : gameExists ? t.adminForm.saveChanges : t.adminForm.saveGame}
            </button>
            <button
              type="button"
              onClick={deleteGame}
              disabled={!ready || !gameExists || saving || deleting}
              className="rounded-md border border-[var(--accent-55)] px-4 py-2.5 text-[13px] font-semibold uppercase tracking-[.04em] text-[var(--accent)] transition-colors hover:bg-[var(--accent-11)] disabled:opacity-30"
            >
              {deleting ? t.adminForm.deleting : t.adminForm.deleteGame}
            </button>
            <button
              type="button"
              onClick={() => {
                setRows(Array.from({ length: 10 }, emptyRow));
                setSaveMsg(null);
                setScanError(null);
              }}
              className="text-[12px] font-medium text-[var(--text-subtle)] hover:text-[var(--text-strong)]"
            >
              {t.adminForm.resetRows}
            </button>
            {saveMsg && (
              <span className={`text-[12px] ${saveMsg.ok ? 'text-[var(--green)]' : 'text-[var(--accent)]'}`}>
                {saveMsg.text}
              </span>
            )}
          </div>
        </>
      )}
    </div>
  );
}
