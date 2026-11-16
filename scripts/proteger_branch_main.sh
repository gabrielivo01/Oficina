#!/usr/bin/env bash
set -euo pipefail

repos=(
  "gabrielivo01/oficina"
  "gabrielivo01/oficina-execution-service"
  "gabrielivo01/oficina-billing-service"
)

command -v gh >/dev/null 2>&1 || {
  echo "gh CLI é obrigatório." >&2
  exit 1
}
gh auth status >/dev/null

for repo in "${repos[@]}"; do
  echo "Aplicando proteção na branch main de ${repo}..."
  gh api \
    --method PUT \
    --input - \
    "repos/${repo}/branches/main/protection" <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["CI / CI quality and unit tests"]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true
  },
  "restrictions": null
}
JSON
done
