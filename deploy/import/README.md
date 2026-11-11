# Importing from Supabase

A one-time copy of the live site's data into the new schema, under the `alsace-arena`
organisation. Supabase isn't changed and stays the rollback. Rehearse it on a local database
until the real run at cutover is routine.

| File | What it does |
|---|---|
| `export.sh`, `export.sql` | Reads the five Supabase tables into `data/*.csv`. Read-only. |
| `import.sh`, `import.sql` | Loads `data/*.csv` into a database that already has the schema, in one transaction. |
| `data/` | The exports. Real player data: ignored by git, never committed. |

Both scripts run `psql` in a `postgres:17` container, so only Docker is needed.

## 1. Export

In Supabase, Connect → **Session pooler** (it works without IPv6), copy the connection string
with your database password, then:

```bash
export SUPABASE_DB_URL='postgresql://…'
deploy/import/export.sh
```

## 2. Rehearse on a local database

Start the backend once so Flyway creates the tables (`cd backend && ../mvnw spring-boot:run`),
find the port of its Postgres with `docker compose -f backend/compose.yaml port postgres 5432`,
then:

```bash
export TARGET_DB_URL='postgresql://leaderboard:leaderboard@host.docker.internal:PORT/leaderboard'
deploy/import/import.sh
```

It prints how many players, seasons, matches, games and stats it imported. It can run again:
it first empties `alsace-arena`'s tournament.

If the export has rows the new schema would refuse (a player twice in one game, two games with
the same number in a match, two players with the same Riot ID, a match without a season), it
lists them all and imports nothing. Fix them in Supabase and export again.

## What maps where

- `players.username` becomes the Riot ID. Nicknames aren't imported: players start with no
  alias and no Discord username.
- Each `match_season` becomes a season: 8 matches, 1 to 5 games, 50 points per win, today's
  stat rules, and five bonuses at 10 (FB, LD, MA, MP, MD).
- `acs` is Valorant's performance score, so it goes into `performance_score`. ACS (the
  tracker's), ADR, damage delta, KAST %, headshot % and clutches stay empty.
- Each `bonus_*` flag becomes an awarded bonus, copied as recorded, so totals match the live
  site.
- Ids of ranks, players, matches, games and stats are kept.
- Not imported: `players.nickname`, `auth.users` and `profiles`. Admins sign in with Google on the new site.

The production run, on the server, is part of the cutover checklist.
