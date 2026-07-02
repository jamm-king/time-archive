#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-media-duration-smoke] %s\n' "$1"
}

fail() {
  printf '[verify-staging-media-duration-smoke] ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: scripts/verify-staging-media-duration-smoke.sh [--base-url URL] [--start-second N] [--end-second N]

Verifies deployed staging video duration validation through the public HTTPS
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
  if command -v python3 >/dev/null 2>&1 && python3 -c 'import json, os, struct, sys' >/dev/null 2>&1; then
    printf 'python3'
    return
  fi
  if command -v python >/dev/null 2>&1 && python -c 'import json, os, struct, sys' >/dev/null 2>&1; then
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

create_mp4_file() {
  local path="$1"
  local duration_ms="$2"

  "$PYTHON_BIN" - "$path" "$duration_ms" <<'PY'
import struct
import sys

path, duration_ms = sys.argv[1], int(sys.argv[2])

def box(kind, payload):
    raw_kind = kind.encode("ascii")
    return struct.pack(">I4s", len(payload) + 8, raw_kind) + payload

def mvhd_payload(duration):
    version_and_flags = b"\x00\x00\x00\x00"
    creation_time = struct.pack(">I", 0)
    modification_time = struct.pack(">I", 0)
    timescale = struct.pack(">I", 1000)
    duration_value = struct.pack(">I", duration)
    return version_and_flags + creation_time + modification_time + timescale + duration_value

payload = (
    box("ftyp", b"isom\x00\x00\x02\x00isommp41")
    + box("moov", box("mvhd", mvhd_payload(duration_ms)))
)

with open(path, "wb") as target:
    target.write(payload)
PY
}

upload_file() {
  local upload_url="$1"
  local file_path="$2"
  local status_file status

  status_file="$(mktemp)"
  if ! curl --silent --show-error --request PUT "$upload_url" \
    --header "Content-Type: video/mp4" \
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
  local filename="$5"
  local content_length_bytes body

  content_length_bytes="$(wc -c < "$file_path" | tr -d '[:space:]')"
  export CONTENT_LENGTH_BYTES="$content_length_bytes"
  export UPLOAD_FILENAME="$filename"

  body="$("$PYTHON_BIN" -c '
import json
import os

print(json.dumps({
    "mediaType": "VIDEO",
    "originalFilename": os.environ["UPLOAD_FILENAME"],
    "contentType": "video/mp4",
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
OWNED_MEDIA_AFTER_SHORT_FILE="$(mktemp)"
OWNED_MEDIA_AFTER_LONG_FILE="$(mktemp)"
SHORT_VIDEO_PATH="$(mktemp)"
LONG_VIDEO_PATH="$(mktemp)"
trap 'rm -f "$OWNER_COOKIE_FILE" "$OWNED_RANGES_FILE" "$OWNED_MEDIA_BEFORE_FILE" "$OWNED_MEDIA_AFTER_SHORT_FILE" "$OWNED_MEDIA_AFTER_LONG_FILE" "$SHORT_VIDEO_PATH" "$LONG_VIDEO_PATH"' EXIT

range_duration_seconds=$(( END_SECOND - START_SECOND ))
short_duration_ms=$(( range_duration_seconds * 1000 ))
long_duration_ms=$(( range_duration_seconds * 1000 + 2000 ))

log "Using BASE_URL=$BASE_URL"
log "Using range [$START_SECOND, $END_SECOND)"
log "Using short duration ${short_duration_ms}ms and over-duration ${long_duration_ms}ms"

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

create_mp4_file "$SHORT_VIDEO_PATH" "$short_duration_ms"
short_upload_request="$(create_upload_request "$OWNER_COOKIE_FILE" "$owner_csrf_token" "$ownership_record_id" "$SHORT_VIDEO_PATH" "staging-media-duration-short.mp4")"
short_upload_request_id="$(printf '%s' "$short_upload_request" | json_get uploadRequestId)"
short_upload_url="$(printf '%s' "$short_upload_request" | json_get uploadUrl)"
[[ -n "$short_upload_request_id" ]] || fail "Short upload request ID was empty"
[[ -n "$short_upload_url" ]] || fail "Short upload URL was empty"
log "Short video upload request created: $short_upload_request_id"

upload_file "$short_upload_url" "$SHORT_VIDEO_PATH"
short_completion="$(
  request_json \
    POST \
    "$BASE_URL/api/owned-ranges/$ownership_record_id/media/upload-requests/$short_upload_request_id/complete" \
    200 \
    "$OWNER_COOKIE_FILE" \
    "" \
    "$owner_csrf_token"
)"
short_media_asset_id="$(printf '%s' "$short_completion" | json_get mediaAsset.mediaAssetId)"
short_duration_response="$(printf '%s' "$short_completion" | json_get mediaAsset.durationMs)"
[[ -n "$short_media_asset_id" ]] || fail "Short media asset ID was empty"
[[ "$short_duration_response" == "$short_duration_ms" ]] || fail "Expected short durationMs=$short_duration_ms but got $short_duration_response"
log "Short video completed with media asset: $short_media_asset_id"

request_json GET "$BASE_URL/api/owned-ranges/$ownership_record_id/media" 200 "$OWNER_COOKIE_FILE" > "$OWNED_MEDIA_AFTER_SHORT_FILE"
owned_media_count_after_short="$(count_owned_media_assets "$OWNED_MEDIA_AFTER_SHORT_FILE")"
(( owned_media_count_after_short == owned_media_count_before + 1 )) || fail "Short video completion did not add exactly one media asset"

create_mp4_file "$LONG_VIDEO_PATH" "$long_duration_ms"
long_upload_request="$(create_upload_request "$OWNER_COOKIE_FILE" "$owner_csrf_token" "$ownership_record_id" "$LONG_VIDEO_PATH" "staging-media-duration-too-long.mp4")"
long_upload_request_id="$(printf '%s' "$long_upload_request" | json_get uploadRequestId)"
long_upload_url="$(printf '%s' "$long_upload_request" | json_get uploadUrl)"
[[ -n "$long_upload_request_id" ]] || fail "Long upload request ID was empty"
[[ -n "$long_upload_url" ]] || fail "Long upload URL was empty"
log "Over-duration video upload request created: $long_upload_request_id"

upload_file "$long_upload_url" "$LONG_VIDEO_PATH"
long_completion_error="$(
  request_json \
    POST \
    "$BASE_URL/api/owned-ranges/$ownership_record_id/media/upload-requests/$long_upload_request_id/complete" \
    409 \
    "$OWNER_COOKIE_FILE" \
    "" \
    "$owner_csrf_token"
)"
long_error_code="$(printf '%s' "$long_completion_error" | json_get code)"
[[ "$long_error_code" == "MEDIA_DURATION_EXCEEDS_OWNED_RANGE" ]] || fail "Expected MEDIA_DURATION_EXCEEDS_OWNED_RANGE but got $long_error_code"
log "Over-duration video completion rejected with expected error"

request_json GET "$BASE_URL/api/owned-ranges/$ownership_record_id/media" 200 "$OWNER_COOKIE_FILE" > "$OWNED_MEDIA_AFTER_LONG_FILE"
owned_media_count_after_long="$(count_owned_media_assets "$OWNED_MEDIA_AFTER_LONG_FILE")"
(( owned_media_count_after_long == owned_media_count_after_short )) || fail "Over-duration rejection created an unexpected media asset"
log "Over-duration rejection did not create a media asset"

log "Staging media duration smoke check passed"
