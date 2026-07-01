#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-presigned-upload-cors-smoke] %s\n' "$1"
}

fail() {
  printf '[verify-staging-presigned-upload-cors-smoke] ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: scripts/verify-staging-presigned-upload-cors-smoke.sh [--base-url URL] [--start-second N] [--end-second N]

Verifies deployed staging presigned upload CORS behavior through the public
HTTPS hostname. Requires STAGING_ADMIN_EMAIL and STAGING_ADMIN_PASSWORD.
The authenticated staging admin account must already own the target range.

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

create_upload_file() {
  local path="$1"

  "$PYTHON_BIN" - "$path" <<'PY'
import sys

path = sys.argv[1]
payload = (
    b"\x89PNG\r\n\x1a\n"
    b"time-archive-staging-presigned-upload-cors-smoke\n"
    b"stable-small-cors-test-bytes\n"
)
with open(path, "wb") as target:
    target.write(payload)
PY
}

assert_cors_headers() {
  local headers_file="$1"
  local context="$2"
  local require_methods="$3"
  local require_headers="$4"

  "$PYTHON_BIN" - "$headers_file" "$context" "$BASE_URL" "$require_methods" "$require_headers" <<'PY'
import re
import sys

headers_path, context, expected_origin, require_methods, require_headers = sys.argv[1:6]
with open(headers_path, encoding="utf-8") as source:
    raw = source.read()

blocks = [
    block for block in re.split(r"\r?\n\r?\n", raw.strip())
    if block.strip()
]
if not blocks:
    raise SystemExit(f"{context}: response headers were empty")

headers = {}
for line in blocks[-1].splitlines():
    if ":" not in line:
        continue
    name, value = line.split(":", 1)
    headers[name.strip().lower()] = value.strip()

allow_origin = headers.get("access-control-allow-origin")
if allow_origin not in (expected_origin, "*"):
    raise SystemExit(
        f"{context}: expected access-control-allow-origin {expected_origin!r} or '*', got {allow_origin!r}"
    )

if require_methods == "true":
    allow_methods = headers.get("access-control-allow-methods", "")
    methods = {part.strip().upper() for part in allow_methods.split(",")}
    if "PUT" not in methods:
        raise SystemExit(f"{context}: access-control-allow-methods must include PUT, got {allow_methods!r}")

if require_headers == "true":
    allow_headers = headers.get("access-control-allow-headers", "")
    normalized = {part.strip().lower() for part in allow_headers.split(",")}
    if "*" not in normalized and "content-type" not in normalized:
        raise SystemExit(
            f"{context}: access-control-allow-headers must include content-type or '*', got {allow_headers!r}"
        )
PY
}

verify_preflight() {
  local upload_url="$1"
  local headers_file status_file status

  headers_file="$(mktemp)"
  status_file="$(mktemp)"
  if ! curl \
    --silent \
    --show-error \
    --request OPTIONS \
    "$upload_url" \
    --header "Origin: $BASE_URL" \
    --header "Access-Control-Request-Method: PUT" \
    --header "Access-Control-Request-Headers: content-type" \
    --dump-header "$headers_file" \
    --output /dev/null \
    --write-out "%{http_code}" > "$status_file"; then
    rm -f "$headers_file" "$status_file"
    fail "CORS preflight request failed"
  fi

  status="$(cat "$status_file")"
  rm -f "$status_file"
  if [[ "$status" -lt 200 || "$status" -gt 299 ]]; then
    cat "$headers_file" >&2
    rm -f "$headers_file"
    fail "CORS preflight returned HTTP $status"
  fi

  assert_cors_headers "$headers_file" "preflight" true true
  rm -f "$headers_file"
}

upload_file_with_origin() {
  local upload_url="$1"
  local file_path="$2"
  local headers_file status_file status

  headers_file="$(mktemp)"
  status_file="$(mktemp)"
  if ! curl \
    --silent \
    --show-error \
    --request PUT \
    "$upload_url" \
    --header "Origin: $BASE_URL" \
    --header "Content-Type: $UPLOAD_CONTENT_TYPE" \
    --data-binary "@$file_path" \
    --dump-header "$headers_file" \
    --output /dev/null \
    --write-out "%{http_code}" > "$status_file"; then
    rm -f "$headers_file" "$status_file"
    fail "Object upload with Origin failed"
  fi

  status="$(cat "$status_file")"
  rm -f "$status_file"
  if [[ "$status" -lt 200 || "$status" -gt 299 ]]; then
    cat "$headers_file" >&2
    rm -f "$headers_file"
    fail "Object upload with Origin returned HTTP $status"
  fi

  assert_cors_headers "$headers_file" "upload" false false
  rm -f "$headers_file"
}

BASE_URL="${STAGING_PUBLIC_BASE_URL:-}"
START_SECOND="${STAGING_MEDIA_START_SECOND:-7000}"
END_SECOND="${STAGING_MEDIA_END_SECOND:-7001}"
UPLOAD_CONTENT_TYPE="${UPLOAD_CONTENT_TYPE:-image/png}"
UPLOAD_FILENAME="${UPLOAD_FILENAME:-staging-presigned-upload-cors-smoke.png}"

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
export UPLOAD_CONTENT_TYPE
export UPLOAD_FILENAME

require_command curl
require_command wc
PYTHON_BIN="$(detect_python)"

COOKIE_FILE="$(mktemp)"
OWNED_RANGES_FILE="$(mktemp)"
UPLOAD_FILE_PATH="$(mktemp)"
trap 'rm -f "$COOKIE_FILE" "$OWNED_RANGES_FILE" "$UPLOAD_FILE_PATH"' EXIT

log "Using BASE_URL=$BASE_URL"
log "Using range [$START_SECOND, $END_SECOND)"

csrf_token="$(csrf_token_for "$COOKIE_FILE")"
login_admin "$COOKIE_FILE" "$csrf_token"
csrf_token="$(csrf_token_for "$COOKIE_FILE")"
log "Staging owner authenticated"

request_json GET "$BASE_URL/api/me/owned-ranges" 200 "$COOKIE_FILE" > "$OWNED_RANGES_FILE"
ownership_record_id="$(find_owned_range_id "$OWNED_RANGES_FILE")"
[[ -n "$ownership_record_id" ]] || fail "Authenticated account does not own active range [$START_SECOND, $END_SECOND)"
log "Owned range found: $ownership_record_id"

create_upload_file "$UPLOAD_FILE_PATH"
content_length_bytes="$(wc -c < "$UPLOAD_FILE_PATH" | tr -d '[:space:]')"
export CONTENT_LENGTH_BYTES="$content_length_bytes"

upload_request_body="$("$PYTHON_BIN" -c '
import json
import os

print(json.dumps({
    "mediaType": "IMAGE",
    "originalFilename": os.environ["UPLOAD_FILENAME"],
    "contentType": os.environ["UPLOAD_CONTENT_TYPE"],
    "contentLengthBytes": int(os.environ["CONTENT_LENGTH_BYTES"]),
}))
')"
upload_request="$(
  request_json \
    POST \
    "$BASE_URL/api/owned-ranges/$ownership_record_id/media/upload-requests" \
    201 \
    "$COOKIE_FILE" \
    "$upload_request_body" \
    "$csrf_token"
)"
upload_request_id="$(printf '%s' "$upload_request" | json_get uploadRequestId)"
upload_url="$(printf '%s' "$upload_request" | json_get uploadUrl)"
[[ -n "$upload_request_id" ]] || fail "Upload request ID was empty"
[[ -n "$upload_url" ]] || fail "Upload URL was empty"
log "Upload request created: $upload_request_id"

verify_preflight "$upload_url"
log "Presigned PUT CORS preflight passed"

upload_file_with_origin "$upload_url" "$UPLOAD_FILE_PATH"
log "Presigned PUT with Origin passed"

log "Staging presigned upload CORS smoke check passed"
