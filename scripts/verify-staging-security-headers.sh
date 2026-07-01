#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-security-headers] %s\n' "$1"
}

fail() {
  printf '[verify-staging-security-headers] ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: scripts/verify-staging-security-headers.sh [--base-url URL]

Verifies security headers through the deployed public staging HTTPS hostname.

Options:
  --base-url URL  Public staging base URL. Falls back to STAGING_PUBLIC_BASE_URL.
  -h, --help      Show this help.
USAGE
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

detect_python() {
  if command -v python3 >/dev/null 2>&1; then
    printf 'python3'
    return
  fi
  if command -v python >/dev/null 2>&1; then
    printf 'python'
    return
  fi
  fail "Required command not found: python3 or python"
}

assert_headers() {
  local path="$1"
  local headers_file status_file status

  headers_file="$(mktemp)"
  status_file="$(mktemp)"

  if ! curl \
    --silent \
    --show-error \
    --location \
    --dump-header "$headers_file" \
    --output /dev/null \
    --write-out "%{http_code}" \
    "$BASE_URL$path" > "$status_file"; then
    rm -f "$headers_file" "$status_file"
    fail "Request failed: GET $BASE_URL$path"
  fi

  status="$(cat "$status_file")"
  rm -f "$status_file"

  if [[ "$status" != 2* ]]; then
    cat "$headers_file" >&2
    rm -f "$headers_file"
    fail "Expected 2xx but got HTTP $status from GET $BASE_URL$path"
  fi

  "$PYTHON_BIN" - "$headers_file" "$path" <<'PY'
import re
import sys

headers_path = sys.argv[1]
path = sys.argv[2]

with open(headers_path, encoding="utf-8") as source:
    raw = source.read()

blocks = [
    block for block in re.split(r"\r?\n\r?\n", raw.strip())
    if block.strip()
]
if not blocks:
    raise SystemExit(f"{path}: response headers were empty")

headers = {}
for line in blocks[-1].splitlines():
    if ":" not in line:
        continue
    name, value = line.split(":", 1)
    headers[name.strip().lower()] = value.strip()

expected_exact = {
    "x-content-type-options": "nosniff",
    "x-frame-options": "DENY",
    "referrer-policy": "strict-origin-when-cross-origin",
}
for name, expected in expected_exact.items():
    actual = headers.get(name)
    if actual != expected:
        raise SystemExit(f"{path}: expected {name}: {expected!r}, got {actual!r}")

hsts = headers.get("strict-transport-security")
if not hsts:
    raise SystemExit(f"{path}: missing strict-transport-security")
if "includesubdomains" not in hsts.lower():
    raise SystemExit(f"{path}: HSTS must include includeSubDomains")
match = re.search(r"(?:^|;)\s*max-age=(\d+)", hsts, re.IGNORECASE)
if not match or int(match.group(1)) < 31536000:
    raise SystemExit(f"{path}: HSTS max-age must be at least 31536000")

csp = headers.get("content-security-policy")
if not csp:
    raise SystemExit(f"{path}: missing content-security-policy")
required_csp = (
    "frame-ancestors 'none'",
    "object-src 'none'",
    "base-uri 'self'",
)
for directive in required_csp:
    if directive not in csp:
        raise SystemExit(f"{path}: CSP missing directive {directive!r}")

permissions = headers.get("permissions-policy")
if not permissions:
    raise SystemExit(f"{path}: missing permissions-policy")
for directive in ("camera=()", "microphone=()", "geolocation=()"):
    if directive not in permissions:
        raise SystemExit(f"{path}: Permissions-Policy missing {directive!r}")
PY

  rm -f "$headers_file"
}

BASE_URL="${STAGING_PUBLIC_BASE_URL:-}"

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

log "Using BASE_URL=$BASE_URL"
assert_headers "/"
log "Web root security headers passed"
assert_headers "/api/timeline?from=0&to=1"
log "Public API proxy security headers passed"
log "Staging security headers check passed"
