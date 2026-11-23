# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git branches

Always pull before making changes: run `git fetch origin` and start from the latest
`origin/main` before the first edit.

- On a branch whose PR is open, bring it up to date with `origin/main` first
- On `main`, or on a branch whose PR is already merged, create a new branch from `origin/main`
  instead

Always name the branch after the feature or fix it contains, never a random or generated name.

- Format: `<type>/<feature-name>` in lowercase kebab-case
- Types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`, `ci`
- Examples: `feat/season-signups`, `fix/leaderboard-ties`, `chore/sonar-setup`
- If the session assigns a generic branch (for example `claude/<random-words>`), create a
  feature-named branch instead and push there

## Pull requests

- Every change goes in through a pull request into `main`. Never push to `main` directly.
  `migration` and `dev` are retired: don't push to them or open pull requests into them.
- Open the PR and stop there: never merge it, not even when CI is green. Irma reviews and clicks
  merge.
- Every merge into `main` that passes CI deploys production (tournament-leaderboard.irmahs.dev).

## The context graph and the notes

Irma's notes on every irmahs.dev app are in her Obsidian notes, the repo irmahs/my-obsidian-notes (checked out at `~/Dev/my-obsidian-notes`), under `Self Hosted/`.

- Before starting work, read `Self Hosted/Context Graph.md` from that repo's `origin/main`: how irmahs.dev and every subdomain fit together (the proxy, the account service, which app talks to which, where each keeps its data). Without a checkout, read it with `gh api repos/irmahs/my-obsidian-notes/contents/Self%20Hosted/Context%20Graph.md -H "Accept: application/vnd.github.raw"`.
- When a change alters what the app is or does, update the notes too:
  - `Apps.md`, the app's section;
  - `Technologies.md`, if it adds, removes or changes a technology;
  - `Context Graph.md`, if it changes a subdomain, a redirect, a link between apps, who asks whom (sign-in, admin checks, internal calls), or where data lives. Update both the graph and the table.
- The notes go in through their own pull request, like any other change: a branch from the notes repo's `origin/main`, never their `main`. The local checkout is often left on an old branch, so work in a `git worktree` of `origin/main` rather than touching it. Link the app's PR in the notes PR.

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

- `legacy/` — the old app (Next.js 16 + Supabase), replaced by the rewrite and put to sleep: its
  Vercel project no longer builds. It is **frozen**: don't change, install or build anything in
  it. Kept for reference; its own guide is [`legacy/CLAUDE.md`](legacy/CLAUDE.md).
- The rewrite, built with Maven from the root `pom.xml` (parent: Spring Boot 4.1, Java 21):
  - `frontend/` — Angular 22 (standalone components, Vitest, tests named `*.test.ts`). Organised
    by feature like the backend: `src/app/shared/` for what every feature uses, `layout/` for the
    shells, then one folder per feature (`results/`, `sign-in/`, …). A component shared by several
    features goes in `shared/`; one shared by pages of a single feature stays in that feature.
    `frontend/pom.xml` runs `npm ci`, `npm run check`, `npm run typecheck:test`, `npm run build` and
    `npm run test:ci` through frontend-maven-plugin and packages `dist/browser` under `static/` in
    a jar.
  - `backend/` — Spring Boot (Web MVC, JPA, Flyway, PostgreSQL, Actuator). Package
    `dev.irmahs.leaderboard`. Depends on the frontend jar, so `backend/target/tournament-leaderboard.jar`
    serves the UI and the API. Flyway migrations in `backend/src/main/resources/db/migration`,
    named by date (`V2026_11_05_1__description.sql`); Hibernate runs with `ddl-auto: validate`.
  - Deployment — on the shared irmahs.dev server, like the other irmahs-labs apps; see
    [`deploy/README.md`](deploy/README.md).
- [`docs/context-graph.md`](docs/context-graph.md) maps the rewrite: packages and their
  dependencies, endpoints, tables, screens and phases. Read it before starting work, and update it
  in the same pull request whenever a change adds, removes or reconnects any of those.
- The design system, https://claude.ai/artifact/LJLikryobGFQ53nune6jnV, holds the tokens,
  type, components and their rules, desktop and phone. `frontend/src/styles.css` is its source:
  build every screen from its tokens and shared classes, put a pattern used by two pages or more
  in `styles.css` rather than copying it, and update the design system in the same pull request
  whenever a token, a shared class or a shared component changes.
- The full plan (schema, endpoints, screens, phases) is the migration plan page kept with the
  project designs; ask the user for the link when you need it.

## Commands (rewrite)

```bash
./mvnw verify                        # everything: Angular build + tests, Java unit tests (*Test),
                                     # integration tests (*IT, Testcontainers; skipped without Docker)
./mvnw install -DskipTests           # once, before running the backend alone
cd backend && ../mvnw spring-boot:run   # API on :8080; starts compose.yaml's Postgres (needs Docker)
cd frontend && npm start             # Angular on :4200, proxies /api, /actuator and sign-in to :8080
```

Signing in locally needs the dev Google client in `backend/local.yml` (ignored by git): copy
`backend/local.example.yml`. Without it the backend doesn't start.

CI (`.github/workflows/ci.yml`) runs `./mvnw verify` on pull requests and on pushes to `main`,
then SonarQube Cloud (its quality gate blocks pull requests), then, on `main`, the deploy. The version comes from
`-Drevision=…` (CI-friendly versions + flatten-maven-plugin); it defaults to `0.0.0-SNAPSHOT`.

## Rules

- Don't touch `legacy/`: no edits, no dependency updates, no builds.
- `main` is protected by a ruleset: changes land through pull requests only, and only once the
  **Build and test** check (with the quality gate) has passed, since every merge into it deploys
  production.
- Database changes must work with the previous version too: a deploy starts the new app next to
  the old one (`deploy/rollout.sh`), so Flyway migrates while the old version still serves.
  Expand, then contract: add columns as nullable or with a default, add tables freely; rename or
  drop a column or table, or tighten a constraint, only in a later release, once no running
  version uses the old shape. A migration that can't follow this says so in its PR, and is merged
  outside match nights.
- Commit messages and pull request titles follow Conventional Commits (`feat:`, `fix:`, `chore:`,
  `docs:`, `refactor:`, `test:`, `ci:`), since release versions will be derived from them.
- Repository: `irmahs-labs/tournament-leaderboard` (moved from `irmahs/alsace-arena-spike-tournament`;
  GitHub redirects the old address).
