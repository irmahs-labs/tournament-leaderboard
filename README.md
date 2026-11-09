# Tournament Leaderboard

Leaderboards, match stats, schedules and scoring for seasonal Valorant tournaments, run by
organisations such as Alsace Arena's Spike Tournament.

**Live site:** https://alsace-arena-spike-tournament.vercel.app/ (moving to
https://tournament-leaderboard.irmahs.dev)

## Repository layout

| Folder | What it is |
|---|---|
| `legacy/` | The current live app: Next.js 16 on Supabase, deployed by Vercel with Root Directory `legacy`. See [`legacy/README.md`](legacy/README.md). |
| `backend/` | The rewrite's API: Java 21, Spring Boot 4, PostgreSQL with Flyway. Its jar also serves the Angular app. |
| `frontend/` | The rewrite's Angular 22 app, built by Maven into the backend jar. |
| `deploy/` | The Hetzner server: setup script, Docker Compose with Caddy and Postgres. See [`deploy/README.md`](deploy/README.md). |

The legacy app is frozen: no more changes or builds. Its last deployment keeps running, and keeps taking scores, until the rewrite reaches parity and the
site moves to `tournament-leaderboard.irmahs.dev`.

## Building the rewrite

```bash
./mvnw verify
```

That builds and tests everything: the Angular app (Vitest), the Spring Boot app (JUnit 5,
Mockito), and the integration tests against a real PostgreSQL (Testcontainers, needs Docker;
skipped when Docker isn't running). It also fails on lint and formatting: Ultracite (Oxlint +
Oxfmt) and the typecheck for the Angular app, Spotless (palantir-java-format) for Java. The result
is `backend/target/tournament-leaderboard.jar`.

To fix formatting:

```bash
cd frontend && npm run fix   # Ultracite
./mvnw spotless:apply        # Java
```

To work on it locally, with Docker running:

```bash
./mvnw install -DskipTests          # once, so the backend can find the frontend module
cd backend && ../mvnw spring-boot:run   # API on :8080, starts Postgres from compose.yaml
cd frontend && npm install && npm start # Angular on :4200, /api proxied to :8080
```

---

Created and run by [Irma HS](https://irmahs.dev) · tournament-leaderboard@irmahs.dev · [MIT licence](LICENSE)
