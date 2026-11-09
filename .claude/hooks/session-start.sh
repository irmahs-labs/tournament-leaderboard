#!/bin/bash
# Installs the rewrite's dependencies so tests, checks and builds work in Claude
# Code cloud sessions (adapted from irmahs-labs/template): the Java dependencies,
# frontend/node_modules (through Maven's `npm ci`) and the frontend jar the
# backend depends on. It runs only when a file that decides what gets installed
# has changed since the install last succeeded. legacy/ is frozen, so it is
# not installed.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"

hashes="$HOME/.m2/repository/.tournament-leaderboard-session-start.sha256"
if ! sha256sum --check --status "$hashes" 2>/dev/null; then
  rm -f "$hashes"
  ./mvnw -B -ntp -q install -DskipTests
  mkdir -p "$(dirname "$hashes")"
  sha256sum .claude/hooks/session-start.sh .mvn/wrapper/maven-wrapper.properties \
    pom.xml backend/pom.xml frontend/pom.xml frontend/package.json frontend/package-lock.json \
    > "$hashes" || rm -f "$hashes"
fi
