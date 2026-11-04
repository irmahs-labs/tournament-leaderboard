'use client';

import { useRouter } from 'next/navigation';
import type { Locale } from '@/i18n/dictionary';

export default function LanguageToggle({ locale }: { locale: Locale }) {
  const router = useRouter();

  function setLocale(next: Locale) {
    if (next === locale) return;
    document.cookie = `lang=${next}; path=/; max-age=31536000`;
    router.refresh();
  }

  return (
    <div className="flex overflow-hidden rounded-md border border-[var(--border)] text-[11px] font-semibold uppercase tracking-[.04em]">
      {(['en', 'fr'] as const).map((code) => (
        <button
          key={code}
          type="button"
          onClick={() => setLocale(code)}
          aria-current={locale === code}
          className={`px-2.5 py-1.5 transition-colors ${
            locale === code
              ? 'bg-[var(--surface-strong)] text-[var(--text-strong)]'
              : 'text-[var(--text-subtle)] hover:text-[var(--text-strong)]'
          }`}
        >
          {code}
        </button>
      ))}
    </div>
  );
}
