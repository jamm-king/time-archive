#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-auth-smoke] %s\n' "$1"
}

fail() {
  printf '[verify-staging-auth-smoke] ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: scripts/verify-staging-auth-smoke.sh [--base-url URL]

Verifies the deployed staging HTTPS authentication flow. The check creates a
disposable staging user with a staging-auth-smoke email prefix.

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
  local body="${4:-}"
  local csrf_mode="${5:-with-csrf}"
  local headers_file="${6:-}"
  local response_file status_file status
  local curl_args

  response_file="$(mktemp)"
  status_file="$(mktemp)"
  [[ -n "$headers_file" ]] || headers_file="$(mktemp)"

  curl_args=(
    --silent
    --show-error
    --location
    --request "$method"
    "$url"
    --cookie "$SESSION_COOKIE_FILE"
    --cookie-jar "$SESSION_COOKIE_FILE"
    --dump-header "$headers_file"
    --output "$response_file"
    --write-out "%{http_code}"
  )

  if [[ "$method" != "GET" && "$csrf_mode" == "with-csrf" && -n "${CSRF_TOKEN:-}" ]]; then
    curl_args+=(--header "X-XSRF-TOKEN: $CSRF_TOKEN")
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

refresh_csrf_token() {
  local csrf_response

  csrf_response="$(request_json GET "$BASE_URL/api/csrf" 200)"
  CSRF_TOKEN="$(printf '%s' "$csrf_response" | json_get token)"
  export CSRF_TOKEN
  [[ -n "$CSRF_TOKEN" ]] || fail "CSRF token was empty"
}

assert_session_cookie_security() {
  local headers_file="$1"

  "$PYTHON_BIN" - "$headers_file" <<'PY'
import sys

headers_path = sys.argv[1]
cookies = []
with open(headers_path, encoding="utf-8") as source:
    for line in source:
        if line.lower().startswith("set-cookie:"):
            cookies.append(line.split(":", 1)[1].strip())

session_cookies = [
    cookie for cookie in cookies
    if cookie.startswith("SESSION=") or cookie.startswith("JSESSIONID=")
]
if not session_cookies:
    raise SystemExit("session cookie was not set")

cookie = session_cookies[-1]
lower_cookie = cookie.lower()
for required in ("httponly", "secure", "samesite=lax"):
    if required not in lower_cookie:
        raise SystemExit(f"session cookie missing {required}: {cookie}")
PY
}

BASE_URL="${STAGING_PUBLIC_BASE_URL:-}"
AUTH_EMAIL="${AUTH_EMAIL:-}"
AUTH_PASSWORD="${AUTH_PASSWORD:-password123}"
AUTH_DISPLAY_NAME="${AUTH_DISPLAY_NAME:-Staging Auth Smoke}"
export AUTH_PASSWORD
export AUTH_DISPLAY_NAME

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

require_command curl
PYTHON_BIN="$(detect_python)"
SESSION_COOKIE_FILE="$(mktemp)"
trap 'rm -f "$SESSION_COOKIE_FILE"' EXIT

log "Using BASE_URL=$BASE_URL"
refresh_csrf_token
log "CSRF token fetched"

if [[ -z "$AUTH_EMAIL" ]]; then
  AUTH_EMAIL="$("$PYTHON_BIN" -c '
import uuid

print("staging-auth-smoke-%s@example.com" % uuid.uuid4().hex)
')"
  export AUTH_EMAIL
fi

register_body="$("$PYTHON_BIN" -c '
import json
import os

print(json.dumps({
    "email": os.environ["AUTH_EMAIL"],
    "password": os.environ["AUTH_PASSWORD"],
    "displayName": os.environ["AUTH_DISPLAY_NAME"],
}))
')"

request_json POST "$BASE_URL/api/auth/register" 403 "$register_body" without-csrf >/dev/null
log "Missing CSRF mutation rejection passed"

register_headers="$(mktemp)"
registered_user="$(request_json POST "$BASE_URL/api/auth/register" 201 "$register_body" with-csrf "$register_headers")"
assert_session_cookie_security "$register_headers"
rm -f "$register_headers"

registered_user_id="$(printf '%s' "$registered_user" | json_get userId)"
registered_email="$(printf '%s' "$registered_user" | json_get email)"
[[ -n "$registered_user_id" ]] || fail "Registered user ID was empty"
[[ "$registered_email" == "$AUTH_EMAIL" ]] || fail "Registered email did not match"
log "Registered disposable staging user"

current_user="$(request_json GET "$BASE_URL/api/me" 200)"
current_user_id="$(printf '%s' "$current_user" | json_get userId)"
[[ "$current_user_id" == "$registered_user_id" ]] || fail "Current user did not match registered user"
log "Current user lookup passed"

refresh_csrf_token
request_json POST "$BASE_URL/api/auth/logout" 204 >/dev/null
request_json GET "$BASE_URL/api/me" 401 >/dev/null
log "Logout and post-logout rejection passed"

refresh_csrf_token
login_body="$("$PYTHON_BIN" -c '
import json
import os

print(json.dumps({
    "email": os.environ["AUTH_EMAIL"],
    "password": os.environ["AUTH_PASSWORD"],
}))
')"
login_headers="$(mktemp)"
logged_in_user="$(request_json POST "$BASE_URL/api/auth/login" 200 "$login_body" with-csrf "$login_headers")"
assert_session_cookie_security "$login_headers"
rm -f "$login_headers"

logged_in_user_id="$(printf '%s' "$logged_in_user" | json_get userId)"
[[ "$logged_in_user_id" == "$registered_user_id" ]] || fail "Logged-in user did not match registered user"
request_json GET "$BASE_URL/api/me" 200 >/dev/null
log "Login and final current user lookup passed"

log "Staging auth smoke check passed"
