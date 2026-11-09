#!/usr/bin/env bash
# First start only (empty volume): creates leaderboard_app, the role the app connects with.
# It reads and writes rows but owns nothing; see deploy/README.md, "Database roles".
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v app_password="$APP_DB_PASSWORD" <<'SQL'
CREATE ROLE leaderboard_app LOGIN PASSWORD :'app_password';
-- Tables and sequences Flyway creates later, as the owner:
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO leaderboard_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO leaderboard_app;
SQL
