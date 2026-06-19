#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@time-archive.local}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-password123}"
START_SECOND="${START_SECOND:-6000}"
END_SECOND="${END_SECOND:-6001}"
EVENT_ID="${EVENT_ID:-evt_admin_preview_local_${START_SECOND}_${END_SECOND}}"
PAYMENT_REFERENCE="${PAYMENT_REFERENCE:-pi_admin_preview_local_${START_SECOND}_${END_SECOND}}"
REQUEST_ID="${REQUEST_ID:-admin-preview-local-request-${START_SECOND}-${END_SECOND}}"
EVENT_TYPE="${EVENT_TYPE:-payment_intent.succeeded}"
PAYLOAD_HASH="${PAYLOAD_HASH:-sha256-admin-preview-local-${START_SECOND}-${END_SECOND}}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-120}"
UPLOAD_FILENAME="${UPLOAD_FILENAME:-time-archive-admin-preview.png}"
UPLOAD_CONTENT_TYPE="${UPLOAD_CONTENT_TYPE:-image/png}"

export BASE_URL
export ADMIN_EMAIL
export ADMIN_PASSWORD
export START_SECOND
export END_SECOND
export EVENT_ID
export PAYMENT_REFERENCE
export REQUEST_ID
export EVENT_TYPE
export PAYLOAD_HASH
export HEALTH_TIMEOUT_SECONDS
export UPLOAD_FILENAME
export UPLOAD_CONTENT_TYPE

log() {
  printf '[verify-admin-preview] %s\n' "$1"
}

fail() {
  printf '[verify-admin-preview] ERROR: %s\n' "$1" >&2
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
  local body="${3:-}"
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

  if [[ "$status" -lt 200 || "$status" -gt 299 ]]; then
    printf 'HTTP %s from %s %s\n' "$status" "$method" "$url" >&2
    cat "$response_file" >&2
    rm -f "$response_file"
    exit 1
  fi

  cat "$response_file"
  rm -f "$response_file"
}

refresh_csrf_token() {
  local csrf_response

  csrf_response="$(request_json GET "$BASE_URL/api/csrf")"
  CSRF_TOKEN="$(printf '%s' "$csrf_response" | json_get token)"
  export CSRF_TOKEN
  [[ -n "$CSRF_TOKEN" ]] || fail "CSRF token was empty"
}

authenticate_admin() {
  local register_body login_body response_file status_file status
  local curl_args

  register_body="$("$PYTHON_BIN" -c '
import json
import os

print(json.dumps({
    "email": os.environ["ADMIN_EMAIL"],
    "password": os.environ["ADMIN_PASSWORD"],
    "displayName": "Admin",
}))
')"
  response_file="$(mktemp)"
  status_file="$(mktemp)"
  curl_args=(
    -sS
    -X POST
    "$BASE_URL/api/auth/register"
    --cookie "$SESSION_COOKIE_FILE"
    --cookie-jar "$SESSION_COOKIE_FILE"
    -H "X-XSRF-TOKEN: $CSRF_TOKEN"
    -H "Content-Type: application/json"
    -d "$register_body"
    -o "$response_file"
    -w "%{http_code}"
  )

  if ! curl "${curl_args[@]}" > "$status_file"; then
    rm -f "$response_file" "$status_file"
    fail "Admin registration request failed"
  fi

  status="$(cat "$status_file")"
  rm -f "$status_file"

  if [[ "$status" -ge 200 && "$status" -le 299 ]]; then
    cat "$response_file"
    rm -f "$response_file"
    return
  fi

  if [[ "$status" != "409" ]]; then
    printf 'HTTP %s from admin registration\n' "$status" >&2
    cat "$response_file" >&2
    rm -f "$response_file"
    exit 1
  fi

  rm -f "$response_file"
  login_body="$("$PYTHON_BIN" -c '
import json
import os

print(json.dumps({
    "email": os.environ["ADMIN_EMAIL"],
    "password": os.environ["ADMIN_PASSWORD"],
}))
')"
  request_json POST "$BASE_URL/api/auth/login" "$login_body"
}

wait_for_health() {
  local deadline status

  deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    status="$(
      { curl -sS "$BASE_URL/actuator/health" 2>/dev/null || true; } |
        "$PYTHON_BIN" -c '
import json
import sys

try:
    print(json.load(sys.stdin).get("status", ""))
except Exception:
    print("")
'
    )"
    if [[ "$status" == "UP" ]]; then
      return
    fi
    sleep 2
  done

  fail "API health did not become UP within ${HEALTH_TIMEOUT_SECONDS}s"
}

