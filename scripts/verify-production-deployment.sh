#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-production-deployment] %s\n' "$1"
}

fail() {
  printf '[verify-production-deployment] ERROR: %s\n' "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

require_command bash
require_command docker

PYTHON_BIN=""
if command -v python3 >/dev/null 2>&1 && python3 -c 'import json' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'import json' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  fail "python3 or python is required"
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/deploy/production/docker-compose.yml"
EXAMPLE_ENV="$ROOT_DIR/deploy/production/runtime.env.example"
PARAMETERS_FIXTURE="$ROOT_DIR/deploy/production/ssm-parameters.example.json"
RENDER_SCRIPT="$ROOT_DIR/deploy/production/render-runtime-env.sh"
DEPLOY_SCRIPT="$ROOT_DIR/deploy/production/deploy.sh"

for file in "$COMPOSE_FILE" "$EXAMPLE_ENV" "$PARAMETERS_FIXTURE" "$RENDER_SCRIPT" "$DEPLOY_SCRIPT"; do
  [[ -f "$file" ]] || fail "Required production deployment file is missing: $file"
done

for script in "$ROOT_DIR"/deploy/production/*.sh "$ROOT_DIR"/scripts/*.sh; do
  bash -n "$script"
done
log "Shell syntax passed"

# shellcheck disable=SC1090
source "$EXAMPLE_ENV"

CONFIG_JSON="$(mktemp)"
RENDERED_ENV="$(mktemp)"
RENDERED_CONFIG_JSON="$(mktemp)"
EMPTY_ENV="$(mktemp)"
trap 'rm -f "$CONFIG_JSON" "$RENDERED_ENV" "$RENDERED_CONFIG_JSON" "$EMPTY_ENV"' EXIT
chmod 600 "$CONFIG_JSON" "$RENDERED_ENV" "$RENDERED_CONFIG_JSON" "$EMPTY_ENV"

docker compose -f "$COMPOSE_FILE" --profile migration config --format json > "$CONFIG_JSON"

"$PYTHON_BIN" - "$CONFIG_JSON" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, encoding="utf-8") as source:
    config = json.load(source)

services = config.get("services", {})
expected = {"api", "web", "redis", "cloudflared", "migrate"}
if set(services) != expected:
    raise SystemExit(f"unexpected production services: {sorted(services)}")

for forbidden in ("postgres", "minio", "minio-init"):
    if forbidden in services:
        raise SystemExit(f"local-only service is present: {forbidden}")

for name, service in services.items():
    if "build" in service:
        raise SystemExit(f"production service contains build configuration: {name}")
    if service.get("ports"):
        raise SystemExit(f"production service publishes host ports: {name}")
    image = service.get("image", "")
    if not image or image.endswith(":latest"):
        raise SystemExit(f"production service does not use a pinned image reference: {name}")
    logging = service.get("logging", {})
    if logging.get("driver") != "awslogs":
        raise SystemExit(f"production service does not use awslogs: {name}")

for name in ("redis", "cloudflared"):
    image = services[name].get("image", "")
    digest = image.rpartition("@sha256:")[2]
    if len(digest) != 64 or any(character not in "0123456789abcdef" for character in digest):
        raise SystemExit(f"third-party production image is not digest-pinned: {name}")

api_environment = services["api"].get("environment", {})
if api_environment.get("TIME_ARCHIVE_PAYMENT_FAKE_ENABLED") != "false":
    raise SystemExit("fake payments are not forced off")
if api_environment.get("TIME_ARCHIVE_INITIAL_ADMIN_EMAILS") not in ("", None):
    raise SystemExit("environment-based production admin bootstrap is enabled")
if api_environment.get("SPRING_FLYWAY_ENABLED") != "false":
    raise SystemExit("API runtime must not execute Flyway")
if api_environment.get("SERVER_SERVLET_SESSION_COOKIE_SECURE") != "true":
    raise SystemExit("secure session cookies are not forced on")

migrate = services["migrate"]
if "migration" not in migrate.get("profiles", []):
    raise SystemExit("migration service is not profile-gated")
if migrate.get("environment", {}).get("SPRING_FLYWAY_ENABLED") != "true":
    raise SystemExit("migration service does not enable Flyway")

sensitive_prefixes = (
    "TIME_ARCHIVE_DATABASE_",
    "TIME_ARCHIVE_STORAGE_S3_",
    "TIME_ARCHIVE_RATE_LIMIT_KEY_SALT",
)
for name in ("web", "redis", "cloudflared"):
    environment = services[name].get("environment", {})
    leaked = [
        key for key in environment
        if key.startswith(sensitive_prefixes)
    ]
    if leaked:
        raise SystemExit(f"sensitive API environment leaked to {name}: {leaked}")

cloudflared_environment = services["cloudflared"].get("environment", {})
if set(cloudflared_environment) != {"TUNNEL_TOKEN"}:
    raise SystemExit("cloudflared environment contains unexpected values")

redis_command = " ".join(services["redis"].get("command", []))
for required in ("--appendonly yes", "--maxmemory 256mb", "--maxmemory-policy noeviction"):
    if required not in redis_command:
        raise SystemExit(f"Redis production policy is missing: {required}")

print("production compose policy validation passed")
PY

if (
  unset COMPOSE_ENV_FILES
  unset TIME_ARCHIVE_DATABASE_PASSWORD
  docker compose \
    --env-file "$EMPTY_ENV" \
    -f "$COMPOSE_FILE" \
    --profile migration \
    config \
    --quiet >/dev/null 2>&1
); then
  fail "Compose accepted a missing required database password"
fi
log "Missing secret fail-fast validation passed"

if (
  export TIME_ARCHIVE_API_IMAGE="registry.example/api:$(printf 'a%.0s' {1..40})"
  export TIME_ARCHIVE_WEB_IMAGE="registry.example/web:$(printf 'b%.0s' {1..40})"
  export TIME_ARCHIVE_REDIS_IMAGE=redis:8-alpine
  export TIME_ARCHIVE_CLOUDFLARED_IMAGE="cloudflare/cloudflared@sha256:$(printf 'c%.0s' {1..64})"
  "$DEPLOY_SCRIPT" production >/dev/null 2>&1
); then
  fail "Deployment accepted a mutable Redis image tag"
fi
log "Immutable infrastructure image validation passed"

"$RENDER_SCRIPT" production "$RENDERED_ENV" "$PARAMETERS_FIXTURE"

if [[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* ]]; then
  log "Skipping POSIX mode assertion on Windows Git Bash"
elif [[ "$(stat -c '%a' "$RENDERED_ENV")" != "600" ]]; then
  fail "Rendered runtime environment is not mode 600"
fi

# shellcheck disable=SC1090
source "$RENDERED_ENV"
docker compose -f "$COMPOSE_FILE" --profile migration config --format json > "$RENDERED_CONFIG_JSON"
"$PYTHON_BIN" - "$RENDERED_CONFIG_JSON" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    config = json.load(source)

api = config["services"]["api"]["environment"]
required = {
    "TIME_ARCHIVE_DATABASE_URL",
    "TIME_ARCHIVE_DATABASE_USERNAME",
    "TIME_ARCHIVE_DATABASE_PASSWORD",
    "TIME_ARCHIVE_STORAGE_S3_ACCESS_KEY",
    "TIME_ARCHIVE_STORAGE_S3_SECRET_KEY",
    "TIME_ARCHIVE_RATE_LIMIT_KEY_SALT",
}
missing = sorted(key for key in required if not api.get(key))
if missing:
    raise SystemExit(f"rendered environment is missing API values: {missing}")

print("SSM fixture rendering validation passed")
PY

log "Production deployment validation passed"
