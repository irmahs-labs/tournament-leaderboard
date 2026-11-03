'use client';

import { useState } from 'react';

type Leader = { label: string; name: string };

const PER_VIEW = 3;

export default function StatLeaders({ items }: { items: Leader[] }) {
  const [start, setStart] = useState(0);
  const maxStart = Math.max(0, items.length - PER_VIEW);
  const clamped = Math.min(start, maxStart);

  const arrow =
    'shrink-0 rounded-md border border-[var(--border)] px-2 py-3 text-[16px] leading-none text-[var(--text-subtle)] transition-colors hover:border-[var(--border-strong)] hover:text-[var(--text-strong)] disabled:opacity-25';

  return (
    <div className="flex items-stretch gap-2 px-6 py-4 border-b border-[var(--border)]">
      <button
        type="button"
        onClick={() => setStart(Math.max(0, clamped - 1))}
        disabled={clamped === 0}
        className={arrow}
        aria-label="Previous"
      >
        ‹
      </button>

      <div className="flex-1 overflow-hidden">
        <div
          className="flex transition-transform duration-300 ease-out"
          style={{ transform: `translateX(calc(-${clamped} * (100% / ${PER_VIEW})))` }}
        >
          {items.map(({ label, name }) => (
            <div key={label} className="shrink-0 basis-1/3 px-1">
              <div className="min-w-0 rounded-lg bg-[var(--surface)] px-3.5 py-2.5">
                <div className="truncate font-display text-[15px] font-bold leading-tight text-[var(--text-strong)]">{name}</div>
                <div className="mt-1 text-[10px] uppercase tracking-[.07em] text-[var(--text-faint)]">{label}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <button
        type="button"
        onClick={() => setStart(Math.min(maxStart, clamped + 1))}
        disabled={clamped >= maxStart}
        className={arrow}
        aria-label="Next"
      >
        ›
      </button>
    </div>
  );
}
