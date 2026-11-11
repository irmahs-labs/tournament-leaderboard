#!/usr/bin/env bash
# Exports the live Supabase tables to deploy/import/data/*.csv. Read-only. See README.md.
#
#   export SUPABASE_DB_URL='postgresql://…'   # Supabase → Connect → Session pooler
#   deploy/import/export.sh
set -euo pipefail
: "${SUPABASE_DB_URL:?Set SUPABASE_DB_URL to the Supabase session pooler connection string}"

dir="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$dir/data"
# The URL holds the password: it goes in as an environment variable, never on a command line.
docker run --rm -e SUPABASE_DB_URL -v "$dir:/import" -w /import/data postgres:17 \
  sh -c 'psql "$SUPABASE_DB_URL" -f /import/export.sql'
ls -l "$dir/data"
