#!/usr/bin/env bash
set -euo pipefail

BLOCKED_PATTERNS=(
  '\.env$'
  '\.pem$'
  '\.key$'
  '\.p12$'
  '\.jks$'
  '(^|/)application-local\.properties$'
)

# Files that are useful locally (assignment briefs, scratch/session notes,
# ad-hoc exploration output) but aren't part of what this repo ships.
# Add new entries here *and* to .gitignore when you spot another one.
EXCLUDED_PATTERNS=(
  '(^|/)implementation_instruction\.md$'
)

check_blocked_files() {
  local files=("$@")
  local blocked=()
  if [ "${#files[@]}" -eq 0 ]; then
    return 0
  fi
  for f in "${files[@]}"; do
    for pattern in "${BLOCKED_PATTERNS[@]}"; do
      if [[ "$f" =~ $pattern ]]; then
        blocked+=("$f")
        break
      fi
    done
  done
  if [ "${#blocked[@]}" -gt 0 ]; then
    echo "Commit blocked: the following files look like local secrets/config and must not be tracked:" >&2
    printf '  %s\n' "${blocked[@]}" >&2
    echo "Remove them from the commit (git restore --staged <file>) and ensure they're gitignored." >&2
    return 1
  fi
}

check_excluded_files() {
  local files=("$@")
  local excluded=()
  if [ "${#files[@]}" -eq 0 ]; then
    return 0
  fi
  for f in "${files[@]}"; do
    for pattern in "${EXCLUDED_PATTERNS[@]}"; do
      if [[ "$f" =~ $pattern ]]; then
        excluded+=("$f")
        break
      fi
    done
  done
  if [ "${#excluded[@]}" -gt 0 ]; then
    echo "Commit blocked: the following files are not part of this repo's deliverable and must stay local-only:" >&2
    printf '  %s\n' "${excluded[@]}" >&2
    echo "Unstage them (git restore --staged <file>) — they're already covered in .gitignore." >&2
    return 1
  fi
}
