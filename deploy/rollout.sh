#!/usr/bin/env bash
# Starts the version just checked out without a gap, on the server in /srv/tournament-leaderboard
# (run by .github/workflows/deploy.yml after the dump). See deploy/README.md, "Deploys without a
# gap".
#
# Caddy reaches the app as tournament-leaderboard:8080, and Docker's DNS answers with every
# running container under that alias. So the new app starts next to the old one, under the same
# alias; once its health check passes, the old one is stopped gracefully and removed. A new app
# that never becomes healthy is removed instead, and the old one keeps serving.
set -euo pipefail

export COMPOSE_FILE=compose.prod.yaml

# How long the new app may take to pass its health check (the image allows 90 s to start).
READY_SECONDS=${READY_SECONDS:-240}

docker compose build app
docker compose up -d --wait --remove-orphans db

old=$(docker compose ps -q app)
if [ -z "$old" ]; then
  echo "No app running: starting it"
  docker compose up -d --wait app
  exit 0
fi

# A second container from the new image; the running one is kept as it is.
docker compose up -d --no-deps --no-recreate --scale app=$(( $(wc -l <<<"$old") + 1 )) app
new=$(docker compose ps -q app | grep -vxF -f <(printf '%s\n' "$old") || true)
[ -n "$new" ] || { echo "The new app container didn't start"; exit 1; }
echo "New app $(docker inspect -f '{{.Name}}' "$new") is starting next to the old one"

give_up() {
  echo "$1: removing it; the old app keeps serving"
  docker logs --tail 80 "$new" || true
  docker rm -f "$new" > /dev/null
  exit 1
}

deadline=$(( SECONDS + READY_SECONDS ))
while :; do
  status=$(docker inspect -f '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' "$new")
  case "$status" in
    "running healthy") break ;;
    *unhealthy | restarting* | exited* | dead*) give_up "The new app failed ($status)" ;;
  esac
  (( SECONDS < deadline )) || give_up "The new app wasn't healthy after ${READY_SECONDS} s"
  sleep 3
done
echo "The new app is healthy"

# Graceful: the old app stops taking requests, finishes the ones it has and closes its
# online-count streams, which the pages reopen on the new one.
for container in $old; do
  echo "Stopping the old app $(docker inspect -f '{{.Name}}' "$container")"
  docker stop --time 30 "$container" > /dev/null
  docker rm "$container" > /dev/null
done
echo "Deployed: $(docker compose ps -q app | wc -l) app running"
