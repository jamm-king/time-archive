#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-rollback-drill] %s\n' "$1"
}

fail() {
  printf '[verify-staging-rollback-drill] ERROR: %s\n' "$1" >&2
  exit 1
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="$ROOT_DIR/scripts/inspect-staging-release-state.sh"
RUNBOOK="$ROOT_DIR/docs/operations/staging-rollback-drill.md"
DEPLOY_DOC="$ROOT_DIR/docs/operations/staging-deployment.md"

[[ -f "$SCRIPT" ]] || fail "Script not found: $SCRIPT"
[[ -f "$RUNBOOK" ]] || fail "Runbook not found: $RUNBOOK"
[[ -f "$DEPLOY_DOC" ]] || fail "Deployment doc not found: $DEPLOY_DOC"

bash -n "$SCRIPT"
bash -n "$0"
log "Shell syntax passed"

if command -v python3 >/dev/null 2>&1 && python3 -c 'import re' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'import re' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  fail "python3 or python is required"
fi

"$PYTHON_BIN" - "$SCRIPT" "$RUNBOOK" "$DEPLOY_DOC" <<'PY'
import re
import sys

script_path, runbook_path, deploy_doc_path = sys.argv[1:4]
with open(script_path, encoding="utf-8") as source:
    script = source.read()
with open(runbook_path, encoding="utf-8") as source:
    runbook = source.read()
with open(deploy_doc_path, encoding="utf-8") as source:
    deploy_doc = source.read()

errors = []

script_required = [
    "--expected-account-id",
    "--dry-run",
    "ap-northeast-2",
    "time-archive-staging",
    "/time-archive/staging/",
    "/var/lib/time-archive/deployments",
    "current.env",
    "previous.env",
    "API_IMAGE",
    "WEB_IMAGE",
    "REDIS_IMAGE",
    "CLOUDFLARED_IMAGE",
    "DEPLOYED_AT",
    "AWS-RunShellScript",
    "Inspect Time Archive staging release state",
    "ssm send-command",
    "ssm wait command-executed",
    "get-command-invocation",
]
for text in script_required:
    if text not in script:
        errors.append(f"inspection script is missing required behavior: {text}")

script_forbidden = [
    "--with-decryption",
    "get-parameter",
    "get-parameters-by-path",
    "docker compose up",
    "deploy.sh staging",
    "rm -rf",
    "DROP ",
    "DELETE ",
    "TRUNCATE ",
    "DATABASE_PASSWORD",
    "SECRET",
    "ACCESS_KEY",
]
for text in script_forbidden:
    if text in script:
        errors.append(f"inspection script contains forbidden behavior: {text}")

if "Authenticated AWS account $account_id does not match expected $EXPECTED_ACCOUNT_ID" not in script:
    errors.append("inspection script must fail when AWS account does not match the expected account")
if "Unexpected runtime parameter path: $RUNTIME_PARAMETER_PATH" not in script:
    errors.append("inspection script must validate the staging runtime parameter path")

runbook_required = [
    "Deploy staging",
    "Smoke staging public",
    "Smoke staging auth",
    "Smoke staging admin",
    "Smoke staging media preview",
    "scripts/inspect-staging-release-state.sh",
    "previous.env",
    "current.env",
    "image_sha",
    "redis_image",
    "cloudflared_image",
    "Database rollback is out of scope",
    "forward recovery",
]
for text in runbook_required:
    if text not in runbook:
        errors.append(f"rollback runbook is missing required content: {text}")

if re.search(r"(?<![0-9])[0-9]{12}(?![0-9])", runbook):
    errors.append("rollback runbook must not contain a literal AWS account ID")
if re.search(r"(password|secret|access key)", runbook, re.IGNORECASE):
    errors.append("rollback runbook must not ask operators to handle secrets")

if "Staging Rollback Drill" not in deploy_doc:
    errors.append("deployment runbook must link to staging rollback drill")

if errors:
    raise SystemExit("\n".join(f"- {error}" for error in errors))

print("staging rollback drill policy validation passed")
PY

log "Staging rollback drill validation passed"
