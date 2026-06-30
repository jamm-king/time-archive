#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-media-preview-smoke-workflow] %s\n' "$1"
}

fail() {
  printf '[verify-staging-media-preview-smoke-workflow] ERROR: %s\n' "$1" >&2
  exit 1
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="$ROOT_DIR/.github/workflows/smoke-staging-media-preview.yml"
SCRIPT="$ROOT_DIR/scripts/verify-staging-media-preview-smoke.sh"

[[ -f "$WORKFLOW" ]] || fail "Workflow not found: $WORKFLOW"
[[ -f "$SCRIPT" ]] || fail "Smoke script not found: $SCRIPT"

if command -v python3 >/dev/null 2>&1 && python3 -c 'import yaml' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'import yaml' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  fail "python3 or python with PyYAML is required"
fi

"$PYTHON_BIN" - "$WORKFLOW" "$SCRIPT" <<'PY'
import copy
import re
import sys

import yaml


CHECKOUT_REVISION = "34e114876b0b11c390a56381ad16ebd13914f8d5"


def as_bool(value):
    return str(value).lower() == "true"


def collect_secret_refs(value):
    raw = yaml.dump(value)
    return set(re.findall(r"secrets\.([A-Z0-9_]+)", raw))


def validate(workflow, script_text):
    errors = []

    triggers = workflow.get("on")
    if not isinstance(triggers, dict) or set(triggers) != {"workflow_dispatch"}:
        errors.append("workflow must be manual workflow_dispatch only")

    inputs = triggers.get("workflow_dispatch", {}).get("inputs", {})
    if set(inputs) != {"public_base_url", "start_second", "end_second"}:
        errors.append("workflow inputs must contain only public_base_url, start_second, and end_second")
    if as_bool(inputs.get("public_base_url", {}).get("required")):
        errors.append("public_base_url must remain optional")
    if inputs.get("start_second", {}).get("default") != "7000":
        errors.append("start_second default must remain 7000")
    if inputs.get("end_second", {}).get("default") != "7001":
        errors.append("end_second default must remain 7001")

    if workflow.get("permissions") != {"contents": "read"}:
        errors.append("workflow permissions must be contents: read only")

    concurrency = workflow.get("concurrency", {})
    if concurrency.get("group") != "staging-media-preview-smoke":
        errors.append("workflow must serialize the staging media preview smoke group")
    if as_bool(concurrency.get("cancel-in-progress")):
        errors.append("workflow must not cancel an in-progress media preview smoke check")

    env = workflow.get("env", {})
    if env != {"STAGING_PUBLIC_BASE_URL": "${{ vars.STAGING_PUBLIC_BASE_URL }}"}:
        errors.append("workflow must read only the reviewed staging public URL variable")

    raw_workflow = yaml.dump(workflow)
    if "id-token" in raw_workflow:
        errors.append("workflow must not request OIDC permissions")
    if re.search(r"(?<![0-9])[0-9]{12}(?![0-9])", raw_workflow):
        errors.append("workflow must not contain a literal AWS account ID")

    secret_refs = collect_secret_refs(workflow)
    if secret_refs != {"STAGING_ADMIN_EMAIL", "STAGING_ADMIN_PASSWORD"}:
        errors.append(f"workflow secret references are not the reviewed set: {sorted(secret_refs)}")

    jobs = workflow.get("jobs", {})
    if set(jobs) != {"smoke"}:
        errors.append("workflow must contain only the smoke job")
        return errors

    job = jobs["smoke"]
    if job.get("environment") != "staging":
        errors.append("smoke job must use the staging environment")
    if "refs/heads/main" not in str(job.get("if", "")):
        errors.append("smoke job must be limited to refs/heads/main")
    if job.get("runs-on") != "ubuntu-latest":
        errors.append("smoke job must use ubuntu-latest")
    try:
        if int(job.get("timeout-minutes", "0")) > 10:
            errors.append("smoke job timeout must not exceed 10 minutes")
    except ValueError:
        errors.append("smoke job timeout must be numeric")

    actions = [
        step.get("uses")
        for step in job.get("steps", [])
        if isinstance(step, dict) and step.get("uses")
    ]
    if actions != [f"actions/checkout@{CHECKOUT_REVISION}"]:
        errors.append("workflow action dependency set must contain only pinned checkout")

    step_text = "\n".join(
        str(step.get("run", "")) for step in job.get("steps", []) if isinstance(step, dict)
    )
    for required in (
        "verify-staging-media-preview-smoke.sh",
        "INPUT_PUBLIC_BASE_URL",
        "INPUT_START_SECOND",
        "INPUT_END_SECOND",
        "STAGING_PUBLIC_BASE_URL",
        "GITHUB_STEP_SUMMARY",
    ):
        if required not in step_text:
            errors.append(f"workflow is missing required media preview smoke behavior: {required}")

    for required in (
        "https://",
        "/api/csrf",
        "/api/auth/login",
        "/api/me/owned-ranges",
        "/api/owned-ranges/$ownership_record_id/media/upload-requests",
        "/api/admin/media/assets?status=UPLOADED",
        "/api/admin/media/assets/$MEDIA_ASSET_ID/preview-url",
        "STAGING_ADMIN_EMAIL",
        "STAGING_ADMIN_PASSWORD",
        "staging-media-preview-smoke.png",
        "cmp -s",
    ):
        if required not in script_text:
            errors.append(f"script is missing required media preview smoke behavior: {required}")

    for forbidden in ("/approve", "/reject", "/hide", "/api/internal/payments/fake"):
        if forbidden in script_text:
            errors.append(f"script must not exercise forbidden endpoint: {forbidden}")
    for forbidden in ("preview_url", "upload_url"):
        if re.search(rf"(echo|printf|log).*{forbidden}", script_text):
            errors.append(f"script appears to print sensitive URL variable: {forbidden}")
    if re.search(r"(echo|printf|log).*STAGING_ADMIN_PASSWORD", script_text):
        errors.append("script appears to print the admin password variable")

    return errors


with open(sys.argv[1], encoding="utf-8") as source:
    workflow = yaml.load(source, Loader=yaml.BaseLoader)
with open(sys.argv[2], encoding="utf-8") as source:
    script_text = source.read()

errors = validate(workflow, script_text)
if errors:
    raise SystemExit("\n".join(f"- {error}" for error in errors))

mutated = copy.deepcopy(workflow)
mutated["on"]["push"] = {"branches": ["main"]}
if not validate(mutated, script_text):
    raise SystemExit("policy self-test failed to detect automatic trigger")

mutated = copy.deepcopy(workflow)
mutated["permissions"]["id-token"] = "write"
if not validate(mutated, script_text):
    raise SystemExit("policy self-test failed to detect OIDC permission")

mutated = copy.deepcopy(workflow)
mutated["jobs"]["smoke"].pop("environment", None)
if not validate(mutated, script_text):
    raise SystemExit("policy self-test failed to detect staging environment removal")

mutated = copy.deepcopy(workflow)
mutated["jobs"]["smoke"]["steps"][1]["env"]["EXTRA_SECRET"] = "${{ secrets.EXTRA_SECRET }}"
if not validate(mutated, script_text):
    raise SystemExit("policy self-test failed to detect unexpected secret")

mutated_script = script_text.replace("/api/me/owned-ranges", "/api/me")
if not validate(workflow, mutated_script):
    raise SystemExit("policy self-test failed to detect missing owned range lookup")

mutated_script = script_text + "\ncurl /api/admin/media/assets/$MEDIA_ASSET_ID/approve\n"
if not validate(workflow, mutated_script):
    raise SystemExit("policy self-test failed to detect forbidden approval endpoint")

print("staging media preview smoke workflow policy validation passed")
PY

log "Staging media preview smoke workflow validation passed"
