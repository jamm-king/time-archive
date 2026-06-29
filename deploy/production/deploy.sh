#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[deploy] %s\n' "$1"
}

fail() {
  printf '[deploy] ERROR: %s\n' "$1" >&2
  exit 1
}

require_image() {
  local name="$1"
  local value="${!name:-}"

  [[ -n "$value" ]] || fail "Required image is empty: $name"
  [[ "$value" != *:latest ]] || fail "Latest image tags are not allowed: $name"
  [[ "$value" != *replace-with* ]] || fail "Placeholder image is not deployable: $name"
}

if [[ $# -ne 1 ]]; then
  fail "Usage: $0 <staging|production>"
fi

ENVIRONMENT="$1"
[[ "$ENVIRONMENT" == "staging" || "$ENVIRONMENT" == "production" ]] ||
  fail "Environment must be staging or production"

require_image TIME_ARCHIVE_API_IMAGE
require_image TIME_ARCHIVE_WEB_IMAGE
require_image TIME_ARCHIVE_REDIS_IMAGE
require_image TIME_ARCHIVE_CLOUDFLARED_IMAGE

[[ "$TIME_ARCHIVE_API_IMAGE" =~ :[0-9a-f]{40}$ ]] ||
  fail "API image must use a full Git SHA tag"
[[ "$TIME_ARCHIVE_WEB_IMAGE" =~ :[0-9a-f]{40}$ ]] ||
  fail "Web image must use a full Git SHA tag"
[[ "$TIME_ARCHIVE_REDIS_IMAGE" =~ @sha256:[0-9a-f]{64}$ ]] ||
  fail "Redis image must use a SHA-256 digest"
[[ "$TIME_ARCHIVE_CLOUDFLARED_IMAGE" =~ @sha256:[0-9a-f]{64}$ ]] ||
  fail "cloudflared image must use a SHA-256 digest"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${TIME_ARCHIVE_COMPOSE_FILE:-$SCRIPT_DIR/docker-compose.yml}"
RUNTIME_ENV_FILE="${TIME_ARCHIVE_RUNTIME_ENV_FILE:-/run/time-archive/runtime.env}"
RELEASE_DIR="${TIME_ARCHIVE_RELEASE_DIR:-/var/lib/time-archive/deployments}"
PARAMETERS_JSON="${TIME_ARCHIVE_SSM_PARAMETERS_JSON:-}"

[[ -f "$COMPOSE_FILE" ]] || fail "Compose file not found: $COMPOSE_FILE"
install -d -m 700 "$RELEASE_DIR"

if [[ -n "$PARAMETERS_JSON" ]]; then
  "$SCRIPT_DIR/render-runtime-env.sh" \
    "$ENVIRONMENT" \
    "$RUNTIME_ENV_FILE" \
    "$PARAMETERS_JSON"
else
  "$SCRIPT_DIR/render-runtime-env.sh" \
    "$ENVIRONMENT" \
    "$RUNTIME_ENV_FILE"
fi

# shellcheck disable=SC1090
source "$RUNTIME_ENV_FILE"

if [[ "${TIME_ARCHIVE_SKIP_REGISTRY_LOGIN:-false}" != "true" ]]; then
  registry="${TIME_ARCHIVE_API_IMAGE%%/*}"
  [[ "${TIME_ARCHIVE_WEB_IMAGE%%/*}" == "$registry" ]] ||
    fail "API and Web images must use the same ECR registry"
  aws ecr get-login-password --region "$AWS_REGION" |
    docker login --username AWS --password-stdin "$registry" >/dev/null
fi

candidate_release="$(mktemp)"
trap 'rm -f "$candidate_release"' EXIT
chmod 600 "$candidate_release"
{
  printf 'export TIME_ARCHIVE_ENVIRONMENT=%q\n' "$ENVIRONMENT"
  printf 'export TIME_ARCHIVE_API_IMAGE=%q\n' "$TIME_ARCHIVE_API_IMAGE"
  printf 'export TIME_ARCHIVE_WEB_IMAGE=%q\n' "$TIME_ARCHIVE_WEB_IMAGE"
  printf 'export TIME_ARCHIVE_REDIS_IMAGE=%q\n' "$TIME_ARCHIVE_REDIS_IMAGE"
  printf 'export TIME_ARCHIVE_CLOUDFLARED_IMAGE=%q\n' "$TIME_ARCHIVE_CLOUDFLARED_IMAGE"
  printf 'export TIME_ARCHIVE_DEPLOYED_AT=%q\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "$candidate_release"

log "Pulling immutable deployment images"
docker compose -f "$COMPOSE_FILE" pull api web redis cloudflared migrate

log "Running controlled Flyway migration"
docker compose -f "$COMPOSE_FILE" --profile migration run --rm migrate

log "Starting $ENVIRONMENT services"
docker compose -f "$COMPOSE_FILE" up -d --remove-orphans api web redis cloudflared

TIME_ARCHIVE_COMPOSE_FILE="$COMPOSE_FILE" \
  "$SCRIPT_DIR/verify-deployment.sh"

if [[ -f "$RELEASE_DIR/current.env" ]]; then
  install -m 600 "$RELEASE_DIR/current.env" "$RELEASE_DIR/previous.env"
fi
install -m 600 "$candidate_release" "$RELEASE_DIR/current.env"
log "Deployment completed for $ENVIRONMENT"
