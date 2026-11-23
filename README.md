# Tournament Leaderboard

Leaderboards, match stats, schedules and scoring for seasonal Valorant tournaments, run by
organisations such as Alsace Arena's Spike Tournament.

**Live site:** https://tournament-leaderboard.irmahs.dev

## Repository layout

| Folder | What it is |
|---|---|
| `backend/` | The API: Java 21, Spring Boot 4, PostgreSQL with Flyway. Its jar also serves the Angular app. |
| `frontend/` | The Angular 22 app, built by Maven into the backend jar. |
| `deploy/` | How it runs on the shared irmahs.dev server, with the root `Dockerfile` and `compose.prod.yaml`. See [`deploy/README.md`](deploy/README.md). |
| `legacy/` | The old app, Next.js 16 on Supabase, which this one replaced. Asleep and frozen, kept for reference. See [`legacy/README.md`](legacy/README.md). |

Every merge into `main` that passes CI deploys the live site.

## Building

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