create_upload_file() {
  local path="$1"
  "$PYTHON_BIN" -c '
import sys

path = sys.argv[1]
payload = (
    b"\x89PNG\r\n\x1a\n"
    b"time-archive-admin-preview-verification\n"
    b"not-a-real-renderable-png-but-stable-test-bytes\n"
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

download_preview_file() {
  local preview_url="$1"
  local output_path="$2"
  local status_file status

  status_file="$(mktemp)"
  if ! curl -sS "$preview_url" \
    -o "$output_path" \
    -w "%{http_code}" > "$status_file"; then
    rm -f "$status_file"
    fail "Preview URL download failed"
  fi

  status="$(cat "$status_file")"
  rm -f "$status_file"
  [[ "$status" -ge 200 && "$status" -le 299 ]] || fail "Preview URL download returned HTTP $status"
}

require_command cmp
require_command curl
require_command wc
PYTHON_BIN="$(detect_python)"
SESSION_COOKIE_FILE="$(mktemp)"

log "Using BASE_URL=$BASE_URL"
log "Using range [$START_SECOND, $END_SECOND)"

wait_for_health
log "Health check passed"
refresh_csrf_token
log "CSRF token fetched"

register_body="$("$PYTHON_BIN" -c '
import json
import os
import uuid

print(json.dumps({
    "email": "admin-preview-%s-%s-%s@example.com" % (
        os.environ["START_SECOND"],
        os.environ["END_SECOND"],
        uuid.uuid4().hex,
    ),
    "password": "password123",
    "displayName": "Admin Preview User",
}))
')"
current_user="$(request_json POST "$BASE_URL/api/auth/register" "$register_body")"
current_user_id="$(printf '%s' "$current_user" | json_get userId)"
[[ -n "$current_user_id" ]] || fail "Authenticated user ID was empty"
log "Authenticated user created: $current_user_id"
refresh_csrf_token
log "CSRF token refreshed after authentication"

availability="$(request_json GET "$BASE_URL/api/archive/availability?startSecond=$START_SECOND&endSecond=$END_SECOND")"
available="$(printf '%s' "$availability" | json_get available)"
[[ "$available" == "true" ]] || fail "Range is not available. Use START_SECOND and END_SECOND to choose another range."
log "Initial availability passed"

reservation_body="$("$PYTHON_BIN" -c '
import json
import os

print(json.dumps({
    "startSecond": int(os.environ["START_SECOND"]),
    "endSecond": int(os.environ["END_SECOND"]),
}))
')"
reservation="$(request_json POST "$BASE_URL/api/purchase/reservations" "$reservation_body")"
reservation_id="$(printf '%s' "$reservation" | json_get reservationId)"
[[ -n "$reservation_id" ]] || fail "Reservation ID was empty"
log "Reservation created: $reservation_id"

checkout="$(request_json POST "$BASE_URL/api/purchase/reservations/$reservation_id/checkout")"
checkout_url="$(printf '%s' "$checkout" | json_get checkoutUrl)"
[[ -n "$checkout_url" ]] || fail "Checkout URL was empty"
log "Checkout created: $checkout_url"

export RESERVATION_ID="$reservation_id"
webhook_body="$("$PYTHON_BIN" -c '
import json
import os

print(json.dumps({
    "providerEventId": os.environ["EVENT_ID"],
    "eventType": os.environ["EVENT_TYPE"],
    "payloadHash": os.environ["PAYLOAD_HASH"],
    "reservationId": os.environ["RESERVATION_ID"],
    "paymentReference": os.environ["PAYMENT_REFERENCE"],
    "requestId": os.environ["REQUEST_ID"],
}))
')"

payment="$(request_json POST "$BASE_URL/api/internal/payments/fake/webhooks/primary-purchase-completed" "$webhook_body")"
ownership_record_id="$(printf '%s' "$payment" | json_get ownershipRecordId)"
[[ -n "$ownership_record_id" ]] || fail "Ownership record ID was empty"
log "Ownership created: $ownership_record_id"

upload_file_path="$(mktemp)"
download_file_path="$(mktemp)"
trap 'rm -f "$upload_file_path" "$download_file_path" "$SESSION_COOKIE_FILE"' EXIT
create_upload_file "$upload_file_path"
content_length_bytes="$(wc -c < "$upload_file_path" | tr -d '[:space:]')"
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
    "$upload_request_body"
)"
upload_request_id="$(printf '%s' "$upload_request" | json_get uploadRequestId)"
upload_url="$(printf '%s' "$upload_request" | json_get uploadUrl)"
original_file_url="$(printf '%s' "$upload_request" | json_get originalFileUrl)"
[[ -n "$upload_request_id" ]] || fail "Upload request ID was empty"
[[ -n "$upload_url" ]] || fail "Upload URL was empty"
[[ -n "$original_file_url" ]] || fail "Original file URL was empty"
log "Upload request created: $upload_request_id"

upload_file "$upload_url" "$upload_file_path"
log "Object uploaded through presigned URL"

completion="$(
  request_json \
    POST \
    "$BASE_URL/api/owned-ranges/$ownership_record_id/media/upload-requests/$upload_request_id/complete"
)"
media_asset_id="$(printf '%s' "$completion" | json_get mediaAsset.mediaAssetId)"
moderation_status="$(printf '%s' "$completion" | json_get mediaAsset.moderationStatus)"
[[ -n "$media_asset_id" ]] || fail "Media asset ID was empty"
[[ "$moderation_status" == "UPLOADED" ]] || fail "Expected media asset moderationStatus=UPLOADED"
log "Upload completed and media asset created: $media_asset_id"

admin_user="$(authenticate_admin)"
admin_user_id="$(printf '%s' "$admin_user" | json_get userId)"
admin_role="$(printf '%s' "$admin_user" | json_get role)"
[[ -n "$admin_user_id" ]] || fail "Admin user ID was empty"
[[ "$admin_role" == "ADMIN" ]] || fail "Expected admin user role=ADMIN"
log "Admin authenticated: $admin_user_id"

preview_response="$(request_json GET "$BASE_URL/api/admin/media/assets/$media_asset_id/preview-url")"
preview_media_asset_id="$(printf '%s' "$preview_response" | json_get mediaAssetId)"
preview_url="$(printf '%s' "$preview_response" | json_get previewUrl)"
preview_expires_at="$(printf '%s' "$preview_response" | json_get expiresAt)"
[[ "$preview_media_asset_id" == "$media_asset_id" ]] || fail "Preview response mediaAssetId did not match"
[[ -n "$preview_url" ]] || fail "Preview URL was empty"
[[ -n "$preview_expires_at" ]] || fail "Preview expiresAt was empty"
log "Admin preview URL created"

download_preview_file "$preview_url" "$download_file_path"
cmp -s "$upload_file_path" "$download_file_path" || fail "Preview download bytes did not match uploaded object"
log "Preview URL downloaded uploaded object bytes"

log "Local admin preview flow verification passed"
