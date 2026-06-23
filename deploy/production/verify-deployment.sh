#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-deployment] %s\n' "$1"
}

fail() {
  printf '[verify-deployment] ERROR: %s\n' "$1" >&2
  exit 1
}

COMPOSE_FILE="${TIME_ARCHIVE_COMPOSE_FILE:-/opt/time-archive/deploy/docker-compose.yml}"
PUBLIC_BASE_URL="${TIME_ARCHIVE_PUBLIC_BASE_URL:-}"

[[ -f "$COMPOSE_FILE" ]] || fail "Compose file not found: $COMPOSE_FILE"

running_services="$(docker compose -f "$COMPOSE_FILE" ps --status running --services)"
for service in api web redis cloudflared; do
  grep -Fxq "$service" <<< "$running_services" || fail "Service is not running: $service"
done

docker compose -f "$COMPOSE_FILE" exec -T api \
  wget -q -O - http://localhost:8080/actuator/health >/dev/null
docker compose -f "$COMPOSE_FILE" exec -T web \
  wget -q -O - http://localhost:3000 >/dev/null
docker compose -f "$COMPOSE_FILE" exec -T redis redis-cli ping |
  grep -Fxq PONG || fail "Redis PING failed"

if [[ -n "$PUBLIC_BASE_URL" ]]; then
  command -v curl >/dev/null 2>&1 || fail "curl is required for public verification"
  curl --fail --silent --show-error "$PUBLIC_BASE_URL" >/dev/null
  curl --fail --silent --show-error \
    "$PUBLIC_BASE_URL/api/timeline?from=0&to=1" >/dev/null
fi

log "Private and public deployment checks passed"
