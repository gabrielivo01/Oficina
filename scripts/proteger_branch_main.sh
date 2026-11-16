#!/usr/bin/env bash
# Protege a branch main dos 3 microsserviços: merge só por pull request, com as checagens do CI obrigatórias
# (testes + SonarCloud Quality Gate e testes de integração) e sem push direto, inclusive para administradores.
#
# Uso: scripts/proteger_branch_main.sh
#   APROVACOES  número de aprovações exigidas no PR (padrão 0: projeto com um único mantenedor, que não pode
#               aprovar o próprio PR no GitHub; aumente quando houver revisores)
set -euo pipefail

APROVACOES="${APROVACOES:-0}"

repos=(
  "gabrielivo01/Oficina"
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
    "repos/${repo}/branches/main/protection" >/dev/null <<JSON
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["CI quality and unit tests", "Integration tests (Testcontainers)"]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": ${APROVACOES},
    "dismiss_stale_reviews": true
  },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
JSON
  gh api "repos/${repo}/branches/main/protection" \
    --jq '"  PR obrigatório: sim | aprovações: \(.required_pull_request_reviews.required_approving_review_count) | checagens: \(.required_status_checks.contexts | join(", ")) | admins incluídos: \(.enforce_admins.enabled)"'
done
