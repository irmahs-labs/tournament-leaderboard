# Project TODO

## Done

- [x] Supabase setup (schema, RLS, SSR client, env vars)
- [x] Next.js project structure (services, lib, types, constants)
- [x] Navigation with season dropdown (only seasons present in the DB)
- [x] Match list + sidebar with status (done / upcoming)
- [x] Match detail page — aggregated stats across all games in a match
- [x] Per-game stats page with player performance table
- [x] Scoring formula + `computeScore()` helper
- [x] Bonus icons + legend (first blood, least deaths, most assists, plants, defuses, victory)
- [x] Season leaderboard — cumulative points across all games; only shows matches that have games
- [x] Podium — MVP + silver + bronze of the season, above the leaderboard
- [x] Match/game detail — Most Valuable Player card + 6-card leader carousel (least deaths,
      most kills / assists / first bloods / plants / defuses), 3 at a time
- [x] Variable games per match (2–5), data-driven game tabs
- [x] Removed the "final match" concept — is_final dropped, match 9 removed, seasons are 1–8
- [x] Admin auth (Supabase login modal + `is_admin` guard on `/admin`)
- [x] Admin score entry — season/match/game pickers, 10 pre-filled rows, player dropdown
      (excludes players already picked in another row) or inline "New player" (username only —
      `players` has no `in_game_name`/nickname column), no spinner arrows on stat inputs,
      one-click "Calculate bonuses" (the 5 non-victory bonus boxes are view-only), save
- [x] Admin score editing/deleting — picking a saved season+match+game loads its real
      `game_stats` into the form ("Save changes" replaces them); "Delete game" removes the
      game + its stats after a confirm
- [x] Admin roster carryover — picking a game with no saved stats yet pre-fills the player
      rows (not the numbers) from the nearest earlier game in the same match that has data
- [x] Scoreboard-screenshot scan (Claude vision) — built, feature-flagged off
      (`SCOREBOARD_SCAN_ENABLED` in `src/constants/flags.ts`)
- [x] Deployment — Vercel (https://alsace-arena-spike-tournament.vercel.app/)
- [x] Cloud dev container (`.devcontainer/`)
- [x] Light/dark theme toggle — every color is a CSS var (`globals.css`), no more hard-coded hex
- [x] English/French language toggle — cookie-based locale (`src/i18n/`); public pages, the
      admin login modal, and the score-entry form are all translated
- [x] Admin player management (`/admin/players`) — add or edit a player's username, nickname,
      and rank; no delete, separate from score entry (tab strip via `AdminNav.tsx` on both
      admin pages)

## In progress

- [ ] UI / UX polish to align all views

## To do

- [ ] Match dates — display and manage scheduled dates per match
- [ ] Admin — player availability, retain the uploaded screenshot, delete an entire match
      (not just one game)
- [ ] Migrate `matchScores.getPlayerTotals` + the per-game page onto `computeScore()`
- [ ] Simplify home page URL — `/?currentSeason=8&tab=1&matchId=1&matchSeason=8` has redundant
      params, should collapse to something like `/?season=8&match=1`
- [ ] Root `middleware.ts` to refresh Supabase sessions (wire up `lib/supabase/middleware.ts`)

## Backlog

- [ ] Player profile pages
- [ ] Mobile responsiveness
- [ ] Match history across multiple seasons on `/matches`
