#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"

chmod +x .githooks/pre-commit .githooks/pre-merge-commit .githooks/pre-push .githooks/lib/check-blocked-files.sh
git config core.hooksPath .githooks
echo "Git hooks installed (core.hooksPath=.githooks)."
