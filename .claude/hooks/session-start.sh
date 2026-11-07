#!/bin/bash
# Installs the dependencies of all three parts so tests, checks and builds work
# in Claude Code cloud sessions (adapted from irmahs-labs/template). Each install
# runs only when a file that decides what it installs has changed since it last
# succeeded.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"

# A hash file lists the inputs of an install as they were when it last succeeded.
unchanged() { sha256sum --check --status "$1" 2>/dev/null; }
record() {
  local hashes=$1
  shift
  mkdir -p "$(dirname "$hashes")"
  sha256sum "$@" > "$hashes" || rm -f "$hashes"
}

# legacy/: npm, in the background while Maven runs.
legacy_hashes=legacy/node_modules/.session-start.sha256
legacy_inputs=(.claude/hooks/session-start.sh legacy/package.json legacy/package-lock.json)
legacy_pid=""
if ! unchanged "$legacy_hashes"; then
  rm -f "$legacy_hashes"
  (cd legacy && npm install --no-audit --no-fund) && record "$legacy_hashes" "${legacy_inputs[@]}" &
  legacy_pid=$!
fi

# Maven: the Java dependencies and the frontend jar the backend depends on, in
# ~/.m2. Its `npm ci` also installs frontend/node_modules.
maven_hashes="$HOME/.m2/repository/.tournament-leaderboard-session-start.sha256"
maven_inputs=(.claude/hooks/session-start.sh .mvn/wrapper/maven-wrapper.properties
  pom.xml backend/pom.xml frontend/pom.xml frontend/package.json frontend/package-lock.json)
if ! unchanged "$maven_hashes"; then
  rm -f "$maven_hashes"
  ./mvnw -B -ntp -q install -DskipTests
  record "$maven_hashes" "${maven_inputs[@]}"
fi

if [ -n "$legacy_pid" ]; then
  wait "$legacy_pid"
fi
