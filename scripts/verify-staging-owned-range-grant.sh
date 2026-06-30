#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-owned-range-grant] %s\n' "$1"
}

fail() {
  printf '[verify-staging-owned-range-grant] ERROR: %s\n' "$1" >&2
  exit 1
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="$ROOT_DIR/scripts/grant-staging-owned-range.sh"

[[ -f "$SCRIPT" ]] || fail "Script not found: $SCRIPT"
bash -n "$SCRIPT"
log "Shell syntax passed"

if command -v python3 >/dev/null 2>&1 && python3 -c 'import re' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'import re' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  fail "python3 or python is required"
fi

"$PYTHON_BIN" - "$SCRIPT" <<'PY'
import re
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    script = source.read()

errors = []

required = [
    "--expected-account-id",
    "--email",
    "--start-second",
    "--end-second",
    "--dry-run",
    "ap-northeast-2",
    "time-archive-staging",
    "/time-archive/staging/",
    "/time-archive/staging/database/username",
    "/time-archive/staging/database/password",
    "timearchive_app",
    "AWS-RunShellScript",
    "ssm send-command",
    "ssm wait command-executed",
    "get-command-invocation",
    "docker run --rm --network host",
    "postgres:18-alpine",
    "ADMIN_GRANT",
    "target user does not exist",
    "target range overlaps active ownership",
    "int8range(start_second, end_second, '[)')",
    "END_SECOND <= 86400",
    "END_SECOND > START_SECOND",
    "normalized_email",
]
for text in required:
    if text not in script:
        errors.append(f"missing required behavior: {text}")

for forbidden in [
    "TIME_ARCHIVE_INITIAL_ADMIN_EMAILS",
    "/time-archive/bootstrap/staging/database/master-password",
    "MASTER_PASSWORD",
    "PRIMARY_PURCHASE",
    "purchase_reservations",
    "payment_transactions",
    "payment_outbox",
]:
    if forbidden in script:
        errors.append(f"forbidden behavior is present: {forbidden}")

if re.search(r"APP_PASSWORD.*printf|echo.*APP_PASSWORD|log .*PASSWORD", script):
    errors.append("script appears to print a password variable")

if "Authenticated AWS account $account_id does not match expected $EXPECTED_ACCOUNT_ID" not in script:
    errors.append("script must fail when AWS account does not match the expected account")

if "Unexpected runtime parameter path: $RUNTIME_PARAMETER_PATH" not in script:
    errors.append("script must validate the staging runtime parameter path")

if "Comment\": \"Grant Time Archive staging owned range\"" not in script:
    errors.append("SSM command comment must identify the audited operation")

if errors:
    raise SystemExit("\n".join(f"- {error}" for error in errors))

print("staging owned range grant policy validation passed")
PY

log "Staging owned range grant validation passed"
