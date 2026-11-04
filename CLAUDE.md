# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Layout

- `legacy/` — the current live app (Next.js 16 + Supabase), deployed by Vercel with **Root
  Directory `legacy`**. Its own guide is [`legacy/CLAUDE.md`](legacy/CLAUDE.md); read it before
  touching anything in that folder. All `npm` commands run from `legacy/`.
- The rewrite, built with Maven from the root `pom.xml` (parent: Spring Boot 4.1, Java 21):
  - `frontend/` — Angular 22 (standalone components, Vitest). `frontend/pom.xml` runs `npm ci`,
    `npm run build` and `npm run test:ci` through frontend-maven-plugin and packages `dist/browser`
    under `static/` in a jar.
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
