#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<EOF
Usage: scripts/render_k8s_overlay.sh <overlay> [terraform_env_dir]

Arguments:
  overlay            Target overlay: hml or prod
  terraform_env_dir  Terraform environment directory (default: infra/terraform/environments/dev)

Required environment variables:
  none by default (when using External Secrets)

Optional environment variables:
  USE_EXTERNAL_SECRETS    true/false (default: true)
  IMAGE_NAME              Optional image repository override applied to kustomization
  IMAGE_TAG               Optional image tag override applied to kustomization
  JWT_SECRET              required only when USE_EXTERNAL_SECRETS=false and secret tokens are present
  DB_PASSWORD             required only when USE_EXTERNAL_SECRETS=false and secret tokens are present
  SPRING_MAIL_USERNAME
  SPRING_MAIL_PASSWORD
  OUTPUT_DIR              Directory where rendered overlay will be written.
                          Default: /tmp/oficina-k8s-<overlay>-<pid>

Examples:
  JWT_SECRET=... DB_PASSWORD=... scripts/render_k8s_overlay.sh hml
  JWT_SECRET=... DB_PASSWORD=... scripts/render_k8s_overlay.sh prod infra/terraform/environments/dev
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

for cmd in terraform; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "ERROR: command '$cmd' is required but not installed." >&2
    exit 1
  fi
done

USE_EXTERNAL_SECRETS="${USE_EXTERNAL_SECRETS:-true}"

DB_ENDPOINT="$(terraform -chdir="$TF_ENV_DIR" output -raw db_endpoint)"
DB_PORT="$(terraform -chdir="$TF_ENV_DIR" output -raw db_port)"
DB_NAME="$(terraform -chdir="$TF_ENV_DIR" output -raw db_name)"
DB_USERNAME="$(terraform -chdir="$TF_ENV_DIR" output -raw db_username)"

OUTPUT_DIR="${OUTPUT_DIR:-/tmp/oficina-k8s-$OVERLAY-$$}"
SOURCE_OVERLAY_DIR="$ROOT_DIR/k8s/overlays/$OVERLAY"

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cp -R "$SOURCE_OVERLAY_DIR"/* "$OUTPUT_DIR"/

SPRING_MAIL_USERNAME="${SPRING_MAIL_USERNAME:-}"
SPRING_MAIL_PASSWORD="${SPRING_MAIL_PASSWORD:-}"
JWT_SECRET_VALUE="${JWT_SECRET:-}"
DB_PASSWORD_VALUE="${DB_PASSWORD:-}"
IMAGE_NAME="${IMAGE_NAME:-}"
IMAGE_TAG="${IMAGE_TAG:-}"

replace_token() {
  local token="$1"
  local value="$2"
  local file="$3"
  sed -i "s|$token|$value|g" "$file"
}

for file in "$OUTPUT_DIR"/*.yaml; do
  [[ -f "$file" ]] || continue
  replace_token "__DB_ENDPOINT__" "$DB_ENDPOINT" "$file"
  replace_token "__DB_PORT__" "$DB_PORT" "$file"
  replace_token "__DB_NAME__" "$DB_NAME" "$file"
  replace_token "__DB_USERNAME__" "$DB_USERNAME" "$file"
  replace_token "__SPRING_MAIL_USERNAME__" "$SPRING_MAIL_USERNAME" "$file"
  replace_token "__SPRING_MAIL_PASSWORD__" "$SPRING_MAIL_PASSWORD" "$file"

  if grep -q "__JWT_SECRET__\|__DB_PASSWORD__" "$file"; then
    if [[ "$USE_EXTERNAL_SECRETS" == "true" ]]; then
      echo "ERROR: Found inline secret tokens in $file while USE_EXTERNAL_SECRETS=true." >&2
      echo "Adjust overlay or set USE_EXTERNAL_SECRETS=false with JWT_SECRET/DB_PASSWORD." >&2
      exit 1
    fi

    : "${JWT_SECRET_VALUE:?ERROR: JWT_SECRET is required when USE_EXTERNAL_SECRETS=false}"
    : "${DB_PASSWORD_VALUE:?ERROR: DB_PASSWORD is required when USE_EXTERNAL_SECRETS=false}"
    replace_token "__JWT_SECRET__" "$JWT_SECRET_VALUE" "$file"
    replace_token "__DB_PASSWORD__" "$DB_PASSWORD_VALUE" "$file"
  fi
done

KUSTOMIZATION_FILE="$OUTPUT_DIR/kustomization.yaml"
if [[ -f "$KUSTOMIZATION_FILE" ]]; then
  if [[ -n "$IMAGE_NAME" ]]; then
    sed -i -E "s|(^[[:space:]]*newName:[[:space:]]*).*$|\1$IMAGE_NAME|" "$KUSTOMIZATION_FILE"
  fi
  if [[ -n "$IMAGE_TAG" ]]; then
    sed -i -E "s|(^[[:space:]]*newTag:[[:space:]]*).*$|\1$IMAGE_TAG|" "$KUSTOMIZATION_FILE"
  fi
fi

echo "Rendered overlay available at: $OUTPUT_DIR"
echo "Validate output: kubectl kustomize $OUTPUT_DIR"
echo "Apply output:    kubectl apply -k $OUTPUT_DIR"
