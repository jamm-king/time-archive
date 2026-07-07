#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-production-public-smoke] %s\n' "$1"
}

fail() {
  printf '[verify-production-public-smoke] ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: scripts/verify-production-public-smoke.sh [--base-url URL]

Checks the production public HTTPS hostname without authentication or mutation.

Options:
  --base-url URL  Public production base URL. Falls back to PRODUCTION_PUBLIC_BASE_URL.
  -h, --help      Show this help.
USAGE
}

BASE_URL="${PRODUCTION_PUBLIC_BASE_URL:-}"

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

[[ -n "$BASE_URL" ]] || fail "Set --base-url or PRODUCTION_PUBLIC_BASE_URL"
[[ "$BASE_URL" =~ ^https:// ]] || fail "Base URL must use HTTPS"
BASE_URL="${BASE_URL%/}"

command -v curl >/dev/null 2>&1 || fail "curl is required"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

root_body="$TMP_DIR/root.body"
timeline_body="$TMP_DIR/timeline.body"

log "Checking web root at $BASE_URL"
curl --fail --location --silent --show-error --max-time 20 "$BASE_URL" \
  --output "$root_body"
[[ -s "$root_body" ]] || fail "Web root returned an empty body"

log "Checking public timeline through production hostname"
curl --fail --location --silent --show-error --max-time 20 \
  "$BASE_URL/api/timeline?from=0&to=1" \
  --output "$timeline_body"
[[ -s "$timeline_body" ]] || fail "Timeline endpoint returned an empty body"

if command -v python3 >/dev/null 2>&1 && python3 -c 'import json' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'import json' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  PYTHON_BIN=""
fi

if [[ -n "$PYTHON_BIN" ]]; then
  "$PYTHON_BIN" - "$timeline_body" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    payload = json.load(source)

if not isinstance(payload, (dict, list)):
    raise SystemExit("timeline response must be a JSON object or array")
PY
else
  log "Skipping JSON shape validation because Python is unavailable"
fi

log "Production public smoke check passed"
