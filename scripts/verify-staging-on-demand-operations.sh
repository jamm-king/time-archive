#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-on-demand-operations] %s\n' "$1"
}

fail() {
  printf '[verify-staging-on-demand-operations] ERROR: %s\n' "$1" >&2
  exit 1
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
START_SCRIPT="$ROOT_DIR/scripts/start-staging-stack.sh"
STOP_SCRIPT="$ROOT_DIR/scripts/stop-staging-stack.sh"

[[ -f "$START_SCRIPT" ]] || fail "Script not found: $START_SCRIPT"
[[ -f "$STOP_SCRIPT" ]] || fail "Script not found: $STOP_SCRIPT"

bash -n "$START_SCRIPT" "$STOP_SCRIPT"
log "Shell syntax passed"

if command -v python3 >/dev/null 2>&1 && python3 -c 'import re' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'import re' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  fail "python3 or python is required"
fi

"$PYTHON_BIN" - "$START_SCRIPT" "$STOP_SCRIPT" <<'PY'
import re
import sys

start_path, stop_path = sys.argv[1:3]
with open(start_path, encoding="utf-8") as source:
    start_script = source.read()
with open(stop_path, encoding="utf-8") as source:
    stop_script = source.read()

errors = []

shared_required = [
    "--expected-account-id",
    "--dry-run",
    "ap-northeast-2",
    "time-archive-staging",
    "time-archive-staging-postgres",
    "/time-archive/staging/",
    "ApplicationInstanceId",
    "RuntimeParameterPath",
    "sts get-caller-identity",
    "cloudformation describe-stacks",
    "describe-db-instances",
    "describe-instances",
    "Authenticated AWS account $account_id does not match expected $EXPECTED_ACCOUNT_ID",
    "Refusing to",
]
for script_name, script in (("start", start_script), ("stop", stop_script)):
    for text in shared_required:
        if text not in script:
            errors.append(f"{script_name} script missing required behavior: {text}")
    for forbidden in [
        "time-archive-production",
        "/time-archive/production/",
        "delete-stack",
        "delete-db-instance",
        "terminate-instances",
        "create-db-snapshot",
        "delete-db-snapshot",
    ]:
        if forbidden in script:
            errors.append(f"{script_name} script contains forbidden behavior: {forbidden}")
    if re.search(r"DB_INSTANCE_IDENTIFIER=.*production", script):
        errors.append(f"{script_name} script appears to target production DB")

start_required = [
    "rds start-db-instance",
    "rds wait db-instance-available",
    "ec2 start-instances",
    "ec2 wait instance-running",
    "ec2 wait instance-status-ok",
    "Run staging deploy and smoke workflows explicitly.",
]
for text in start_required:
    if text not in start_script:
        errors.append(f"start script missing required behavior: {text}")

stop_required = [
    "ec2 stop-instances",
    "ec2 wait instance-stopped",
    "rds stop-db-instance",
    "rds wait db-instance-stopped",
    "automatically restart after seven consecutive stopped days",
]
for text in stop_required:
    if text not in stop_script:
        errors.append(f"stop script missing required behavior: {text}")

if "start-db-instance" in stop_script:
    errors.append("stop script must not start RDS")
if "stop-db-instance" in start_script:
    errors.append("start script must not stop RDS")
if "start-instances" in stop_script:
    errors.append("stop script must not start EC2")
if "stop-instances" in start_script:
    errors.append("start script must not stop EC2")

if errors:
    raise SystemExit("\n".join(f"- {error}" for error in errors))

print("staging on-demand operation policy validation passed")
PY

log "Staging on-demand operation validation passed"
