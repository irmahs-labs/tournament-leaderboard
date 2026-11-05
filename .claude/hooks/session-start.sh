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

# Maven: the Java dependencies, and the frontend jar the backend depends on, in
# ~/.m2. It runs first because its `npm ci` replaces frontend/node_modules.
m2="${HOME}/.m2/repository"
hashes="$m2/.tournament-leaderboard-session-start.sha256"
if ! sha256sum --check --status "$hashes" 2>/dev/null; then
  rm -f "$hashes"
  ./mvnw -B -ntp -q install -DskipTests
  mkdir -p "$m2"
  sha256sum .claude/hooks/session-start.sh .mvn/wrapper/maven-wrapper.properties \
    pom.xml frontend/pom.xml backend/pom.xml > "$hashes" || rm -f "$hashes"
fi

# npm, for the Angular app and for the legacy Next.js app.
for dir in frontend legacy; do
  hashes="$dir/node_modules/.session-start.sha256"
  if ! sha256sum --check --status "$hashes" 2>/dev/null; then
    rm -f "$hashes"
    (cd "$dir" && npm install --no-audit --no-fund)
    sha256sum .claude/hooks/session-start.sh "$dir/package.json" "$dir/package-lock.json" \
      > "$hashes" || rm -f "$hashes"
  fi
done
