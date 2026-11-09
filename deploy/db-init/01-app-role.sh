#!/usr/bin/env bash
# Runs once, when Postgres starts with an empty data volume, as leaderboard_owner (POSTGRES_USER),
# which owns the leaderboard database. Creates leaderboard_app, the role the app connects with:
# it reads and writes rows but can't change the schema. Row-level security doesn't apply to a
# table's owner, so this split is what makes the organisation policies hold for the app.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v app_password="$LEADERBOARD_APP_PASSWORD" <<'SQL'
CREATE ROLE leaderboard_app LOGIN PASSWORD :'app_password';
REVOKE ALL ON DATABASE leaderboard FROM PUBLIC;
GRANT CONNECT ON DATABASE leaderboard TO leaderboard_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO leaderboard_app;
-- Tables and sequences Flyway creates later, as leaderboard_owner:
ALTER DEFAULT PRIVILEGES FOR ROLE leaderboard_owner IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO leaderboard_app;
ALTER DEFAULT PRIVILEGES FOR ROLE leaderboard_owner IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO leaderboard_app;
SQL
