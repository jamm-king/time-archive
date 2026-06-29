#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-deployment-runtime] %s\n' "$1"
}

fail() {
  printf '[verify-staging-deployment-runtime] ERROR: %s\n' "$1" >&2
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
EXAMPLE_ENV="$ROOT_DIR/deploy/staging/runtime.env.example"
PARAMETERS_FIXTURE="$ROOT_DIR/deploy/staging/ssm-parameters.example.json"
RENDER_SCRIPT="$ROOT_DIR/deploy/production/render-runtime-env.sh"
DEPLOY_SCRIPT="$ROOT_DIR/deploy/production/deploy.sh"
VERIFY_SCRIPT="$ROOT_DIR/deploy/production/verify-deployment.sh"

for file in "$COMPOSE_FILE" "$EXAMPLE_ENV" "$PARAMETERS_FIXTURE" "$RENDER_SCRIPT" "$DEPLOY_SCRIPT" "$VERIFY_SCRIPT"; do
  [[ -f "$file" ]] || fail "Required staging deployment file is missing: $file"
done

for script in "$RENDER_SCRIPT" "$DEPLOY_SCRIPT" "$VERIFY_SCRIPT" "$0"; do
  bash -n "$script"
done
log "Shell syntax passed"

"$PYTHON_BIN" - "$PARAMETERS_FIXTURE" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, encoding="utf-8") as source:
    payload = json.load(source)

parameters = payload.get("Parameters")
if not isinstance(parameters, list):
    raise SystemExit("Parameters must be a list")

expected = {
    "/time-archive/staging/aws/region": "String",
    "/time-archive/staging/cloudwatch/log-group-prefix": "String",
    "/time-archive/staging/database/url": "String",
    "/time-archive/staging/database/username": "SecureString",
    "/time-archive/staging/database/password": "SecureString",
    "/time-archive/staging/r2/endpoint": "String",
    "/time-archive/staging/r2/presigned-url-endpoint": "String",
    "/time-archive/staging/r2/public-base-url": "String",
    "/time-archive/staging/r2/bucket": "String",
    "/time-archive/staging/r2/access-key": "SecureString",
    "/time-archive/staging/r2/secret-key": "SecureString",
    "/time-archive/staging/rate-limit/key-salt": "SecureString",
    "/time-archive/staging/rate-limit/client-ip-header": "String",
    "/time-archive/staging/cloudflare/tunnel-token": "SecureString",
}
actual = {item.get("Name"): item for item in parameters}

if set(actual) != set(expected):
    missing = sorted(set(expected) - set(actual))
    unexpected = sorted(set(actual) - set(expected))
    raise SystemExit(f"unexpected staging parameters; missing={missing}; unexpected={unexpected}")

for name, expected_type in expected.items():
    item = actual[name]
    if item.get("Type") != expected_type:
        raise SystemExit(f"unexpected type for {name}: {item.get('Type')}")
    value = item.get("Value")
    if not isinstance(value, str):
        raise SystemExit(f"missing placeholder value for {name}")
    if not value and name != "/time-archive/staging/rate-limit/client-ip-header":
        raise SystemExit(f"empty placeholder value is not allowed: {name}")
    if "\n" in value or "\r" in value:
        raise SystemExit(f"multiline value is not allowed: {name}")

print("staging SSM parameter fixture validation passed")
PY

RENDERED_ENV="$(mktemp)"
RENDERED_CONFIG_JSON="$(mktemp)"
trap 'rm -f "$RENDERED_ENV" "$RENDERED_CONFIG_JSON"' EXIT
chmod 600 "$RENDERED_ENV" "$RENDERED_CONFIG_JSON"

export TIME_ARCHIVE_API_IMAGE="000000000000.dkr.ecr.ap-northeast-2.amazonaws.com/time-archive-staging-api:$(printf 'a%.0s' {1..40})"
export TIME_ARCHIVE_WEB_IMAGE="000000000000.dkr.ecr.ap-northeast-2.amazonaws.com/time-archive-staging-web:$(printf 'b%.0s' {1..40})"
export TIME_ARCHIVE_REDIS_IMAGE="redis@sha256:$(printf 'c%.0s' {1..64})"
export TIME_ARCHIVE_CLOUDFLARED_IMAGE="cloudflare/cloudflared@sha256:$(printf 'd%.0s' {1..64})"

"$RENDER_SCRIPT" staging "$RENDERED_ENV" "$PARAMETERS_FIXTURE"

if [[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* ]]; then
  log "Skipping POSIX mode assertion on Windows Git Bash"
elif [[ "$(stat -c '%a' "$RENDERED_ENV")" != "600" ]]; then
  fail "Rendered runtime environment is not mode 600"
fi

# shellcheck disable=SC1090
source "$RENDERED_ENV"

[[ "$TIME_ARCHIVE_ENVIRONMENT" == "staging" ]] ||
  fail "Rendered environment is not staging"
[[ "$AWS_REGION" == "ap-northeast-2" ]] ||
  fail "Rendered AWS region is unexpected"
[[ "$TIME_ARCHIVE_CLOUDWATCH_LOG_GROUP_PREFIX" == "/time-archive/staging" ]] ||
  fail "Rendered CloudWatch log group prefix is unexpected"

docker compose -f "$COMPOSE_FILE" --profile migration config --format json > "$RENDERED_CONFIG_JSON"
"$PYTHON_BIN" - "$RENDERED_CONFIG_JSON" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    config = json.load(source)

services = config.get("services", {})
for required in ("api", "web", "redis", "cloudflared", "migrate"):
    if required not in services:
        raise SystemExit(f"missing service: {required}")

api = services["api"].get("environment", {})
if api.get("TIME_ARCHIVE_PAYMENT_FAKE_ENABLED") != "false":
    raise SystemExit("staging deployment must force fake payment off")
if api.get("TIME_ARCHIVE_INITIAL_ADMIN_EMAILS") not in ("", None):
    raise SystemExit("staging deployment must not use env-based admin bootstrap")
if api.get("SERVER_SERVLET_SESSION_COOKIE_SECURE") != "true":
    raise SystemExit("staging deployment must force secure session cookies")

for key in (
    "TIME_ARCHIVE_DATABASE_URL",
    "TIME_ARCHIVE_DATABASE_USERNAME",
    "TIME_ARCHIVE_DATABASE_PASSWORD",
    "TIME_ARCHIVE_STORAGE_S3_ACCESS_KEY",
    "TIME_ARCHIVE_STORAGE_S3_SECRET_KEY",
    "TIME_ARCHIVE_RATE_LIMIT_KEY_SALT",
):
    if not api.get(key):
        raise SystemExit(f"rendered staging API environment is missing {key}")

if services["api"].get("ports") or services["web"].get("ports") or services["redis"].get("ports"):
    raise SystemExit("staging deployment must not publish host ports")

print("staging deployment compose rendering validation passed")
PY

log "Staging deployment runtime validation passed"
