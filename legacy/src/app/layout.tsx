import './globals.css';
import type { Metadata } from 'next';
import { Rajdhani, Inter } from 'next/font/google';
import NavLinks from '@/components/NavLinks';
import AdminMenu from '@/components/AdminMenu';
import ThemeToggle from '@/components/ThemeToggle';
import LanguageToggle from '@/components/LanguageToggle';
import { getMatches } from '@/services/matches';
import { getAdminUser } from '@/lib/supabase/auth';
import { getDictionary } from '@/i18n/dictionary';
import { getLocale } from '@/i18n/locale';

// Runs before paint so the page never flashes the wrong theme.
const THEME_INIT_SCRIPT = `
(function () {
  try {
    var stored = localStorage.getItem('theme');
    var theme = stored === 'light' || stored === 'dark' ? stored : 'dark';
    document.documentElement.dataset.theme = theme;
  } catch (e) {}
})();
`;

const rajdhani = Rajdhani({
  subsets: ['latin'],
  weight: ['500', '600', '700'],
  variable: '--font-rajdhani',
});

const inter = Inter({
  subsets: ['latin'],
  weight: ['400', '500'],
  variable: '--font-inter',
});

export const metadata: Metadata = {
  title: 'Alsace Arena Spike Tournament',
  description: 'Valorant season scoreboard and admin app',
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const [{ matches }, adminUser, locale] = await Promise.all([
    getMatches(),
    getAdminUser(),
    getLocale(),
  ]);
  const t = getDictionary(locale);
  const seasons = [
    ...new Set(matches.map((m) => m.match_season).filter((s): s is number => s != null)),
  ].sort((a, b) => a - b);
  const season = seasons.length ? seasons[seasons.length - 1] : null;

  return (
    <html
      lang={locale}
      suppressHydrationWarning
      className={`${rajdhani.variable} ${inter.variable}`}
    >
      <body className="bg-[var(--bg)] text-[var(--text)] min-h-screen" style={{ fontFamily: 'var(--font-inter), sans-serif' }}>
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
        <nav className="flex items-center justify-between px-7 py-3.5 bg-[var(--bg-nav)] border-b border-[var(--border)]">
          <div
            className="flex items-center gap-2.5 font-display font-bold text-xl text-[var(--text-strong)]"
          >
            <div
              className="w-7 h-7 bg-[var(--accent)] flex-shrink-0"
              style={{ clipPath: 'polygon(50% 0%, 100% 38%, 82% 100%, 18% 100%, 0% 38%)' }}
            />
            SPIKE ALSACE ARENA
            <span className="text-[11px] font-medium text-[var(--accent)] tracking-[.12em] uppercase ml-0.5">
              {season ? t.season(season) : t.seasonDash}
            </span>
          </div>

          <div className="flex items-center gap-3">
            <NavLinks seasons={seasons} locale={locale} />
            <ThemeToggle />
            <LanguageToggle locale={locale} />
            <AdminMenu isAdmin={!!adminUser} locale={locale} />
          </div>
        </nav>

        {children}
      </body>
    </html>
  );
}
