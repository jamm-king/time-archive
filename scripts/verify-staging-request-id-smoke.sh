#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-request-id-smoke] %s\n' "$1"
}

fail() {
  printf '[verify-staging-request-id-smoke] ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: scripts/verify-staging-request-id-smoke.sh [--base-url URL] [--request-id ID]

Verifies X-Request-Id propagation through the public staging HTTPS hostname.

Options:
  --base-url URL   Public staging base URL. Falls back to STAGING_PUBLIC_BASE_URL.
  --request-id ID  Request ID to send. Defaults to a generated smoke value.
  -h, --help       Show this help.
USAGE
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

detect_python() {
  if command -v python3 >/dev/null 2>&1 && python3 -c 'import json' >/dev/null 2>&1; then
    printf 'python3'
    return
  fi
  if command -v python >/dev/null 2>&1 && python -c 'import json' >/dev/null 2>&1; then
    printf 'python'
    return
  fi
  fail "Required command not found: python3 or python"
}

header_value() {
  local header_file="$1"
  local header_name="$2"

  "$PYTHON_BIN" - "$header_file" "$header_name" <<'PY'
import sys

path, wanted = sys.argv[1], sys.argv[2].lower()
value = ""
with open(path, encoding="iso-8859-1") as source:
    for raw_line in source:
        line = raw_line.rstrip("\r\n")
        if ":" not in line:
            continue
        name, raw_value = line.split(":", 1)
        if name.lower() == wanted:
            value = raw_value.strip()
print(value)
PY
}

json_get() {
  local path="$1"
  "$PYTHON_BIN" - "$path" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    payload = json.load(source)
print(payload.get("requestId", ""))
PY
}

BASE_URL="${STAGING_PUBLIC_BASE_URL:-}"
REQUEST_ID="staging-request-id-smoke-$(date -u +%Y%m%d%H%M%S)"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      [[ $# -ge 2 ]] || fail "--base-url requires a value"
      BASE_URL="$2"
      shift 2
      ;;
    --request-id)
      [[ $# -ge 2 ]] || fail "--request-id requires a value"
      REQUEST_ID="$2"
      shift 2
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown argument: $1"
      ;;
  esac
done

[[ -n "$BASE_URL" ]] || fail "Set --base-url or STAGING_PUBLIC_BASE_URL"
[[ "$BASE_URL" =~ ^https:// ]] || fail "Base URL must use HTTPS"
[[ "$REQUEST_ID" =~ ^[A-Za-z0-9._-]{8,128}$ ]] || fail "Request ID must be 8-128 safe token characters"
BASE_URL="${BASE_URL%/}"

require_command curl
PYTHON_BIN="$(detect_python)"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

success_headers="$TMP_DIR/success.headers"
success_body="$TMP_DIR/success.body"
error_headers="$TMP_DIR/error.headers"
error_body="$TMP_DIR/error.body"

log "Using BASE_URL=$BASE_URL"
log "Using request id $REQUEST_ID"

success_status="$(
  curl --silent --show-error --location --max-time 20 \
    --header "X-Request-Id: $REQUEST_ID" \
    --dump-header "$success_headers" \
    --output "$success_body" \
    --write-out "%{http_code}" \
    "$BASE_URL/api/timeline?from=0&to=1"
)"
[[ "$success_status" == "200" ]] || fail "Expected successful timeline request HTTP 200, got $success_status"
success_request_id="$(header_value "$success_headers" "X-Request-Id")"
[[ "$success_request_id" == "$REQUEST_ID" ]] ||
  fail "Successful response X-Request-Id did not match"
log "Successful response header propagation passed"

error_status="$(
  curl --silent --show-error --location --max-time 20 \
    --header "X-Request-Id: $REQUEST_ID" \
    --dump-header "$error_headers" \
    --output "$error_body" \
    --write-out "%{http_code}" \
    "$BASE_URL/api/timeline?from=1&to=1"
)"
[[ "$error_status" == "400" ]] || fail "Expected invalid timeline request HTTP 400, got $error_status"
error_header_request_id="$(header_value "$error_headers" "X-Request-Id")"
error_body_request_id="$(json_get "$error_body")"
[[ "$error_header_request_id" == "$REQUEST_ID" ]] ||
  fail "Error response X-Request-Id did not match"
[[ "$error_body_request_id" == "$REQUEST_ID" ]] ||
  fail "Error response body requestId did not match"
log "Error response header and body propagation passed"

log "Staging request ID smoke check passed"
