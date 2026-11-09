# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

<!-- The sections down to "Checks before pushing" come from irmahs-labs/template. They are
adapted here because, for now, all work on the rewrite goes into the `migration` branch. -->

## Git branches

Always pull before making changes: run `git fetch origin` and start from the latest
`origin/migration` before the first edit.

- On a branch whose PR is open, bring it up to date with `origin/migration` first
- On `migration`, `dev` or `main`, or on a branch whose PR is already merged, create a new branch
  from `origin/migration` instead

Always name the branch after the feature or fix it contains, never a random or generated name.

- Format: `<type>/<feature-name>` in lowercase kebab-case
- Types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`, `ci`
- Examples: `feat/season-signups`, `fix/leaderboard-ties`, `chore/sonar-setup`
- If the session assigns a generic branch (for example `claude/<random-words>`), create a
  feature-named branch instead and push there

## Pull requests

- For now, every change goes in through a pull request into `migration`. Never push to
  `migration`, `dev` or `main` directly, and don't open pull requests into `dev` or `main`.
- Open the PR and stop there: never merge it, not even when CI is green. Irma reviews and clicks
  merge.
- Vercel only builds production, from `main`: anything merged into `main` rebuilds the frozen
  legacy site. Its last deployment stays live; don't trigger a Vercel deploy.

## Checks before pushing

```bash
./mvnw verify                  # all of it; CI runs this
cd frontend && npm run check   # Ultracite (oxlint + oxfmt); `npm run fix` fixes what it can
cd frontend && npm run typecheck
./mvnw spotless:apply          # formats Java; verify fails on unformatted code
```

The PostToolUse hook in `.claude/hooks/check.sh` runs on every file you edit: Ultracite and the
typecheck in `frontend/`, Spotless and a compile in `backend/`. In cloud sessions,
`.claude/hooks/session-start.sh` installs the Maven and npm dependencies of the rewrite.

## Layout

- `legacy/` — the current live app (Next.js 16 + Supabase), deployed by Vercel with **Root
  Directory `legacy`**. It is **frozen**: don't change, install or build anything in it. Its last
  Vercel deployment stays live until the rewrite replaces it. Its own guide is [`legacy/CLAUDE.md`](legacy/CLAUDE.md).
- The rewrite, built with Maven from the root `pom.xml` (parent: Spring Boot 4.1, Java 21):
  - `frontend/` — Angular 22 (standalone components, Vitest, tests named `*.test.ts`).
    `frontend/pom.xml` runs `npm ci`, `npm run check`, `npm run typecheck:test`, `npm run build` and
    `npm run test:ci` through frontend-maven-plugin and packages `dist/browser` under `static/` in
    a jar.
  - `backend/` — Spring Boot (Web MVC, JPA, Flyway, PostgreSQL, Actuator). Package
    `dev.irmahs.leaderboard`. Depends on the frontend jar, so `backend/target/tournament-leaderboard.jar`
    serves the UI and the API. Flyway migrations in `backend/src/main/resources/db/migration`,
    named by date (`V2026_11_05_1__description.sql`); Hibernate runs with `ddl-auto: validate`.
  - `deploy/` — the Hetzner server: `bootstrap.sh` (run once as root), Docker Compose with Caddy
    and Postgres, the database roles, and `.env` examples. See [`deploy/README.md`](deploy/README.md).
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

CI (`.github/workflows/ci.yml`) runs `./mvnw verify` on pull requests and on pushes to `main`,
`dev` and `migration`, then SonarQube Cloud once the `SONAR_TOKEN` secret exists. The version comes from
`-Drevision=…` (CI-friendly versions + flatten-maven-plugin); it defaults to `0.0.0-SNAPSHOT`.

## Rules

- Don't touch `legacy/`: no edits, no dependency updates, no builds.
- `main` is protected by a ruleset: changes land through pull requests only. Until the rewrite is
  ready, work stays in `migration`; it reaches `dev` and `main` only when Irma decides.
- Commit messages and pull request titles follow Conventional Commits (`feat:`, `fix:`, `chore:`,
  `docs:`, `refactor:`, `test:`, `ci:`), since release versions will be derived from them.
- Repository: `irmahs-labs/tournament-leaderboard` (moved from `irmahs/alsace-arena-spike-tournament`;
  GitHub redirects the old address).
