#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OPENAPI_FILE="${OPENAPI_FILE:-docs/api/openapi.yaml}"
REDOCLY_IMAGE="${REDOCLY_IMAGE:-redocly/cli:latest}"

log() {
  printf '[verify-openapi] %s\n' "$1"
}

fail() {
  printf '[verify-openapi] ERROR: %s\n' "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 is required"
}

require_command docker

if [[ ! -f "$REPO_ROOT/$OPENAPI_FILE" ]]; then
  fail "OpenAPI file not found: $OPENAPI_FILE"
fi

DOCKER_REPO_ROOT="$REPO_ROOT"
if command -v cygpath >/dev/null 2>&1; then
  DOCKER_REPO_ROOT="$(cygpath -w "$REPO_ROOT")"
fi

log "Validating $OPENAPI_FILE"
MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$DOCKER_REPO_ROOT:/work:ro" \
  -w /work \
  "$REDOCLY_IMAGE" \
  lint "$OPENAPI_FILE"
log "OpenAPI validation passed"
