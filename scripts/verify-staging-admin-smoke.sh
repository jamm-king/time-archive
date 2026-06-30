#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-admin-smoke] %s\n' "$1"
}

fail() {
  printf '[verify-staging-admin-smoke] ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: scripts/verify-staging-admin-smoke.sh [--base-url URL]

Verifies deployed staging admin authorization through the public HTTPS
hostname. Requires STAGING_ADMIN_EMAIL and STAGING_ADMIN_PASSWORD.

Options:
  --base-url URL  Public staging base URL. Falls back to STAGING_PUBLIC_BASE_URL.
  -h, --help      Show this help.
USAGE
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

detect_python() {
  if command -v python3 >/dev/null 2>&1 && python3 -c 'import json, uuid' >/dev/null 2>&1; then
    printf 'python3'
    return
  fi
  if command -v python >/dev/null 2>&1 && python -c 'import json, uuid' >/dev/null 2>&1; then
    printf 'python'
    return
  fi
  fail "Required command not found: python3 or python"
}

json_get() {
  local path="$1"
  "$PYTHON_BIN" -c '
import json
import sys

data = json.load(sys.stdin)
value = data
for part in sys.argv[1].split("."):
    if part:
        if isinstance(value, list):
            value = value[int(part)]
        else:
            value = value[part]
if value is None:
    print("")
elif isinstance(value, bool):
    print(str(value).lower())
else:
    print(value)
' "$path"
}

request_json() {
  local method="$1"
  local url="$2"
  local expected_status="$3"
  local cookie_file="$4"
  local body="${5:-}"
  local csrf_token="${6:-}"
  local response_file status_file status
  local curl_args

  response_file="$(mktemp)"
  status_file="$(mktemp)"
  curl_args=(
    --silent
    --show-error
    --location
    --request "$method"
    "$url"
    --cookie "$cookie_file"
    --cookie-jar "$cookie_file"
    --output "$response_file"
    --write-out "%{http_code}"
  )

  if [[ "$method" != "GET" && -n "$csrf_token" ]]; then
    curl_args+=(--header "X-XSRF-TOKEN: $csrf_token")
  fi

  if [[ -n "$body" ]]; then
    curl_args+=(--header "Content-Type: application/json" --data "$body")
  fi

  if ! curl "${curl_args[@]}" > "$status_file"; then
    rm -f "$response_file" "$status_file"
    fail "Request failed: $method $url"
  fi

  status="$(cat "$status_file")"
  rm -f "$status_file"

  if [[ "$status" != "$expected_status" ]]; then
    printf 'Expected HTTP %s but got %s from %s %s\n' "$expected_status" "$status" "$method" "$url" >&2
    cat "$response_file" >&2
    rm -f "$response_file"
    exit 1
  fi

  cat "$response_file"
  rm -f "$response_file"
}

csrf_token_for() {
  local cookie_file="$1"
  local response token

  response="$(request_json GET "$BASE_URL/api/csrf" 200 "$cookie_file")"
  token="$(printf '%s' "$response" | json_get token)"
  [[ -n "$token" ]] || fail "CSRF token was empty"
  printf '%s' "$token"
}

register_regular_user() {
  local cookie_file="$1"
  local csrf_token="$2"
  local email body user role

  email="$("$PYTHON_BIN" -c '
import uuid

print("staging-admin-smoke-%s@example.com" % uuid.uuid4().hex)
')"
  export REGULAR_EMAIL="$email"
  export REGULAR_PASSWORD="$REGULAR_USER_PASSWORD"

  body="$("$PYTHON_BIN" -c '
import json
import os

print(json.dumps({
    "email": os.environ["REGULAR_EMAIL"],
    "password": os.environ["REGULAR_PASSWORD"],
    "displayName": "Staging Admin Smoke Regular User",
}))
')"
  user="$(request_json POST "$BASE_URL/api/auth/register" 201 "$cookie_file" "$body" "$csrf_token")"
  role="$(printf '%s' "$user" | json_get role)"
  [[ "$role" == "USER" ]] || fail "Expected disposable user role=USER"
}

login_admin() {
  local cookie_file="$1"
  local csrf_token="$2"
  local body user role

  body="$("$PYTHON_BIN" -c '
import json
import os

print(json.dumps({
    "email": os.environ["STAGING_ADMIN_EMAIL"],
    "password": os.environ["STAGING_ADMIN_PASSWORD"],
}))
')"
  user="$(request_json POST "$BASE_URL/api/auth/login" 200 "$cookie_file" "$body" "$csrf_token")"
  role="$(printf '%s' "$user" | json_get role)"
  [[ "$role" == "ADMIN" ]] || fail "Expected staging admin role=ADMIN"
}

BASE_URL="${STAGING_PUBLIC_BASE_URL:-}"
REGULAR_USER_PASSWORD="${REGULAR_USER_PASSWORD:-password123}"
export REGULAR_USER_PASSWORD

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      [[ $# -ge 2 ]] || fail "--base-url requires a value"
      BASE_URL="$2"
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
BASE_URL="${BASE_URL%/}"
[[ -n "${STAGING_ADMIN_EMAIL:-}" ]] || fail "STAGING_ADMIN_EMAIL is required"
[[ -n "${STAGING_ADMIN_PASSWORD:-}" ]] || fail "STAGING_ADMIN_PASSWORD is required"
export STAGING_ADMIN_EMAIL
export STAGING_ADMIN_PASSWORD

require_command curl
PYTHON_BIN="$(detect_python)"

UNAUTH_COOKIE_FILE="$(mktemp)"
REGULAR_COOKIE_FILE="$(mktemp)"
ADMIN_COOKIE_FILE="$(mktemp)"
ADMIN_RESPONSE_FILE="$(mktemp)"
trap 'rm -f "$UNAUTH_COOKIE_FILE" "$REGULAR_COOKIE_FILE" "$ADMIN_COOKIE_FILE" "$ADMIN_RESPONSE_FILE"' EXIT

log "Using BASE_URL=$BASE_URL"

request_json GET "$BASE_URL/api/admin/media/assets?status=UPLOADED" 401 "$UNAUTH_COOKIE_FILE" >/dev/null
log "Unauthenticated admin access rejection passed"

regular_csrf_token="$(csrf_token_for "$REGULAR_COOKIE_FILE")"
register_regular_user "$REGULAR_COOKIE_FILE" "$regular_csrf_token"
request_json GET "$BASE_URL/api/admin/media/assets?status=UPLOADED" 403 "$REGULAR_COOKIE_FILE" >/dev/null
log "Regular user admin access rejection passed"

admin_csrf_token="$(csrf_token_for "$ADMIN_COOKIE_FILE")"
login_admin "$ADMIN_COOKIE_FILE" "$admin_csrf_token"
request_json GET "$BASE_URL/api/admin/media/assets?status=UPLOADED" 200 "$ADMIN_COOKIE_FILE" > "$ADMIN_RESPONSE_FILE"
"$PYTHON_BIN" - "$ADMIN_RESPONSE_FILE" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    payload = json.load(source)

if not isinstance(payload, list):
    raise SystemExit("admin moderation list response must be a JSON array")
PY
log "Admin moderation list access passed"

log "Staging admin smoke check passed"
