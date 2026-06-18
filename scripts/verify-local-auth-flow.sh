#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3000}"
READY_TIMEOUT_SECONDS="${READY_TIMEOUT_SECONDS:-120}"
AUTH_EMAIL="${AUTH_EMAIL:-}"
AUTH_PASSWORD="${AUTH_PASSWORD:-password123}"
AUTH_DISPLAY_NAME="${AUTH_DISPLAY_NAME:-Auth Flow User}"

export BASE_URL
export READY_TIMEOUT_SECONDS
export AUTH_EMAIL
export AUTH_PASSWORD
export AUTH_DISPLAY_NAME

log() {
  printf '[verify-auth] %s\n' "$1"
}

fail() {
  printf '[verify-auth] ERROR: %s\n' "$1" >&2
  exit 1
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
  local response_file status_file status
  local curl_args

  response_file="$(mktemp)"
  status_file="$(mktemp)"
  curl_args=(
    -sS
    -X "$method"
    "$url"
    --cookie "$SESSION_COOKIE_FILE"
    --cookie-jar "$SESSION_COOKIE_FILE"
    -o "$response_file"
    -w "%{http_code}"
  )

  if [[ "$method" != "GET" && -n "${CSRF_TOKEN:-}" ]]; then
    curl_args+=(-H "X-XSRF-TOKEN: $CSRF_TOKEN")
  fi

  if [[ -n "$body" ]]; then
    curl_args+=(-H "Content-Type: application/json" -d "$body")
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

wait_for_ready() {
  local deadline

  deadline=$((SECONDS + READY_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    if curl -sS --fail "$BASE_URL/api/csrf" >/dev/null 2>&1; then
      return
    fi
    sleep 2
  done

  fail "Auth endpoint did not become ready within ${READY_TIMEOUT_SECONDS}s"
}

require_command curl
PYTHON_BIN="$(detect_python)"
SESSION_COOKIE_FILE="$(mktemp)"
trap 'rm -f "$SESSION_COOKIE_FILE"' EXIT

log "Using BASE_URL=$BASE_URL"

wait_for_ready
log "Auth endpoint is ready"
refresh_csrf_token
log "CSRF token fetched"

if [[ -z "$AUTH_EMAIL" ]]; then
  AUTH_EMAIL="$("$PYTHON_BIN" -c '
import uuid

print("auth-%s@example.com" % uuid.uuid4().hex)
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
registered_user="$(request_json POST "$BASE_URL/api/auth/register" 201 "$register_body")"
registered_user_id="$(printf '%s' "$registered_user" | json_get userId)"
registered_email="$(printf '%s' "$registered_user" | json_get email)"
[[ -n "$registered_user_id" ]] || fail "Registered user ID was empty"
[[ "$registered_email" == "$AUTH_EMAIL" ]] || fail "Registered email did not match"
log "Registered user: $registered_user_id"

current_user="$(request_json GET "$BASE_URL/api/me" 200)"
current_user_id="$(printf '%s' "$current_user" | json_get userId)"
[[ "$current_user_id" == "$registered_user_id" ]] || fail "Current user did not match registered user"
log "Current user lookup passed"

refresh_csrf_token
log "CSRF token refreshed before logout"
request_json POST "$BASE_URL/api/auth/logout" 204 >/dev/null
log "Logout passed"

request_json GET "$BASE_URL/api/me" 401 >/dev/null
log "Post-logout current user rejection passed"

refresh_csrf_token
log "CSRF token refreshed before login"
login_body="$("$PYTHON_BIN" -c '
import json
import os

print(json.dumps({
    "email": os.environ["AUTH_EMAIL"],
    "password": os.environ["AUTH_PASSWORD"],
}))
')"
logged_in_user="$(request_json POST "$BASE_URL/api/auth/login" 200 "$login_body")"
logged_in_user_id="$(printf '%s' "$logged_in_user" | json_get userId)"
[[ "$logged_in_user_id" == "$registered_user_id" ]] || fail "Logged-in user did not match registered user"
log "Login passed"

final_current_user="$(request_json GET "$BASE_URL/api/me" 200)"
final_current_user_id="$(printf '%s' "$final_current_user" | json_get userId)"
[[ "$final_current_user_id" == "$registered_user_id" ]] || fail "Final current user did not match registered user"
log "Final current user lookup passed"

log "Local auth flow verification passed"
