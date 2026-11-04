# Alsace Arena Spike Tournament (legacy Next.js app)

> This is the current live app, kept in `legacy/` while the Spring Boot + Angular rewrite is built
> at the root of the repository. Run every command below from this folder: `cd legacy`.

**Live site:** https://alsace-arena-spike-tournament.vercel.app/

A web application for tracking standings and match stats for a monthly Valorant competition at Alsace Arena.

Built with **Next.js**, **Tailwind CSS**, **Supabase**, and **Claude** (scoreboard OCR).

## What this app does

- Season leaderboard — cumulative player stats and points across every game of the season.
- Podium — MVP + silver + bronze of the season, shown above the leaderboard.
- Per-match and per-game stat tables, each led by a Most Valuable Player card and a
  carousel of category leaders (least deaths, most kills, assists, first bloods, plants, defuses).
- Season switcher in the header (only lists seasons that exist in the database).
- Light/dark theme toggle and English/French language toggle, both in the header — the public
  site and the whole admin area are fully translated.
- Admin area (Supabase auth), two pages with a tab strip between them:
  - `/admin` — score entry: enter a game's 10 player rows by hand, pick each player from a
    dropdown, or tick "New" to register one on the spot by username. Moving to a later game
    with no data yet carries the same 10 players over automatically, so only the numbers need
    re-entering. One-click "Check bonuses" fills every bonus but victory. Uploading a
    screenshot for Claude to read instead is built in but off by default (feature flag).
  - `/admin/players` — add a player (username, nickname, rank) or edit an existing one's
    username/nickname/rank inline. Players can only be created and edited, never deleted.

## Scoring formula

```
Total = ACS + Kills + Assists + Eco + First bloods + Plants + Defuses
      + (bonus count × 10)
      − Deaths
      + (victories × 50)
```

Bonuses are awarded per game for: first blood, least deaths, most assists, most plants, most defuses.
A victory bonus applies only to players marked as winning that game — if no result is recorded, nobody gets it.

## Season structure

- A season is a set of matches (numbered 1–8); a match is **2–5 games** (varies by data).
- The leaderboard and podium are computed across every game of the season.
- Only matches with at least one game recorded appear on the leaderboard page.

## Tech stack

- **Next.js 16** (App Router, TypeScript, React 19)
- **Tailwind CSS v4**
- **Supabase** (PostgreSQL, auth, RLS) via `@supabase/ssr`
- **`@anthropic-ai/sdk`** — `claude-opus-5` vision can read the scoreboard screenshot
  (feature-flagged off by default, see `src/constants/flags.ts`)

## Project structure

```
src/
  app/
    page.tsx                       — season leaderboard + MVP banner + match tabs
    matches/                       — match grid
    matches/[season]/[matchId]/    — match detail (all games combined, 2–5 per match)
      [game]/                      — individual game stats
    admin/                         — score-entry form (page.tsx) + actions.ts (save/load/delete
                                     games, create/rename/delete players)
      players/                     — player management page
    api/admin/scan-scoreboard/     — POST an image, Claude returns the 10 rows
  components/
    NavLinks.tsx                   — nav bar + season dropdown
    AdminMenu.tsx                  — login modal (browser Supabase client)
    AdminNav.tsx                   — tab strip between the two admin pages
    ScoreEntry.tsx                 — the admin score-entry form
    PlayerManager.tsx              — add/edit players (username, nickname, rank)
    StatLeaders.tsx                — category-leader carousel (match/game detail header)
    ThemeToggle.tsx, LanguageToggle.tsx
  constants/scoring.ts             — POINTS_PER_BONUS, POINTS_PER_WIN, computeScore()
  constants/flags.ts               — SCOREBOARD_SCAN_ENABLED feature flag
  i18n/dictionary.ts, locale.ts    — EN/FR strings + cookie-based locale
  lib/supabase/                    — client / server factories + auth.ts (getAdminUser)
  services/                        — data access (matches, games, gameStats, matchScores, players, ranks)
  types/                           — TypeScript domain model
```

## Theme & language

All colors are CSS variables (`src/app/globals.css`, dark by default, `[data-theme="light"]`
for the alternate palette) — components reference `var(--token)`, never a literal hex.
`ThemeToggle` flips `data-theme` on `<html>` and remembers it in `localStorage`. Language is a
`lang` cookie (`en`/`fr`, read server-side in `src/i18n/locale.ts`) so translated text renders
correctly in Server Components; `LanguageToggle` sets the cookie and refreshes.

## Getting started

1. Install dependencies:
   ```bash
   npm install
   ```
2. Create `.env.local` in this `legacy/` folder:
   ```env
   NEXT_PUBLIC_SUPABASE_URL=https://your-project-ref.supabase.co
   NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=your-anon-key
   ANTHROPIC_API_KEY=sk-ant-...        # only needed if SCOREBOARD_SCAN_ENABLED is turned on
   ```
3. Run the Supabase setup once: paste `misc/admin-setup.sql` into the Supabase SQL editor
   (creates the profile trigger, RLS policies, and the admin write access).
4. Start the dev server:
   ```bash
   npm run dev
   ```
5. Open [http://localhost:3000](http://localhost:3000).

A cloud dev container is defined in `../.devcontainer/` at the repository root (GitHub Codespaces or any devcontainer host).

## Creating a new admin

There's no sign-up flow — admins are created by hand in Supabase, then promoted with SQL:

1. Supabase dashboard → **Authentication → Users → Add user** → enter their email + password,
   tick **Auto Confirm User**.
2. Copy that user's **UUID** from the Users list.
3. In the Supabase **SQL editor**, run:
   ```sql
   insert into public.profiles (id, is_admin)
   values ('paste-the-uuid-here', true)
   on conflict (id) do update set is_admin = true;
   ```
4. They can now sign in from the **Admin** button in the header and reach `/admin`.

**"Wrong email or password" even with correct credentials?** This almost always means step 1's
**Auto Confirm User** checkbox wasn't ticked — Supabase Auth silently refuses to sign in an
unconfirmed user and returns that same generic error. Fix it with:
```sql
update auth.users
set email_confirmed_at = now()
where email = 'their-email@example.com';
```
(`confirmed_at` is a generated column derived from `email_confirmed_at` — don't set it directly,
it updates itself.)

(See `misc/admin-setup.sql`, steps 3–4, for the same instructions alongside the rest of the
one-time RLS/schema setup.)
