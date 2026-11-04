'use client';

import Link from 'next/link';
import { usePathname, useSearchParams, useRouter } from 'next/navigation';
import { Suspense } from 'react';
import { getDictionary, type Locale } from '@/i18n/dictionary';

function NavLinksInner({ seasons, locale }: { seasons: number[]; locale: Locale }) {
  const t = getDictionary(locale);
  const links = [{ href: '/', label: t.nav.leaderboard, exact: true }];
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const router = useRouter();

  const currentSeasonParam = searchParams.get('currentSeason');

  const selectedSeason = currentSeasonParam ?? (seasons.length ? String(seasons[seasons.length - 1]) : '');

  function handleSeasonChange(e: React.ChangeEvent<HTMLSelectElement>) {
    router.push(`/?currentSeason=${e.target.value}`);
  }

  return (
    <div className="flex items-center gap-3">
      {seasons.length > 1 && (
        <select
          value={selectedSeason}
          onChange={handleSeasonChange}
          className="text-[12px] font-medium bg-[var(--surface)] border border-[var(--border)] text-[var(--text-subtle)] rounded-md px-2.5 py-1.5 focus:outline-none focus:border-[var(--border-strong)] hover:border-[var(--border-strong)] cursor-pointer"
        >
          {seasons.map((s) => (
            <option key={s} value={s}>
              {t.season(s)}
            </option>
          ))}
        </select>
      )}

      <div className="flex gap-1">
        {links.map(({ href, label, exact }) => {
          const active = exact ? pathname === href : pathname.startsWith(href);
          return (
            <Link
              key={href}
              href={href}
              className={`text-[13px] font-medium px-3.5 py-1.5 rounded-md transition-colors ${
                active
                  ? 'text-[var(--text-strong)] bg-[var(--surface-strong)]'
                  : 'text-[var(--text-subtle)] hover:text-[var(--text-strong)] hover:bg-[var(--surface-strong)]'
              }`}
            >
              {label}
            </Link>
          );
        })}
      </div>
    </div>
  );
}

export default function NavLinks({ seasons, locale }: { seasons: number[]; locale: Locale }) {
  const t = getDictionary(locale);
  return (
    <Suspense fallback={
      <div className="flex gap-1">
        <span className="text-[13px] font-medium px-3.5 py-1.5 rounded-md text-[var(--text-faint)]">
          {t.nav.leaderboard}
        </span>
      </div>
    }>
      <NavLinksInner seasons={seasons} locale={locale} />
    </Suspense>
  );
}
