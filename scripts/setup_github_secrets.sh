#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"

if ! command -v gh >/dev/null 2>&1; then
  echo "ERROR: gh CLI is required." >&2
  exit 1
fi

echo "Configuring REQUIRED GitHub repository secrets for workflows."
echo "You will be prompted to enter each value securely."
echo

required=(
  AWS_REGION
  AWS_ROLE_TO_ASSUME
  EKS_CLUSTER_NAME
  DB_PASSWORD
)

for name in "${required[@]}"; do
  echo "Setting required secret: $name"
  gh secret set "$name"
done

echo
echo "Optional secrets for mail and authenticated smoke test."
echo "Press Enter without typing a value to skip an optional secret."
echo

optional=(
  SPRING_MAIL_USERNAME
  SPRING_MAIL_PASSWORD
  AUTH_SMOKE_LOGIN
  AUTH_SMOKE_PASSWORD
  AUTH_SMOKE_LOGIN_URL
  AUTH_SMOKE_TARGET_URL
)

for name in "${optional[@]}"; do
  read -r -p "Set optional secret $name? (y/N): " choice
  if [[ "$choice" =~ ^[Yy]$ ]]; then
    gh secret set "$name"
  fi
done

echo
echo "Configured secrets:"
gh secret list
