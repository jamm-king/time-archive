#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-cloudflare-edge-hardening] %s\n' "$1"
}

fail() {
  printf '[verify-cloudflare-edge-hardening] ERROR: %s\n' "$1" >&2
  exit 1
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_PROXY="$ROOT_DIR/apps/web/src/lib/backend-proxy.ts"
RUNBOOK="$ROOT_DIR/docs/operations/cloudflare-edge-hardening.md"
RUNTIME_DOC="$ROOT_DIR/docs/operations/staging-runtime-parameters.md"
ARCHITECTURE_DOC="$ROOT_DIR/docs/operations/ec2-rds-deployment-architecture.md"
CHECKLIST="$ROOT_DIR/docs/operations/release-readiness-checklist.md"

for file in "$WEB_PROXY" "$RUNBOOK" "$RUNTIME_DOC" "$ARCHITECTURE_DOC" "$CHECKLIST"; do
  [[ -f "$file" ]] || fail "Required file is missing: $file"
done

python_bin=""
if command -v python3 >/dev/null 2>&1 && python3 -c 'print("ok")' >/dev/null 2>&1; then
  python_bin=python3
elif command -v python >/dev/null 2>&1 && python -c 'print("ok")' >/dev/null 2>&1; then
  python_bin=python
else
  fail "python3 or python is required"
fi

"$python_bin" - "$WEB_PROXY" "$RUNBOOK" "$RUNTIME_DOC" "$ARCHITECTURE_DOC" "$CHECKLIST" <<'PY'
import re
import sys

web_proxy, runbook, runtime_doc, architecture_doc, checklist = sys.argv[1:6]

with open(web_proxy, encoding="utf-8") as source:
    web_proxy_text = source.read()
with open(runbook, encoding="utf-8") as source:
    runbook_text = source.read()
with open(runtime_doc, encoding="utf-8") as source:
    runtime_text = source.read()
with open(architecture_doc, encoding="utf-8") as source:
    architecture_text = source.read()
with open(checklist, encoding="utf-8") as source:
    checklist_text = source.read()

required_proxy_tokens = (
    "FORWARDED_CLOUDFLARE_HEADERS",
    '"cf-connecting-ip"',
    '"cf-ray"',
    '"cf-visitor"',
    '"cf-ipcountry"',
    "headers.set(headerName, headerValue)",
)
for token in required_proxy_tokens:
    if token not in web_proxy_text:
        raise SystemExit(f"Web proxy is missing required Cloudflare forwarding token: {token}")

for forbidden in ('"x-forwarded-for"', '"true-client-ip"'):
    if forbidden in web_proxy_text:
        raise SystemExit(f"Web proxy must not forward broad/spoof-prone client IP header: {forbidden}")

required_runbook_tokens = (
    "CF-Connecting-IP",
    "TIME_ARCHIVE_RATE_LIMIT_CLIENT_IP_HEADER=CF-Connecting-IP",
    "Cache Rules",
    "Bypass cache",
    "WAF",
    "Rate limiting",
    "direct origin",
    "Smoke staging public",
    "Smoke staging request ID",
    "Smoke staging auth",
)
for token in required_runbook_tokens:
    if token not in runbook_text:
        raise SystemExit(f"Cloudflare edge hardening runbook is missing: {token}")

if "CF-Connecting-IP" not in runtime_text:
    raise SystemExit("staging runtime parameter documentation must mention CF-Connecting-IP")
if "CF-Connecting-IP" not in architecture_text:
    raise SystemExit("deployment architecture documentation must mention CF-Connecting-IP")

match = re.search(r"\| Edge rate limiting and client identity \| ([^|]+) \|", checklist_text)
if not match:
    raise SystemExit("release readiness checklist is missing edge rate limiting row")
if "Needs verification" not in match.group(1):
    raise SystemExit("edge rate limiting row must remain Needs verification until Cloudflare rules are manually verified")

print("cloudflare edge hardening validation passed")
PY

log "Cloudflare edge hardening validation passed"
