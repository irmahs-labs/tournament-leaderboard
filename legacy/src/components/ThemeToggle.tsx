'use client';

import { useEffect, useState } from 'react';
import { IconSun, IconMoon } from '@tabler/icons-react';

type Theme = 'dark' | 'light';

export default function ThemeToggle() {
  // Matches the inline no-flash script in layout.tsx, which already set
  // documentElement.dataset.theme before this component ever mounts.
  const [theme, setTheme] = useState<Theme>('dark');

  useEffect(() => {
    const current = document.documentElement.dataset.theme === 'light' ? 'light' : 'dark';
    setTheme(current);
  }, []);

  function toggle() {
    const next: Theme = theme === 'dark' ? 'light' : 'dark';
    setTheme(next);
    document.documentElement.dataset.theme = next;
    try {
      localStorage.setItem('theme', next);
    } catch {
      // Private browsing / storage disabled — theme just won't persist across visits.
    }
  }

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
      title={theme === 'dark' ? 'Light mode' : 'Dark mode'}
      className="flex h-8 w-8 items-center justify-center rounded-md border border-[var(--border)] text-[var(--text-subtle)] transition-colors hover:border-[var(--border-strong)] hover:text-[var(--text-strong)]"
    >
      {theme === 'dark' ? <IconSun size={15} /> : <IconMoon size={15} />}
    </button>
  );
}
