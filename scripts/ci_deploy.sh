#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<EOF
Usage: scripts/ci_deploy.sh <overlay>

Arguments:
  overlay  Target overlay: demo, hml or prod

Deploys the oficina-app Kubernetes overlay to a cluster that already exists
(provisioned by the oficina-infra-k8s repo) against a database that already
exists (provisioned by the oficina-infra-db repo). This repository owns no
Terraform state of its own after the 4-repository split — it only renders
and applies k8s manifests.

Required environment variables:
  DB_ENDPOINT, DB_PORT, DB_NAME, DB_USERNAME
    RDS connection details, from the oficina-infra-db repo's Terraform outputs.

Optional environment variables:
  USE_EXTERNAL_SECRETS       true/false (default: true)
  IMAGE_NAME                 Optional image repository override for deploy
  IMAGE_TAG                  Optional image tag override for deploy
  JWT_SECRET                 required only when USE_EXTERNAL_SECRETS=false
  DB_PASSWORD                required only when USE_EXTERNAL_SECRETS=false
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
  WAIT_FOR_ROLLOUT           true/false (default: true)
  RUN_SMOKE_TEST             true/false (default: true)

Examples:
  DB_ENDPOINT=... DB_PORT=5432 DB_NAME=oficina_db DB_USERNAME=postgres \\
    JWT_SECRET=... scripts/ci_deploy.sh hml
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

OVERLAY="${1:-}"

if [[ -z "$OVERLAY" ]]; then
  echo "ERROR: overlay is required." >&2
  usage
  exit 1
fi

if [[ "$OVERLAY" != "demo" && "$OVERLAY" != "hml" && "$OVERLAY" != "prod" ]]; then
  echo "ERROR: overlay must be 'demo', 'hml' or 'prod'." >&2
  exit 1
fi

for cmd in kubectl; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "ERROR: command '$cmd' is required but not installed." >&2
    exit 1
  fi
done

: "${DB_ENDPOINT:?ERROR: DB_ENDPOINT is required (see oficina-infra-db repo outputs)}"
: "${DB_PORT:?ERROR: DB_PORT is required (see oficina-infra-db repo outputs)}"
: "${DB_NAME:?ERROR: DB_NAME is required (see oficina-infra-db repo outputs)}"
: "${DB_USERNAME:?ERROR: DB_USERNAME is required (see oficina-infra-db repo outputs)}"

NAMESPACE="${NAMESPACE:-oficina}"
WAIT_FOR_ROLLOUT="${WAIT_FOR_ROLLOUT:-true}"
RUN_SMOKE_TEST="${RUN_SMOKE_TEST:-true}"
USE_EXTERNAL_SECRETS="${USE_EXTERNAL_SECRETS:-true}"

if [[ "$USE_EXTERNAL_SECRETS" == "false" ]]; then
  : "${JWT_SECRET:?ERROR: JWT_SECRET is required when USE_EXTERNAL_SECRETS=false}"
fi

echo "Starting Kubernetes deploy for overlay '$OVERLAY'"
USE_EXTERNAL_SECRETS="$USE_EXTERNAL_SECRETS" \
IMAGE_NAME="${IMAGE_NAME:-}" \
IMAGE_TAG="${IMAGE_TAG:-}" \
JWT_SECRET="${JWT_SECRET:-}" \
DB_PASSWORD="${DB_PASSWORD:-}" \
DB_ENDPOINT="$DB_ENDPOINT" \
DB_PORT="$DB_PORT" \
DB_NAME="$DB_NAME" \
DB_USERNAME="$DB_USERNAME" \
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
"$ROOT_DIR/scripts/deploy_k8s_overlay.sh" "$OVERLAY"

echo "CI deploy flow finished for overlay '$OVERLAY'."
