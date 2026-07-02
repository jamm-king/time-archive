#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-media-signature-smoke] %s\n' "$1"
}

fail() {
  printf '[verify-staging-media-signature-smoke] ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: scripts/verify-staging-media-signature-smoke.sh [--base-url URL] [--start-second N] [--end-second N]

Verifies deployed staging file signature validation through the public HTTPS
hostname and real presigned object upload path. Requires STAGING_ADMIN_EMAIL
and STAGING_ADMIN_PASSWORD. The authenticated staging admin account must already
own the target range.

Options:
  --base-url URL      Public staging base URL. Falls back to STAGING_PUBLIC_BASE_URL.
  --start-second N   Owned range start second. Defaults to STAGING_MEDIA_START_SECOND or 7000.
  --end-second N     Owned range end second. Defaults to STAGING_MEDIA_END_SECOND or 7001.
  -h, --help         Show this help.
USAGE
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

detect_python() {
  if command -v python3 >/dev/null 2>&1 && python3 -c 'import json, os, sys' >/dev/null 2>&1; then
    printf 'python3'
    return
  fi
  if command -v python >/dev/null 2>&1 && python -c 'import json, os, sys' >/dev/null 2>&1; then
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

find_owned_range_id() {
  local owned_ranges_file="$1"

  "$PYTHON_BIN" - "$owned_ranges_file" "$START_SECOND" "$END_SECOND" <<'PY'
import json
import sys

path, start_second, end_second = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
with open(path, encoding="utf-8") as source:
    ranges = json.load(source)

if not isinstance(ranges, list):
    raise SystemExit("owned ranges response must be a JSON array")

for item in ranges:
    if (
        int(item.get("startSecond", -1)) == start_second
        and int(item.get("endSecond", -1)) == end_second
        and item.get("status") == "ACTIVE"
    ):
        print(item.get("ownershipRecordId", ""))
        break
PY
}

count_owned_media_assets() {
  local media_assets_file="$1"

  "$PYTHON_BIN" - "$media_assets_file" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    payload = json.load(source)

if not isinstance(payload, list):
    raise SystemExit("owned media response must be a JSON array")

print(len(payload))
PY
}

create_invalid_png_file() {
  local path="$1"

  "$PYTHON_BIN" - "$path" <<'PY'
import sys

path = sys.argv[1]
with open(path, "wb") as target:
    target.write(b"not-a-png-signature-for-staging-media-signature-smoke\n")
PY
}

upload_file() {
  local upload_url="$1"
  local file_path="$2"
  local status_file status

  status_file="$(mktemp)"
  if ! curl --silent --show-error --request PUT "$upload_url" \
    --header "Content-Type: image/png" \
    --data-binary "@$file_path" \
    --output /dev/null \
    --write-out "%{http_code}" > "$status_file"; then
    rm -f "$status_file"
    fail "Object upload failed"
  fi

  status="$(cat "$status_file")"
  rm -f "$status_file"
  [[ "$status" -ge 200 && "$status" -le 299 ]] || fail "Object upload returned HTTP $status"
}

create_upload_request() {
  local cookie_file="$1"
  local csrf_token="$2"
  local ownership_record_id="$3"
  local file_path="$4"
  local content_length_bytes body

  content_length_bytes="$(wc -c < "$file_path" | tr -d '[:space:]')"
  export CONTENT_LENGTH_BYTES="$content_length_bytes"

  body="$("$PYTHON_BIN" -c '
import json
import os

print(json.dumps({
    "mediaType": "IMAGE",
    "originalFilename": "staging-media-signature-invalid.png",
    "contentType": "image/png",
    "contentLengthBytes": int(os.environ["CONTENT_LENGTH_BYTES"]),
}))
')"

  request_json \
    POST \
    "$BASE_URL/api/owned-ranges/$ownership_record_id/media/upload-requests" \
    201 \
    "$cookie_file" \
    "$body" \
    "$csrf_token"
}

BASE_URL="${STAGING_PUBLIC_BASE_URL:-}"
START_SECOND="${STAGING_MEDIA_START_SECOND:-7000}"
END_SECOND="${STAGING_MEDIA_END_SECOND:-7001}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      [[ $# -ge 2 ]] || fail "--base-url requires a value"
      BASE_URL="$2"
      shift 2
      ;;
    --start-second)
      [[ $# -ge 2 ]] || fail "--start-second requires a value"
      START_SECOND="$2"
      shift 2
      ;;
    --end-second)
      [[ $# -ge 2 ]] || fail "--end-second requires a value"
      END_SECOND="$2"
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
[[ "$START_SECOND" =~ ^[0-9]+$ ]] || fail "start second must be a non-negative integer"
[[ "$END_SECOND" =~ ^[0-9]+$ ]] || fail "end second must be a non-negative integer"
(( START_SECOND < END_SECOND )) || fail "start second must be less than end second"
(( END_SECOND <= 86400 )) || fail "end second must be at most 86400"
BASE_URL="${BASE_URL%/}"
[[ -n "${STAGING_ADMIN_EMAIL:-}" ]] || fail "STAGING_ADMIN_EMAIL is required"
[[ -n "${STAGING_ADMIN_PASSWORD:-}" ]] || fail "STAGING_ADMIN_PASSWORD is required"
export STAGING_ADMIN_EMAIL
export STAGING_ADMIN_PASSWORD

require_command curl
require_command wc
PYTHON_BIN="$(detect_python)"

OWNER_COOKIE_FILE="$(mktemp)"
OWNED_RANGES_FILE="$(mktemp)"
OWNED_MEDIA_BEFORE_FILE="$(mktemp)"
OWNED_MEDIA_AFTER_FILE="$(mktemp)"
INVALID_PNG_PATH="$(mktemp)"
trap 'rm -f "$OWNER_COOKIE_FILE" "$OWNED_RANGES_FILE" "$OWNED_MEDIA_BEFORE_FILE" "$OWNED_MEDIA_AFTER_FILE" "$INVALID_PNG_PATH"' EXIT

log "Using BASE_URL=$BASE_URL"
log "Using range [$START_SECOND, $END_SECOND)"

owner_csrf_token="$(csrf_token_for "$OWNER_COOKIE_FILE")"
login_admin "$OWNER_COOKIE_FILE" "$owner_csrf_token"
owner_csrf_token="$(csrf_token_for "$OWNER_COOKIE_FILE")"
log "Staging owner authenticated"

request_json GET "$BASE_URL/api/me/owned-ranges" 200 "$OWNER_COOKIE_FILE" > "$OWNED_RANGES_FILE"
ownership_record_id="$(find_owned_range_id "$OWNED_RANGES_FILE")"
[[ -n "$ownership_record_id" ]] || fail "Authenticated account does not own active range [$START_SECOND, $END_SECOND)"
log "Owned range found: $ownership_record_id"

request_json GET "$BASE_URL/api/owned-ranges/$ownership_record_id/media" 200 "$OWNER_COOKIE_FILE" > "$OWNED_MEDIA_BEFORE_FILE"
owned_media_count_before="$(count_owned_media_assets "$OWNED_MEDIA_BEFORE_FILE")"

create_invalid_png_file "$INVALID_PNG_PATH"
upload_request="$(create_upload_request "$OWNER_COOKIE_FILE" "$owner_csrf_token" "$ownership_record_id" "$INVALID_PNG_PATH")"
upload_request_id="$(printf '%s' "$upload_request" | json_get uploadRequestId)"
upload_url="$(printf '%s' "$upload_request" | json_get uploadUrl)"
[[ -n "$upload_request_id" ]] || fail "Upload request ID was empty"
[[ -n "$upload_url" ]] || fail "Upload URL was empty"
log "Invalid PNG upload request created: $upload_request_id"

upload_file "$upload_url" "$INVALID_PNG_PATH"
log "Invalid PNG object uploaded through presigned PUT URL"

completion_error="$(
  request_json \
    POST \
    "$BASE_URL/api/owned-ranges/$ownership_record_id/media/upload-requests/$upload_request_id/complete" \
    409 \
    "$OWNER_COOKIE_FILE" \
    "" \
    "$owner_csrf_token"
)"
error_code="$(printf '%s' "$completion_error" | json_get code)"
[[ "$error_code" == "MEDIA_FILE_SIGNATURE_MISMATCH" ]] || fail "Expected MEDIA_FILE_SIGNATURE_MISMATCH but got $error_code"
log "Signature mismatch completion rejected with expected error"

request_json GET "$BASE_URL/api/owned-ranges/$ownership_record_id/media" 200 "$OWNER_COOKIE_FILE" > "$OWNED_MEDIA_AFTER_FILE"
owned_media_count_after="$(count_owned_media_assets "$OWNED_MEDIA_AFTER_FILE")"
(( owned_media_count_after == owned_media_count_before )) || fail "Signature mismatch rejection created an unexpected media asset"
log "Signature mismatch rejection did not create a media asset"

log "Staging media signature smoke check passed"
