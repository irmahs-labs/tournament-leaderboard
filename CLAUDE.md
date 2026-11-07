# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

<!-- The sections down to "Checks before pushing" come from irmahs-labs/template. They are
adapted here because this repo works through `dev` (Vercel previews it) before `main`. -->

## Git branches

Always pull before making changes: run `git fetch origin` and start from the latest `origin/dev`
before the first edit.

- On a branch whose PR is open, bring it up to date with `origin/dev` first
- On `dev` or `main`, or on a branch whose PR is already merged, create a new branch from
  `origin/dev` instead

Always name the branch after the feature or fix it contains, never a random or generated name.

- Format: `<type>/<feature-name>` in lowercase kebab-case
- Types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`, `ci`
- Examples: `feat/season-signups`, `fix/leaderboard-ties`, `chore/sonar-setup`
- If the session assigns a generic branch (for example `claude/<random-words>`), create a
  feature-named branch instead and push there

## Pull requests

- Never push to `dev` or `main`. Every change goes in through a pull request into `dev`; `dev`
  goes into `main` through a pull request too.
- Open the PR and stop there: never merge it, not even when CI is green. Irma reviews and clicks
  merge.
- Vercel deploys `legacy/` on merge to `main` and previews `dev`. Re-run a deploy only when Irma
  asks.

## Checks before pushing

```bash
./mvnw verify                  # all of it; CI runs this
cd frontend && npm run check   # Ultracite (oxlint + oxfmt); `npm run fix` fixes what it can
cd frontend && npm run typecheck
./mvnw spotless:apply          # formats Java; verify fails on unformatted code
cd legacy && npm run build     # only when legacy/ changed
```

The PostToolUse hook in `.claude/hooks/check.sh` runs on every file you edit: Ultracite and the
typecheck in `frontend/`, Spotless and a compile in `backend/`, the typecheck in `legacy/`. In
cloud sessions, `.claude/hooks/session-start.sh` installs the Maven and npm dependencies.

## Layout

- `legacy/` — the current live app (Next.js 16 + Supabase), deployed by Vercel with **Root
  Directory `legacy`**. Its own guide is [`legacy/CLAUDE.md`](legacy/CLAUDE.md); read it before
  touching anything in that folder. All `npm` commands run from `legacy/`.
- The rewrite, built with Maven from the root `pom.xml` (parent: Spring Boot 4.1, Java 21):
  - `frontend/` — Angular 22 (standalone components, Vitest, tests named `*.test.ts`).
    `frontend/pom.xml` runs `npm ci`, `npm run check`, `npm run typecheck:test`, `npm run build` and
    `npm run test:ci` through frontend-maven-plugin and packages `dist/browser` under `static/` in
    a jar.
  - `backend/` — Spring Boot (Web MVC, JPA, Flyway, PostgreSQL, Actuator). Package
    `dev.irmahs.leaderboard`. Depends on the frontend jar, so `backend/target/tournament-leaderboard.jar`
    serves the UI and the API. Flyway migrations in `backend/src/main/resources/db/migration`,
    named by date (`V2026_11_05_1__description.sql`); Hibernate runs with `ddl-auto: validate`.
  - `deploy/` (coming) — Docker Compose, Caddy, backups for Hetzner.
- The full plan (schema, endpoints, screens, phases) is the migration plan page kept with the
  project designs; ask the user for the link when you need it.

## Commands (rewrite)

```bash
./mvnw verify                        # everything: Angular build + tests, Java unit tests (*Test),
                                     # integration tests (*IT, Testcontainers; skipped without Docker)
./mvnw install -DskipTests           # once, before running the backend alone
cd backend && ../mvnw spring-boot:run   # API on :8080; starts compose.yaml's Postgres (needs Docker)
cd frontend && npm start             # Angular on :4200, proxies /api and /actuator to :8080
```

CI (`.github/workflows/ci.yml`) runs `./mvnw verify` on pull requests and on pushes to `main` and
`dev`, then SonarQube Cloud once the `SONAR_TOKEN` secret exists. The version comes from
`-Drevision=…` (CI-friendly versions + flatten-maven-plugin); it defaults to `0.0.0-SNAPSHOT`.

## Rules

- Keep `legacy/` deployable: Vercel builds it on every merge to `main`.
- `main` is protected by a ruleset: changes land through pull requests only. Work goes into `dev`
  first (Vercel previews it), then `dev` is merged into `main`.
- Commit messages and pull request titles follow Conventional Commits (`feat:`, `fix:`, `chore:`,
  `docs:`, `refactor:`, `test:`, `ci:`), since release versions will be derived from them.
- Repository: `irmahs-labs/tournament-leaderboard` (moved from `irmahs/alsace-arena-spike-tournament`;
  GitHub redirects the old address).
