#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3000}"
START_SECOND="${START_SECOND:-5000}"
END_SECOND="${END_SECOND:-5001}"
READY_TIMEOUT_SECONDS="${READY_TIMEOUT_SECONDS:-120}"
UPLOAD_FILENAME="${UPLOAD_FILENAME:-time-archive-web-upload.png}"
UPLOAD_CONTENT_TYPE="${UPLOAD_CONTENT_TYPE:-image/png}"

export BASE_URL
export START_SECOND
export END_SECOND
export READY_TIMEOUT_SECONDS
export UPLOAD_FILENAME
export UPLOAD_CONTENT_TYPE

log() {
  printf '[verify-web-purchase-upload] %s\n' "$1"
}

fail() {
  printf '[verify-web-purchase-upload] ERROR: %s\n' "$1" >&2
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

json_array_contains_id() {
  local field="$1"
  local expected="$2"
  "$PYTHON_BIN" -c '
import json
import sys

field = sys.argv[1]
expected = sys.argv[2]
data = json.load(sys.stdin)
if not isinstance(data, list):
    print("false")
    raise SystemExit
print(str(any(isinstance(item, dict) and item.get(field) == expected for item in data)).lower())
' "$field" "$expected"
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

  fail "Web auth endpoint did not become ready within ${READY_TIMEOUT_SECONDS}s"
}

create_upload_file() {
  local path="$1"
  "$PYTHON_BIN" -c '
import sys

path = sys.argv[1]
payload = (
    b"\x89PNG\r\n\x1a\n"
    b"time-archive-web-purchase-upload-verification\n"
    b"stable-local-test-bytes\n"
)
with open(path, "wb") as f:
    f.write(payload)
' "$path"
}

upload_file() {
  local upload_url="$1"
  local file_path="$2"
  local status_file status

  status_file="$(mktemp)"
  if ! curl -sS -X PUT "$upload_url" \
    -H "Content-Type: $UPLOAD_CONTENT_TYPE" \
    --data-binary "@$file_path" \
    -o /dev/null \
    -w "%{http_code}" > "$status_file"; then
    rm -f "$status_file"
    fail "Object upload failed"
  fi

  status="$(cat "$status_file")"
  rm -f "$status_file"
  [[ "$status" -ge 200 && "$status" -le 299 ]] || fail "Object upload returned HTTP $status"
}

require_command curl
require_command wc
PYTHON_BIN="$(detect_python)"
SESSION_COOKIE_FILE="$(mktemp)"
UPLOAD_FILE_PATH="$(mktemp)"
trap 'rm -f "$SESSION_COOKIE_FILE" "$UPLOAD_FILE_PATH"' EXIT

log "Using BASE_URL=$BASE_URL"
log "Using range [$START_SECOND, $END_SECOND)"

wait_for_ready
log "Web endpoint is ready"
refresh_csrf_token
log "CSRF token fetched"

register_body="$("$PYTHON_BIN" -c '
import json
import os
import uuid

print(json.dumps({
    "email": "web-purchase-upload-%s-%s-%s@example.com" % (
        os.environ["START_SECOND"],
        os.environ["END_SECOND"],
        uuid.uuid4().hex,
    ),
    "password": "password123",
    "displayName": "Web Purchase Upload User",
}))
')"
current_user="$(request_json POST "$BASE_URL/api/auth/register" 201 "$register_body")"
current_user_id="$(printf '%s' "$current_user" | json_get userId)"
[[ -n "$current_user_id" ]] || fail "Authenticated user ID was empty"
log "Authenticated user created: $current_user_id"

availability="$(request_json GET "$BASE_URL/api/archive/availability?startSecond=$START_SECOND&endSecond=$END_SECOND" 200)"
available="$(printf '%s' "$availability" | json_get available)"
[[ "$available" == "true" ]] || fail "Range is not available. Use START_SECOND and END_SECOND to choose another range."
log "Initial availability passed"

refresh_csrf_token
reservation_body="$("$PYTHON_BIN" -c '
import json
import os

print(json.dumps({
    "startSecond": int(os.environ["START_SECOND"]),
    "endSecond": int(os.environ["END_SECOND"]),
}))
')"
reservation="$(request_json POST "$BASE_URL/api/purchase/reservations" 201 "$reservation_body")"
reservation_id="$(printf '%s' "$reservation" | json_get reservationId)"
[[ -n "$reservation_id" ]] || fail "Reservation ID was empty"
log "Reservation created: $reservation_id"

refresh_csrf_token
checkout="$(request_json POST "$BASE_URL/api/purchase/reservations/$reservation_id/checkout" 200)"
checkout_url="$(printf '%s' "$checkout" | json_get checkoutUrl)"
[[ -n "$checkout_url" ]] || fail "Checkout URL was empty"
log "Checkout created: $checkout_url"

export RESERVATION_ID="$reservation_id"
payment_body="$("$PYTHON_BIN" -c '
import json
import os
import uuid

request_id = "web-purchase-upload-%s" % uuid.uuid4().hex
print(json.dumps({
    "providerEventId": "evt_%s" % request_id,
    "eventType": "payment_intent.succeeded",
    "payloadHash": "sha256-%s" % request_id,
    "reservationId": os.environ["RESERVATION_ID"],
    "paymentReference": "pi_%s" % request_id,
    "requestId": request_id,
}))
')"
refresh_csrf_token
payment="$(request_json POST "$BASE_URL/api/internal/payments/fake/webhooks/primary-purchase-completed" 200 "$payment_body")"
ownership_record_id="$(printf '%s' "$payment" | json_get ownershipRecordId)"
[[ -n "$ownership_record_id" ]] || fail "Ownership record ID was empty"
log "Payment completed with ownership: $ownership_record_id"

owned_ranges="$(request_json GET "$BASE_URL/api/me/owned-ranges" 200)"
owned_range_found="$(printf '%s' "$owned_ranges" | json_array_contains_id ownershipRecordId "$ownership_record_id")"
[[ "$owned_range_found" == "true" ]] || fail "Purchased ownership record was not listed in current user's owned ranges"
log "Owned range listing includes purchased range"

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
refresh_csrf_token
upload_request="$(
  request_json \
    POST \
    "$BASE_URL/api/owned-ranges/$ownership_record_id/media/upload-requests" \
    201 \
    "$upload_request_body"
)"
upload_request_id="$(printf '%s' "$upload_request" | json_get uploadRequestId)"
upload_url="$(printf '%s' "$upload_request" | json_get uploadUrl)"
[[ -n "$upload_request_id" ]] || fail "Upload request ID was empty"
[[ -n "$upload_url" ]] || fail "Upload URL was empty"
log "Upload request created: $upload_request_id"

