'use client';

import { useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { createPlayer, updatePlayer } from '@/app/admin/actions';
import { getDictionary, type Locale } from '@/i18n/dictionary';
import type { PlayerRef } from '@/services/players';
import type { RankRef } from '@/services/ranks';

const label = 'text-[11px] font-semibold uppercase tracking-[.06em] text-[var(--text-muted)]';
// h-9 forces the <select> to the same box height as the <input>s next to it — native <select>
// chrome otherwise renders a few px taller than a text input with identical padding, so the
// two would sit at slightly different heights in the same row even with items-end.
const inputClass =
  'h-9 rounded-md border border-[var(--border)] bg-[var(--surface)] px-3 text-[13px] text-[var(--text)] [color-scheme:var(--scheme)] focus:border-[var(--accent-55)] focus:outline-none';
const th =
  'px-3 pb-2 text-left text-[9px] font-semibold uppercase tracking-[.08em] text-[var(--border-strong)] border-b border-[var(--border)] whitespace-nowrap';

type Draft = { username: string; nickname: string; rankId: string };

function emptyDraft(): Draft {
  return { username: '', nickname: '', rankId: '' };
}

function draftFromPlayer(p: PlayerRef): Draft {
  return {
    username: p.username,
    nickname: p.nickname ?? '',
    rankId: p.current_rank_id != null ? String(p.current_rank_id) : '',
  };
}

export default function PlayerManager({
  players,
  ranks,
  locale,
}: {
  players: PlayerRef[];
  ranks: RankRef[];
  locale: Locale;
}) {
  const t = getDictionary(locale).adminPlayers;
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editDraft, setEditDraft] = useState<Draft>(emptyDraft());
  const [newDraft, setNewDraft] = useState<Draft>(emptyDraft());
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  function rankName(id: number | null) {
    if (id == null) return t.noRank;
    return ranks.find((r) => r.id === id)?.rank_name ?? t.noRank;
  }

  function startEdit(p: PlayerRef) {
    setEditingId(p.id);
    setEditDraft(draftFromPlayer(p));
    setMsg(null);
  }

  function cancelEdit() {
    setEditingId(null);
    setEditDraft(emptyDraft());
  }

  function saveEdit(id: string) {
    const username = editDraft.username.trim();
    if (!username) return;
    setMsg(null);
    startTransition(async () => {
      const result = await updatePlayer({
        id,
        username,
        nickname: editDraft.nickname.trim() || null,
        current_rank_id: editDraft.rankId ? Number(editDraft.rankId) : null,
      });
      if (result.ok) {
        setEditingId(null);
        router.refresh();
      } else {
        setMsg({ ok: false, text: result.error ?? t.updateFailed });
      }
    });
  }

  function add() {
    const username = newDraft.username.trim();
    if (!username) return;
    setMsg(null);
    startTransition(async () => {
      const result = await createPlayer({
        username,
        nickname: newDraft.nickname.trim() || null,
        current_rank_id: newDraft.rankId ? Number(newDraft.rankId) : null,
      });
      if (result.ok) {
        setNewDraft(emptyDraft());
        router.refresh();
      } else {
        setMsg({ ok: false, text: result.error ?? t.addFailed });
      }
    });
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex flex-col gap-1.5">
          <span className={label}>{t.usernameHeader}</span>
          <input
            value={newDraft.username}
            onChange={(e) => setNewDraft((d) => ({ ...d, username: e.target.value }))}
            placeholder={t.usernamePlaceholder}
            className={`${inputClass} w-44`}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <span className={label}>{t.nicknameHeader}</span>
          <input
            value={newDraft.nickname}
            onChange={(e) => setNewDraft((d) => ({ ...d, nickname: e.target.value }))}
            placeholder={t.nicknamePlaceholder}
            className={`${inputClass} w-44`}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <span className={label}>{t.rankHeader}</span>
          <select
            value={newDraft.rankId}
            onChange={(e) => setNewDraft((d) => ({ ...d, rankId: e.target.value }))}
            className={`${inputClass} w-40`}
          >
            <option value="">{t.noRank}</option>
            {ranks.map((r) => (
              <option key={r.id} value={r.id}>
                {r.rank_name}
              </option>
            ))}
          </select>
        </div>
        <button
          type="button"
          onClick={add}
          disabled={pending || !newDraft.username.trim()}
          className="rounded-md bg-[var(--accent)] px-4 py-2.5 text-[13px] font-semibold uppercase tracking-[.04em] text-white transition-colors hover:bg-[var(--accent-hover)] disabled:opacity-40"
        >
          {t.addPlayer}
        </button>
      </div>

      {msg && <p className={`text-[12px] ${msg.ok ? 'text-[var(--green)]' : 'text-[var(--accent)]'}`}>{msg.text}</p>}

      {players.length === 0 ? (
        <p className="text-[13px] text-[var(--text-muted)]">{t.noPlayers}</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-[620px] border-collapse">
            <thead>
              <tr>
                <th className={th}>{t.usernameHeader}</th>
                <th className={th}>{t.nicknameHeader}</th>
                <th className={th}>{t.rankHeader}</th>
                <th className={th} />
              </tr>
            </thead>
            <tbody>
              {players.map((p) => {
                const editing = editingId === p.id;
                return (
                  <tr key={p.id} className="border-b border-[var(--surface)]">
                    <td className="py-2 pr-3">
                      {editing ? (
                        <input
                          value={editDraft.username}
                          onChange={(e) => setEditDraft((d) => ({ ...d, username: e.target.value }))}
                          onFocus={(e) => e.target.select()}
                          autoFocus
                          className={`${inputClass} w-36`}
                        />
                      ) : (
                        <span className="text-[13px] text-[var(--text)]">{p.username}</span>
                      )}
                    </td>
                    <td className="py-2 pr-3">
                      {editing ? (
                        <input
                          value={editDraft.nickname}
                          onChange={(e) => setEditDraft((d) => ({ ...d, nickname: e.target.value }))}
                          placeholder={t.nicknamePlaceholder}
                          className={`${inputClass} w-36`}
                        />
                      ) : (
                        <span className="text-[13px] text-[var(--text-subtle)]">{p.nickname || '—'}</span>
                      )}
                    </td>
                    <td className="py-2 pr-3">
                      {editing ? (
                        <select
                          value={editDraft.rankId}
                          onChange={(e) => setEditDraft((d) => ({ ...d, rankId: e.target.value }))}
                          className={`${inputClass} w-36`}
                        >
                          <option value="">{t.noRank}</option>
                          {ranks.map((r) => (
                            <option key={r.id} value={r.id}>
                              {r.rank_name}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <span className="text-[13px] text-[var(--text-subtle)]">{rankName(p.current_rank_id)}</span>
                      )}
                    </td>
                    <td className="py-2 pr-1 text-right">
                      {editing ? (
                        <div className="flex items-center justify-end gap-2">
                          <button
                            type="button"
                            onClick={() => saveEdit(p.id)}
                            disabled={pending}
                            className="rounded-md border border-[var(--green)] px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[.04em] text-[var(--green)] transition-colors hover:bg-[var(--green-12)] disabled:opacity-40"
                          >
                            {t.save}
                          </button>
                          <button
                            type="button"
                            onClick={cancelEdit}
                            className="rounded-md border border-[var(--border)] px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[.04em] text-[var(--text-subtle)] transition-colors hover:border-[var(--border-strong)] hover:text-[var(--text-strong)]"
                          >
                            {t.cancel}
                          </button>
                        </div>
                      ) : (
                        <button
                          type="button"
                          onClick={() => startEdit(p)}
                          disabled={pending}
                          className="rounded-md border border-[var(--border)] px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[.04em] text-[var(--text-subtle)] transition-colors hover:border-[var(--border-strong)] hover:text-[var(--text-strong)] disabled:opacity-40"
                        >
                          {t.edit}
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
