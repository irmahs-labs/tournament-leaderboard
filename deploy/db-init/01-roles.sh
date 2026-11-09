#!/usr/bin/env bash
# Runs once, when Postgres starts with an empty data volume. Creates:
#   leaderboard_owner  owns the database and its tables; Flyway connects as this role
#   leaderboard_app    reads and writes rows only; the app's normal connection uses it
# Row-level security doesn't apply to a table's owner, so this split is what makes the
# organisation policies hold for the app.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v owner_password="$LEADERBOARD_OWNER_PASSWORD" \
  -v app_password="$LEADERBOARD_APP_PASSWORD" <<'SQL'
CREATE ROLE leaderboard_owner LOGIN PASSWORD :'owner_password';
CREATE ROLE leaderboard_app LOGIN PASSWORD :'app_password';
CREATE DATABASE leaderboard OWNER leaderboard_owner;
SQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname leaderboard <<'SQL'
REVOKE ALL ON DATABASE leaderboard FROM PUBLIC;
GRANT CONNECT ON DATABASE leaderboard TO leaderboard_app;
GRANT USAGE ON SCHEMA public TO leaderboard_app;
-- Tables and sequences Flyway creates later, as leaderboard_owner:
ALTER DEFAULT PRIVILEGES FOR ROLE leaderboard_owner IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO leaderboard_app;
ALTER DEFAULT PRIVILEGES FOR ROLE leaderboard_owner IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO leaderboard_app;
SQL
