#!/usr/bin/env bash
# Runs once when the dev container is first created. It sets up the Spring Boot +
# Angular rewrite; legacy/ is frozen and isn't installed or built here.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "Installing the rewrite's dependencies and building it once (tests skipped)..."
./mvnw -B -ntp install -DskipTests

# Optional: Claude Code CLI, so you can run `claude` in the container terminal.
npm install -g @anthropic-ai/claude-code || echo "Skipped Claude Code CLI install."

echo "Done. Run 'cd backend && ../mvnw spring-boot:run' and 'cd frontend && npm start', then open port 4200."
