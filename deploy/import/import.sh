#!/usr/bin/env bash
# Loads deploy/import/data/*.csv into a database that already has the schema. See README.md.
#
#   export TARGET_DB_URL='postgresql://leaderboard:leaderboard@host.docker.internal:PORT/leaderboard'
#   deploy/import/import.sh
set -euo pipefail
: "${TARGET_DB_URL:?Set TARGET_DB_URL to the database to import into}"

dir="$(cd "$(dirname "$0")" && pwd)"
docker run --rm -e TARGET_DB_URL -v "$dir:/import:ro" -w /import/data postgres:17 \
  sh -c 'psql "$TARGET_DB_URL" -f /import/import.sql'
