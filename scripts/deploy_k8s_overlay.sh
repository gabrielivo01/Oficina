#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<EOF
Usage: scripts/deploy_k8s_overlay.sh <overlay> [terraform_env_dir]

Arguments:
  overlay            Target overlay: hml or prod
  terraform_env_dir  Terraform environment directory (default: infra/terraform/environments/dev)

Required environment variables:
  none by default (when using External Secrets)

Optional environment variables:
  USE_EXTERNAL_SECRETS    true/false (default: true)
  IMAGE_NAME              Optional image repository override used by rendered overlay
  IMAGE_TAG               Optional image tag override used by rendered overlay
  JWT_SECRET              required only when USE_EXTERNAL_SECRETS=false
  DB_PASSWORD             required only when USE_EXTERNAL_SECRETS=false
  SPRING_MAIL_USERNAME
  SPRING_MAIL_PASSWORD
  NAMESPACE           Kubernetes namespace (default: oficina)
  WAIT_FOR_ROLLOUT    Whether to wait for deployment rollout (default: true)
  RUN_SMOKE_TEST      Whether to run in-cluster health smoke test (default: true)
  SMOKE_TEST_URL      URL used by smoke pod (default: http://oficina-app/actuator/health)
  ROLLBACK_ON_SMOKE_FAIL  Whether to rollback app deployment when smoke test fails (default: true)
  AUTH_SMOKE_ENABLED      true/false (default: false)
  AUTH_SMOKE_LOGIN_URL    Login URL for auth smoke (default: http://oficina-app/auth/login)
  AUTH_SMOKE_TARGET_URL   Protected endpoint for auth smoke (default: http://oficina-app/ordens-servico)
  AUTH_SMOKE_LOGIN        Login user for auth smoke (default: admin)
  AUTH_SMOKE_PASSWORD     Password for auth smoke (default: admin123)
  DB_SMOKE_ENABLED        true/false (default: false)
  DB_SMOKE_URL            URL for DB health check (default: http://oficina-app/actuator/health/db)

Examples:
  DB_PASSWORD=... scripts/deploy_k8s_overlay.sh hml
  DB_PASSWORD=... scripts/deploy_k8s_overlay.sh prod infra/terraform/environments/dev
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

for cmd in kubectl terraform; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "ERROR: command '$cmd' is required but not installed." >&2
    exit 1
  fi
done

NAMESPACE="${NAMESPACE:-oficina}"
WAIT_FOR_ROLLOUT="${WAIT_FOR_ROLLOUT:-true}"
RUN_SMOKE_TEST="${RUN_SMOKE_TEST:-true}"
SMOKE_TEST_URL="${SMOKE_TEST_URL:-http://oficina-app/actuator/health}"
ROLLBACK_ON_SMOKE_FAIL="${ROLLBACK_ON_SMOKE_FAIL:-true}"
USE_EXTERNAL_SECRETS="${USE_EXTERNAL_SECRETS:-true}"
AUTH_SMOKE_ENABLED="${AUTH_SMOKE_ENABLED:-false}"
AUTH_SMOKE_LOGIN_URL="${AUTH_SMOKE_LOGIN_URL:-http://oficina-app/auth/login}"
AUTH_SMOKE_TARGET_URL="${AUTH_SMOKE_TARGET_URL:-http://oficina-app/ordens-servico}"
AUTH_SMOKE_LOGIN="${AUTH_SMOKE_LOGIN:-admin}"
AUTH_SMOKE_PASSWORD="${AUTH_SMOKE_PASSWORD:-admin123}"
DB_SMOKE_ENABLED="${DB_SMOKE_ENABLED:-false}"
DB_SMOKE_URL="${DB_SMOKE_URL:-http://oficina-app/actuator/health/db}"

if [[ "$USE_EXTERNAL_SECRETS" == "false" ]]; then
  : "${JWT_SECRET:?ERROR: JWT_SECRET is required when USE_EXTERNAL_SECRETS=false}"
  : "${DB_PASSWORD:?ERROR: DB_PASSWORD is required when USE_EXTERNAL_SECRETS=false}"
fi

RENDER_SCRIPT="$ROOT_DIR/scripts/render_k8s_overlay.sh"
OUTPUT_DIR="/tmp/oficina-k8s-deploy-$OVERLAY-$$"

rollback_if_enabled() {
  if [[ "$ROLLBACK_ON_SMOKE_FAIL" != "true" ]]; then
    echo "Smoke test failed and rollback is disabled (ROLLBACK_ON_SMOKE_FAIL=false)." >&2
    return 0
  fi

  echo "Smoke test failed. Rolling back deployment 'oficina-app' in namespace '$NAMESPACE'..." >&2
  kubectl -n "$NAMESPACE" rollout undo deployment/oficina-app || true
  kubectl -n "$NAMESPACE" rollout status deployment/oficina-app --timeout=300s || true
}

USE_EXTERNAL_SECRETS="$USE_EXTERNAL_SECRETS" \
IMAGE_NAME="${IMAGE_NAME:-}" \
IMAGE_TAG="${IMAGE_TAG:-}" \
JWT_SECRET="${JWT_SECRET:-}" \
DB_PASSWORD="${DB_PASSWORD:-}" \
SPRING_MAIL_USERNAME="${SPRING_MAIL_USERNAME:-}" \
SPRING_MAIL_PASSWORD="${SPRING_MAIL_PASSWORD:-}" \
OUTPUT_DIR="$OUTPUT_DIR" \
"$RENDER_SCRIPT" "$OVERLAY" "$TF_ENV_REL_PATH"

echo "Validating rendered kustomization from: $OUTPUT_DIR"
kubectl kustomize "$OUTPUT_DIR" > /dev/null

echo "Applying rendered overlay from: $OUTPUT_DIR"
kubectl apply -k "$OUTPUT_DIR"

if [[ "$WAIT_FOR_ROLLOUT" == "true" ]]; then
  echo "Waiting for deployment rollout in namespace '$NAMESPACE'..."
  kubectl -n "$NAMESPACE" rollout status deployment/oficina-app --timeout=300s
fi

if [[ "$RUN_SMOKE_TEST" == "true" ]]; then
  echo "Running smoke test against: $SMOKE_TEST_URL"
  if ! kubectl -n "$NAMESPACE" run oficina-smoke-$$ \
    --image=curlimages/curl:8.8.0 \
    --restart=Never \
    --rm -i \
    --command -- \
    sh -c "curl -fsS '$SMOKE_TEST_URL' > /dev/null"; then
    rollback_if_enabled
    exit 1
  fi

  if [[ "$AUTH_SMOKE_ENABLED" == "true" ]]; then
    echo "Running authenticated smoke test against: $AUTH_SMOKE_TARGET_URL"
    AUTH_SCRIPT="
set -eu
LOGIN_RESPONSE=\$(curl -fsS -H 'Content-Type: application/json' -X POST '$AUTH_SMOKE_LOGIN_URL' -d '{\"login\":\"$AUTH_SMOKE_LOGIN\",\"senha\":\"$AUTH_SMOKE_PASSWORD\"}')
TOKEN=\$(echo \"\$LOGIN_RESPONSE\" | sed -n 's/.*\"token\":\"\([^\"]*\)\".*/\1/p')
if [[ -z \"\$TOKEN\" ]]; then
  echo 'Could not extract JWT token from login response.' >&2
  exit 1
fi
curl -fsS -H \"Authorization: Bearer \$TOKEN\" '$AUTH_SMOKE_TARGET_URL' > /dev/null
"

    if ! kubectl -n "$NAMESPACE" run oficina-auth-smoke-$$ \
      --image=curlimages/curl:8.8.0 \
      --restart=Never \
      --rm -i \
      --command -- \
      sh -c "$AUTH_SCRIPT"; then
      rollback_if_enabled
      exit 1
    fi
  fi

  if [[ "$DB_SMOKE_ENABLED" == "true" ]]; then
    echo "Running database smoke test against: $DB_SMOKE_URL"
    if ! kubectl -n "$NAMESPACE" run oficina-db-smoke-$$ \
      --image=curlimages/curl:8.8.0 \
      --restart=Never \
      --rm -i \
      --command -- \
      sh -c "curl -fsS '$DB_SMOKE_URL' | grep -q '\"status\":\"UP\"'"; then
      rollback_if_enabled
      exit 1
    fi
  fi

  echo "Smoke test succeeded."
fi

echo "Deploy finished for overlay '$OVERLAY'."
