# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Layout

- `legacy/` — the current live app (Next.js 16 + Supabase), deployed by Vercel with **Root
  Directory `legacy`**. Its own guide is [`legacy/CLAUDE.md`](legacy/CLAUDE.md); read it before
  touching anything in that folder. All `npm` commands run from `legacy/`.
- The rewrite (Java 21 + Spring Boot + PostgreSQL with Flyway, Angular, Maven, Docker on Hetzner)
  will live at the root in `backend/`, `frontend/` and `deploy/`. Nothing of it exists yet.

## Rules

- Keep `legacy/` deployable: Vercel builds it on every merge to `main`.
- `main` is protected by a ruleset: changes land through pull requests only.
- Repository: `irmahs-labs/tournament-leaderboard` (moved from `irmahs/alsace-arena-spike-tournament`;
  GitHub redirects the old address).
