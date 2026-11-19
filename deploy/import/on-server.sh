#!/usr/bin/env bash
# Runs the import on the production server, into its database. See README.md, step 4.
#
#   deploy/import/on-server.sh deploy@<server>
#
# The CSVs and import.sql go over SSH into the database container only, never onto the server's
# disk, and are removed from the container afterwards, whether the import worked or not. The
# database is dumped into backups/ first, like before a deploy, so the import can be undone.
set -euo pipefail
target="${1:?Give the server as deploy@<server>}"

dir="$(cd "$(dirname "$0")" && pwd)"
for table in ranks players matches games game_stats; do
  [ -s "$dir/data/$table.csv" ] || { echo "data/$table.csv is missing: run export.sh first" >&2; exit 1; }
done

tar -C "$dir" -cf - import.sql data/ranks.csv data/players.csv data/matches.csv data/games.csv \
    data/game_stats.csv |
  ssh "$target" 'set -eu
    cd /srv/tournament-leaderboard
    export COMPOSE_FILE=compose.prod.yaml
    [ -n "$(docker compose ps -q db < /dev/null)" ] || { echo "The database is not running"; exit 1; }

    mkdir -p backups && chmod 700 backups
    dump="backups/$(date -u +%Y%m%dT%H%M%SZ)-before-import.dump"
    (umask 077 && docker compose exec -T db pg_dump -U leaderboard_owner -d leaderboard --format=custom < /dev/null > "$dump")
    [ -s "$dump" ] || { echo "The database dump is empty"; exit 1; }
    ls -1t backups/*.dump | tail -n +15 | xargs -r rm --
    echo "Dumped the database into $dump"

    # Only this tar reads what comes over SSH; every other command is given no input.
    trap "docker compose exec -T db rm -rf /tmp/import < /dev/null" EXIT
    docker compose exec -T db sh -c "rm -rf /tmp/import && mkdir -m 700 /tmp/import && tar -xf - -C /tmp/import"
    docker compose exec -T -w /tmp/import/data db psql -X -q -U leaderboard_owner -d leaderboard -f /tmp/import/import.sql < /dev/null'
