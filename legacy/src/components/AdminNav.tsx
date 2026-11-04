import Link from 'next/link';
import { getDictionary, type Locale } from '@/i18n/dictionary';

const tabBase = 'text-[12px] font-semibold uppercase tracking-[.04em] px-4 py-2.5 rounded-t-lg border transition-colors';
// The active tab shares the panel's background and its bottom border is painted the same
// color as that background, then pulled down 1px (-mb-px) over the panel's top border — the
// classic seamless tab/panel trick, so the active tab reads as part of the page below it.
const activeTab = 'relative z-10 -mb-px border-[var(--border)] border-b-[var(--surface)] bg-[var(--surface)] text-[var(--text-strong)]';
// The inactive tab sits on the page background (not the panel's), nudged down slightly to
// read as further back, like an unselected browser/folder tab behind the front one.
const inactiveTab = 'translate-y-0.5 border-[var(--border)] bg-[var(--bg-nav)] text-[var(--text-subtle)] hover:translate-y-0 hover:text-[var(--text-strong)] hover:bg-[var(--surface)]';

/** Tab strip switching between the two admin pages, meant to sit directly above the page
 * panel each renders its content in (see admin/page.tsx, admin/players/page.tsx). Both pages
 * are Server Components, so this needs no client state — `current` is passed in by whichever
 * one renders it. */
export default function AdminNav({ current, locale }: { current: 'score' | 'players'; locale: Locale }) {
  const t = getDictionary(locale).adminNav;
  return (
    <div className="mt-6 flex gap-1">
      <Link href="/admin" className={`${tabBase} ${current === 'score' ? activeTab : inactiveTab}`}>
        {t.scoreEntry}
      </Link>
      <Link href="/admin/players" className={`${tabBase} ${current === 'players' ? activeTab : inactiveTab}`}>
        {t.players}
      </Link>
    </div>
  );
}
