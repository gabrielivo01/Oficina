#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<EOF
Usage: scripts/ci_deploy.sh <overlay> [terraform_env_dir]

Arguments:
  overlay            Target overlay: hml or prod
  terraform_env_dir  Terraform environment directory (default: infra/terraform/environments/dev)

Required environment variables:
  DB_PASSWORD

Optional environment variables:
  USE_EXTERNAL_SECRETS       true/false (default: true)
  JWT_SECRET                 required only when USE_EXTERNAL_SECRETS=false
  SPRING_MAIL_USERNAME
  SPRING_MAIL_PASSWORD
  AUTH_SMOKE_ENABLED         true/false (default: false)
  AUTH_SMOKE_LOGIN_URL       login URL used in auth smoke
  AUTH_SMOKE_TARGET_URL      protected URL used in auth smoke
  AUTH_SMOKE_LOGIN           login user used in auth smoke
  AUTH_SMOKE_PASSWORD        password used in auth smoke
  DB_SMOKE_ENABLED           true/false (default: false)
  DB_SMOKE_URL               URL for DB health smoke (default in deploy script)
  NAMESPACE                  Kubernetes namespace (default: oficina)
  TERRAFORM_AUTO_APPLY       true/false (default: false)
  WAIT_FOR_ROLLOUT           true/false (default: true)
  RUN_SMOKE_TEST             true/false (default: true)
  TF_VAR_db_password         if TERRAFORM_AUTO_APPLY=true and db_password variable is needed

Examples:
  JWT_SECRET=... DB_PASSWORD=... scripts/ci_deploy.sh hml
  TERRAFORM_AUTO_APPLY=true JWT_SECRET=... DB_PASSWORD=... TF_VAR_db_password=... scripts/ci_deploy.sh prod infra/terraform/environments/dev
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

OVERLAY="${1:-}"
TF_ENV_REL_PATH="${2:-infra/terraform/environments/dev}"

if [[ -z "$OVERLAY" ]]; then
  echo "ERROR: overlay is required." >&2
  usage
  exit 1
fi

if [[ "$OVERLAY" != "hml" && "$OVERLAY" != "prod" ]]; then
  echo "ERROR: overlay must be 'hml' or 'prod'." >&2
  exit 1
fi

TF_ENV_DIR="$ROOT_DIR/$TF_ENV_REL_PATH"
if [[ ! -d "$TF_ENV_DIR" ]]; then
  echo "ERROR: terraform environment directory not found: $TF_ENV_DIR" >&2
  exit 1
fi

for cmd in terraform kubectl; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "ERROR: command '$cmd' is required but not installed." >&2
    exit 1
  fi
done

: "${DB_PASSWORD:?ERROR: DB_PASSWORD is required}"

NAMESPACE="${NAMESPACE:-oficina}"
TERRAFORM_AUTO_APPLY="${TERRAFORM_AUTO_APPLY:-false}"
WAIT_FOR_ROLLOUT="${WAIT_FOR_ROLLOUT:-true}"
RUN_SMOKE_TEST="${RUN_SMOKE_TEST:-true}"
USE_EXTERNAL_SECRETS="${USE_EXTERNAL_SECRETS:-true}"

if [[ "$USE_EXTERNAL_SECRETS" == "false" ]]; then
  : "${JWT_SECRET:?ERROR: JWT_SECRET is required when USE_EXTERNAL_SECRETS=false}"
fi

echo "Running Terraform init in $TF_ENV_REL_PATH"
terraform -chdir="$TF_ENV_DIR" init -input=false

echo "Running Terraform fmt check"
terraform -chdir="$TF_ENV_DIR" fmt -check

echo "Running Terraform validate"
terraform -chdir="$TF_ENV_DIR" validate

echo "Running Terraform plan"
terraform -chdir="$TF_ENV_DIR" plan -input=false -out=tfplan

if [[ "$TERRAFORM_AUTO_APPLY" == "true" ]]; then
  echo "Applying Terraform plan"
  terraform -chdir="$TF_ENV_DIR" apply -input=false -auto-approve tfplan
else
  echo "Skipping Terraform apply (TERRAFORM_AUTO_APPLY=false)."
fi

echo "Starting Kubernetes deploy for overlay '$OVERLAY'"
USE_EXTERNAL_SECRETS="$USE_EXTERNAL_SECRETS" \
JWT_SECRET="${JWT_SECRET:-}" \
DB_PASSWORD="$DB_PASSWORD" \
SPRING_MAIL_USERNAME="${SPRING_MAIL_USERNAME:-}" \
SPRING_MAIL_PASSWORD="${SPRING_MAIL_PASSWORD:-}" \
AUTH_SMOKE_ENABLED="${AUTH_SMOKE_ENABLED:-false}" \
AUTH_SMOKE_LOGIN_URL="${AUTH_SMOKE_LOGIN_URL:-}" \
AUTH_SMOKE_TARGET_URL="${AUTH_SMOKE_TARGET_URL:-}" \
AUTH_SMOKE_LOGIN="${AUTH_SMOKE_LOGIN:-}" \
AUTH_SMOKE_PASSWORD="${AUTH_SMOKE_PASSWORD:-}" \
DB_SMOKE_ENABLED="${DB_SMOKE_ENABLED:-false}" \
DB_SMOKE_URL="${DB_SMOKE_URL:-}" \
NAMESPACE="$NAMESPACE" \
WAIT_FOR_ROLLOUT="$WAIT_FOR_ROLLOUT" \
RUN_SMOKE_TEST="$RUN_SMOKE_TEST" \
"$ROOT_DIR/scripts/deploy_k8s_overlay.sh" "$OVERLAY" "$TF_ENV_REL_PATH"

echo "CI deploy flow finished for overlay '$OVERLAY'."
