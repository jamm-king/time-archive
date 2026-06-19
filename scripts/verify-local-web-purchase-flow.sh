#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3000}"
START_SECOND="${START_SECOND:-4000}"
END_SECOND="${END_SECOND:-4001}"
READY_TIMEOUT_SECONDS="${READY_TIMEOUT_SECONDS:-120}"

export BASE_URL
export START_SECOND
export END_SECOND
export READY_TIMEOUT_SECONDS

log() {
  printf '[verify-web-purchase] %s\n' "$1"
}

fail() {
  printf '[verify-web-purchase] ERROR: %s\n' "$1" >&2
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

require_command curl
PYTHON_BIN="$(detect_python)"
SESSION_COOKIE_FILE="$(mktemp)"
trap 'rm -f "$SESSION_COOKIE_FILE"' EXIT

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
    "email": "web-purchase-%s-%s-%s@example.com" % (
        os.environ["START_SECOND"],
        os.environ["END_SECOND"],
        uuid.uuid4().hex,
    ),
    "password": "password123",
    "displayName": "Web Purchase User",
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
checkout_provider="$(printf '%s' "$checkout" | json_get provider)"
checkout_url="$(printf '%s' "$checkout" | json_get checkoutUrl)"
[[ "$checkout_provider" == "fake" ]] || fail "Expected fake checkout provider"
[[ -n "$checkout_url" ]] || fail "Checkout URL was empty"
log "Checkout created: $checkout_url"

export RESERVATION_ID="$reservation_id"
payment_body="$("$PYTHON_BIN" -c '
import json
import os
import uuid

request_id = "web-purchase-%s" % uuid.uuid4().hex
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
already_processed="$(printf '%s' "$payment" | json_get alreadyProcessed)"
[[ -n "$ownership_record_id" ]] || fail "Ownership record ID was empty"
[[ "$already_processed" == "false" ]] || fail "Expected first payment completion to process"
log "Payment completed with ownership: $ownership_record_id"

owned_ranges="$(request_json GET "$BASE_URL/api/me/owned-ranges" 200)"
owned_range_found="$(printf '%s' "$owned_ranges" | json_array_contains_id ownershipRecordId "$ownership_record_id")"
[[ "$owned_range_found" == "true" ]] || fail "Purchased ownership record was not listed in current user's owned ranges"
log "Owned range listing includes purchased range"

final_availability="$(request_json GET "$BASE_URL/api/archive/availability?startSecond=$START_SECOND&endSecond=$END_SECOND" 200)"
final_available="$(printf '%s' "$final_availability" | json_get available)"
[[ "$final_available" == "false" ]] || fail "Expected purchased range to be unavailable"
log "Final availability passed"

log "Local web purchase flow verification passed"
