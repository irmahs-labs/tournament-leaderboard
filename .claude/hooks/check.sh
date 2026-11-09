#!/usr/bin/env bash
# PostToolUse hook for Write|Edit, adapted from irmahs-labs/template's
# ultracite.sh for the three parts of this repo. On the one file just written:
#   frontend/  Ultracite (Oxlint with the anti-slop plugin, and Oxfmt), then the typecheck
#   backend/   Spotless (palantir-java-format), then compiling main and test code
#
# legacy/ is frozen: nobody edits it, so it isn't checked.
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

# Never dependencies or build output: whatever git ignores.
git check-ignore -q "$file" && exit 0

report() {
  echo "$1" >&2
  printf '%s\n' "$2" | head -n 40 >&2
  exit 2
}

case "$file" in
  "$root"/frontend/*.ts | "$root"/frontend/*.js | "$root"/frontend/*.mjs | "$root"/frontend/*.cjs | \
  "$root"/frontend/*.json | "$root"/frontend/*.jsonc | "$root"/frontend/*.css | \
  "$root"/frontend/*.md | "$root"/frontend/*.html)
    cd "$root/frontend" || exit 0
    npx --no-install ultracite fix "$file" > /dev/null 2>&1

    # Oxlint only lints script files; the rest is formatted above and that is all.
    if [[ $file =~ \.(ts|js|mjs|cjs)$ ]] && ! lint=$(npx --no-install ultracite check "$file" 2>&1); then
      report "Ultracite: problems in $file it could not fix itself. Run: cd frontend && npx ultracite check" \
        "$(printf '%s\n' "$lint" | grep ': error ')"
    fi
    if [[ $file == *.ts ]] && ! types=$(npm run --silent typecheck 2>&1); then
      report "Typecheck fails after this edit:" "$types"
    fi
    ;;

  "$root"/backend/*.java)
    # One Maven run: format this file, then compile main and test code.
    if ! out=$(./mvnw -B -ntp -q -pl backend spotless:apply test-compile "-DspotlessFiles=$file" 2>&1); then
      report "Backend does not compile after this edit:" "$(printf '%s\n' "$out" | grep -E '^\[ERROR\]')"
    fi
    ;;

esac

exit 0
