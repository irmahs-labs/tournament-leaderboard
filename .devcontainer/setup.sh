#!/usr/bin/env bash
# Runs once when the cloud container is first created.
set -euo pipefail

# The current Next.js app lives in legacy/ while the Spring Boot + Angular rewrite is built.
cd "$(dirname "$0")/../legacy"

echo "Installing dependencies..."
npm install

# .env.local is gitignored, so it isn't in the repo. Recreate it from the
# secrets you set in GitHub -> Settings -> Secrets and variables -> Codespaces
# (or your platform's equivalent), which arrive here as environment variables.
if [ ! -f .env.local ]; then
  {
    echo "NEXT_PUBLIC_SUPABASE_URL=${NEXT_PUBLIC_SUPABASE_URL:-}"
    echo "NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=${NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY:-}"
    echo "ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY:-}"
  } > .env.local
  echo "Wrote .env.local from environment."
  grep -q '=$' .env.local && echo "  (some values are empty — add the missing secrets and rerun)"
fi

# Optional: Claude Code CLI, so you can run \`claude\` in the container terminal.
npm install -g @anthropic-ai/claude-code || echo "Skipped Claude Code CLI install."

echo "Done. Run 'cd legacy && npm run dev' and open the forwarded port 3000."
