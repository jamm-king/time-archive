#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
START_SECOND="${START_SECOND:-100}"
END_SECOND="${END_SECOND:-110}"
EVENT_ID="${EVENT_ID:-evt_local_${START_SECOND}_${END_SECOND}}"
PAYMENT_REFERENCE="${PAYMENT_REFERENCE:-pi_local_${START_SECOND}_${END_SECOND}}"
REQUEST_ID="${REQUEST_ID:-local-request-${START_SECOND}-${END_SECOND}}"
EVENT_TYPE="${EVENT_TYPE:-payment_intent.succeeded}"
PAYLOAD_HASH="${PAYLOAD_HASH:-sha256-local-${START_SECOND}-${END_SECOND}}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-120}"

export BASE_URL
export START_SECOND
export END_SECOND
export EVENT_ID
export PAYMENT_REFERENCE
export REQUEST_ID
export EVENT_TYPE
export PAYLOAD_HASH
export HEALTH_TIMEOUT_SECONDS

log() {
  printf '[verify] %s\n' "$1"
}

fail() {
  printf '[verify] ERROR: %s\n' "$1" >&2
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

require_command curl
PYTHON_BIN="$(detect_python)"
SESSION_COOKIE_FILE="$(mktemp)"
trap 'rm -f "$SESSION_COOKIE_FILE"' EXIT

log "Using BASE_URL=$BASE_URL"
log "Using range [$START_SECOND, $END_SECOND)"

wait_for_health
log "Health check passed"

register_body="$("$PYTHON_BIN" -c '
import json
import os
import uuid

print(json.dumps({
    "email": "purchase-%s-%s-%s@example.com" % (
        os.environ["START_SECOND"],
        os.environ["END_SECOND"],
        uuid.uuid4().hex,
    ),
    "password": "password123",
    "displayName": "Purchase User",
}))
')"
current_user="$(request_json POST "$BASE_URL/api/auth/register" "$register_body")"
buyer_id="$(printf '%s' "$current_user" | json_get userId)"
[[ -n "$buyer_id" ]] || fail "Authenticated user ID was empty"
log "Authenticated user created: $buyer_id"

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
already_processed="$(printf '%s' "$payment" | json_get alreadyProcessed)"
purchase_id="$(printf '%s' "$payment" | json_get purchaseId)"
[[ "$already_processed" == "false" ]] || fail "Expected first webhook to process payment"
[[ -n "$purchase_id" ]] || fail "Purchase ID was empty"
log "Payment completed: $purchase_id"

duplicate_payment="$(request_json POST "$BASE_URL/api/internal/payments/fake/webhooks/primary-purchase-completed" "$webhook_body")"
duplicate_already_processed="$(printf '%s' "$duplicate_payment" | json_get alreadyProcessed)"
[[ "$duplicate_already_processed" == "true" ]] || fail "Expected duplicate webhook to be alreadyProcessed=true"
log "Duplicate webhook idempotency passed"

final_availability="$(request_json GET "$BASE_URL/api/archive/availability?startSecond=$START_SECOND&endSecond=$END_SECOND")"
final_available="$(printf '%s' "$final_availability" | json_get available)"
[[ "$final_available" == "false" ]] || fail "Expected completed range to be unavailable"
log "Final availability passed"

log "Local purchase flow verification passed"
