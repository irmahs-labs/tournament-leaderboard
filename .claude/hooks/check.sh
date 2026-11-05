#!/usr/bin/env bash
# PostToolUse hook for Write|Edit, adapted from irmahs-labs/template's
# ultracite.sh for the three parts of this repo. On the one file just written:
#   frontend/  Ultracite (Oxlint with the anti-slop plugin, and Oxfmt), then the typecheck
#   backend/   Spotless (palantir-java-format), then compiling main and test code
#   legacy/    the typecheck only (it has no linter that works with Next.js 16)
#
# Exit 0: clean. Exit 2: the problems left over go to stderr, which Claude Code
# hands back to Claude to fix. The auto-fixer can change types, so a clean lint
# alone is not taken as clean.
set -uo pipefail
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}" || exit 0
root=$PWD

file=$(jq -r '.tool_response.filePath // .tool_input.file_path // empty')
[ -n "$file" ] && [ -f "$file" ] || exit 0
file=$(realpath "$file")

# Never dependencies or build output.
case "$file" in
  */node_modules/* | */target/* | */dist/* | */.angular/* | */.next/*) exit 0 ;;
esac

report() {
  echo "$1" >&2
  printf '%s\n' "$2" | head -n 40 >&2
  exit 2
}

case "$file" in
  "$root"/frontend/*)
    case "$file" in
      *.ts | *.js | *.mjs | *.cjs | *.json | *.jsonc | *.css | *.md | *.html) ;;
      *) exit 0 ;;
    esac
    cd "$root/frontend" || exit 0
    npx --no-install ultracite fix "$file" > /dev/null 2>&1

    # Oxlint only lints script files; the rest is formatted above and that is
    # all. Failure is judged by reported errors, not the exit code.
    case "$file" in
      *.ts | *.js | *.mjs | *.cjs)
        lint=$(npx --no-install ultracite check "$file" 2>&1)
        count=$(printf '%s\n' "$lint" | grep -c ': error ' || true)
        [ "$count" -gt 0 ] && report \
          "Ultracite: $count problem(s) in $file it could not fix itself. Run: cd frontend && npx ultracite check" \
          "$(printf '%s\n' "$lint" | grep ': error ')"
        ;;
    esac
    case "$file" in
      *.ts)
        types=$(npm run --silent typecheck 2>&1) || report "Typecheck fails after this edit:" "$types"
        ;;
    esac
    ;;

  "$root"/backend/*.java)
    # One Maven run: format this file, then compile main and test code.
    out=$(./mvnw -B -ntp -q -pl backend spotless:apply test-compile "-DspotlessFiles=$file" 2>&1)
    if [ $? -ne 0 ]; then
      hint=""
      case "$out" in
        *tournament-leaderboard-frontend*) hint=" (the frontend jar is missing: run ./mvnw install -DskipTests once)" ;;
      esac
      report "Backend does not compile after this edit$hint:" \
        "$(printf '%s\n' "$out" | grep -E '^\[ERROR\]' || printf '%s\n' "$out")"
    fi
    ;;

  "$root"/legacy/*)
    case "$file" in
      *.ts | *.tsx) ;;
      *) exit 0 ;;
    esac
    cd "$root/legacy" || exit 0
    types=$(npx --no-install tsc --noEmit --incremental false 2>&1) || report "Typecheck fails after this edit (legacy):" "$types"
    ;;
esac

exit 0
