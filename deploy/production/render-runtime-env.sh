#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[render-runtime-env] %s\n' "$1"
}

fail() {
  printf '[render-runtime-env] ERROR: %s\n' "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

require_external_value() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "Required deployment value is empty: $name"
}

parameter_value() {
  local relative_name="$1"
  local full_name="$PARAMETER_PATH/$relative_name"

  DEPLOYMENT_ENVIRONMENT="$ENVIRONMENT" \
    PARAMETER_RELATIVE_NAME="$relative_name" \
    "$PYTHON_BIN" -c '
import json
import os
import sys

payload = json.load(sys.stdin)
parameter_name = "/time-archive/{}/{}".format(
    os.environ["DEPLOYMENT_ENVIRONMENT"],
    os.environ["PARAMETER_RELATIVE_NAME"],
)
matches = [
    item
    for item in payload["Parameters"]
    if item.get("Name") == parameter_name
]
if len(matches) != 1:
    raise SystemExit(1)

value = matches[0].get("Value")
if not isinstance(value, str):
    raise SystemExit("parameter value must be a string")
sys.stdout.write(value)
' < "$PARAMETERS_FILE"
}

write_required_parameter() {
  local variable_name="$1"
  local relative_name="$2"
  local value

  if ! value="$(parameter_value "$relative_name")"; then
    fail "Required SSM parameter is missing or duplicated: $PARAMETER_PATH/$relative_name"
  fi
  write_export "$variable_name" "$value"
}

write_optional_parameter() {
  local variable_name="$1"
  local relative_name="$2"
  local default_value="$3"
  local value

  if ! value="$(parameter_value "$relative_name")"; then
    value="$default_value"
  fi
  write_export "$variable_name" "$value"
}

write_export() {
  local name="$1"
  local value="$2"

  [[ "$value" != *$'\n'* && "$value" != *$'\r'* ]] ||
    fail "Multiline values are not supported: $name"
  printf 'export %s=%q\n' "$name" "$value" >> "$OUTPUT_TEMP_FILE"
}

if [[ $# -lt 2 || $# -gt 3 ]]; then
  fail "Usage: $0 <staging|production> <output-file> [parameters-json]"
fi

ENVIRONMENT="$1"
OUTPUT_FILE="$2"
INPUT_PARAMETERS_FILE="${3:-}"

[[ "$ENVIRONMENT" == "staging" || "$ENVIRONMENT" == "production" ]] ||
  fail "Environment must be staging or production"

if command -v python3 >/dev/null 2>&1 && python3 -c 'import json' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'import json' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  fail "python3 or python is required"
fi
require_external_value TIME_ARCHIVE_API_IMAGE
require_external_value TIME_ARCHIVE_WEB_IMAGE
require_external_value TIME_ARCHIVE_REDIS_IMAGE
require_external_value TIME_ARCHIVE_CLOUDFLARED_IMAGE

PARAMETER_PATH="/time-archive/$ENVIRONMENT"
PARAMETERS_FILE="$(mktemp)"
OUTPUT_TEMP_FILE="$(mktemp)"
trap 'rm -f "$PARAMETERS_FILE" "$OUTPUT_TEMP_FILE"' EXIT
chmod 600 "$PARAMETERS_FILE" "$OUTPUT_TEMP_FILE"

if [[ -n "$INPUT_PARAMETERS_FILE" ]]; then
  [[ -f "$INPUT_PARAMETERS_FILE" ]] || fail "Parameters JSON not found: $INPUT_PARAMETERS_FILE"
  cp "$INPUT_PARAMETERS_FILE" "$PARAMETERS_FILE"
else
  require_command aws
  aws ssm get-parameters-by-path \
    --path "$PARAMETER_PATH/" \
    --recursive \
    --with-decryption \
    --output json > "$PARAMETERS_FILE"
fi

if ! "$PYTHON_BIN" -c '
import json
import sys

payload = json.load(sys.stdin)

if not isinstance(payload.get("Parameters"), list):
    raise SystemExit("SSM response does not contain a Parameters array")
' < "$PARAMETERS_FILE"
then
  fail "SSM response does not contain a valid Parameters array"
fi

write_export TIME_ARCHIVE_ENVIRONMENT "$ENVIRONMENT"
write_export TIME_ARCHIVE_API_IMAGE "$TIME_ARCHIVE_API_IMAGE"
write_export TIME_ARCHIVE_WEB_IMAGE "$TIME_ARCHIVE_WEB_IMAGE"
write_export TIME_ARCHIVE_REDIS_IMAGE "$TIME_ARCHIVE_REDIS_IMAGE"
write_export TIME_ARCHIVE_CLOUDFLARED_IMAGE "$TIME_ARCHIVE_CLOUDFLARED_IMAGE"
write_required_parameter AWS_REGION aws/region
write_required_parameter TIME_ARCHIVE_CLOUDWATCH_LOG_GROUP_PREFIX cloudwatch/log-group-prefix
write_required_parameter TIME_ARCHIVE_DATABASE_URL database/url
write_required_parameter TIME_ARCHIVE_DATABASE_USERNAME database/username
write_required_parameter TIME_ARCHIVE_DATABASE_PASSWORD database/password
write_required_parameter TIME_ARCHIVE_STORAGE_S3_ENDPOINT r2/endpoint
write_required_parameter TIME_ARCHIVE_STORAGE_S3_PRESIGNED_URL_ENDPOINT r2/presigned-url-endpoint
write_required_parameter TIME_ARCHIVE_STORAGE_S3_PUBLIC_BASE_URL r2/public-base-url
write_required_parameter TIME_ARCHIVE_STORAGE_S3_BUCKET r2/bucket
write_optional_parameter TIME_ARCHIVE_STORAGE_S3_REGION r2/region auto
write_required_parameter TIME_ARCHIVE_STORAGE_S3_ACCESS_KEY r2/access-key
write_required_parameter TIME_ARCHIVE_STORAGE_S3_SECRET_KEY r2/secret-key
write_optional_parameter TIME_ARCHIVE_STORAGE_S3_PATH_STYLE_ACCESS r2/path-style-access true
write_required_parameter TIME_ARCHIVE_RATE_LIMIT_KEY_SALT rate-limit/key-salt
write_optional_parameter TIME_ARCHIVE_RATE_LIMIT_CLIENT_IP_HEADER rate-limit/client-ip-header ''
write_required_parameter TIME_ARCHIVE_CLOUDFLARE_TUNNEL_TOKEN cloudflare/tunnel-token

if [[ ! -d "$(dirname "$OUTPUT_FILE")" ]]; then
  install -d -m 700 "$(dirname "$OUTPUT_FILE")"
fi
install -m 600 "$OUTPUT_TEMP_FILE" "$OUTPUT_FILE"
log "Rendered runtime environment for $ENVIRONMENT"