upload_file "$upload_url" "$UPLOAD_FILE_PATH"
log "Object uploaded through presigned URL"

refresh_csrf_token
completion="$(
  request_json \
    POST \
    "$BASE_URL/api/owned-ranges/$ownership_record_id/media/upload-requests/$upload_request_id/complete" \
    200
)"
media_asset_id="$(printf '%s' "$completion" | json_get mediaAsset.mediaAssetId)"
already_completed="$(printf '%s' "$completion" | json_get alreadyCompleted)"
moderation_status="$(printf '%s' "$completion" | json_get mediaAsset.moderationStatus)"
[[ -n "$media_asset_id" ]] || fail "Media asset ID was empty"
[[ "$already_completed" == "false" ]] || fail "Expected first completion to have alreadyCompleted=false"
[[ "$moderation_status" == "UPLOADED" ]] || fail "Expected media asset moderationStatus=UPLOADED"
log "Upload completed and media asset created: $media_asset_id"

refresh_csrf_token
duplicate_completion="$(
  request_json \
    POST \
    "$BASE_URL/api/owned-ranges/$ownership_record_id/media/upload-requests/$upload_request_id/complete" \
    200
)"
duplicate_media_asset_id="$(printf '%s' "$duplicate_completion" | json_get mediaAsset.mediaAssetId)"
duplicate_already_completed="$(printf '%s' "$duplicate_completion" | json_get alreadyCompleted)"
[[ "$duplicate_media_asset_id" == "$media_asset_id" ]] || fail "Duplicate completion returned another media asset"
[[ "$duplicate_already_completed" == "true" ]] || fail "Expected duplicate completion to have alreadyCompleted=true"
log "Upload completion idempotency passed"

media_list="$(request_json GET "$BASE_URL/api/owned-ranges/$ownership_record_id/media" 200)"
listed_media_asset_id="$(printf '%s' "$media_list" | json_get "0.mediaAssetId")"
[[ "$listed_media_asset_id" == "$media_asset_id" ]] || fail "Created media asset was not listed for owned range"
log "Owned range media listing passed"

log "Local web purchase-upload flow verification passed"
