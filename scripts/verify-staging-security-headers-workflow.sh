#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-security-headers-workflow] %s\n' "$1"
}

fail() {
  printf '[verify-staging-security-headers-workflow] ERROR: %s\n' "$1" >&2
  exit 1
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="$ROOT_DIR/.github/workflows/smoke-staging-security-headers.yml"
SCRIPT="$ROOT_DIR/scripts/verify-staging-security-headers.sh"
WEB_CONFIG="$ROOT_DIR/apps/web/next.config.ts"

[[ -f "$WORKFLOW" ]] || fail "Workflow not found: $WORKFLOW"
[[ -f "$SCRIPT" ]] || fail "Smoke script not found: $SCRIPT"
[[ -f "$WEB_CONFIG" ]] || fail "Web config not found: $WEB_CONFIG"

if command -v python3 >/dev/null 2>&1 && python3 -c 'import yaml' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'import yaml' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  fail "python3 or python with PyYAML is required"
fi

"$PYTHON_BIN" - "$WORKFLOW" "$SCRIPT" "$WEB_CONFIG" <<'PY'
import copy
import re
import sys

import yaml


CHECKOUT_REVISION = "34e114876b0b11c390a56381ad16ebd13914f8d5"


def as_bool(value):
    return str(value).lower() == "true"


def validate(workflow, script_text, web_config_text):
    errors = []

    triggers = workflow.get("on")
    if not isinstance(triggers, dict) or set(triggers) != {"workflow_dispatch"}:
        errors.append("workflow must be manual workflow_dispatch only")

    inputs = triggers.get("workflow_dispatch", {}).get("inputs", {})
    if set(inputs) != {"public_base_url"}:
        errors.append("workflow inputs must contain only public_base_url")
    if as_bool(inputs.get("public_base_url", {}).get("required")):
        errors.append("public_base_url must remain optional")

    if workflow.get("permissions") != {"contents": "read"}:
        errors.append("workflow permissions must be contents: read only")

    concurrency = workflow.get("concurrency", {})
    if concurrency.get("group") != "staging-security-headers-smoke":
        errors.append("workflow must serialize the staging security headers smoke group")
    if as_bool(concurrency.get("cancel-in-progress")):
        errors.append("workflow must not cancel an in-progress smoke check")

    env = workflow.get("env", {})
    if env != {"STAGING_PUBLIC_BASE_URL": "${{ vars.STAGING_PUBLIC_BASE_URL }}"}:
        errors.append("workflow must read only the reviewed staging public URL variable")

    raw_workflow = yaml.dump(workflow)
    if "secrets." in raw_workflow:
        errors.append("workflow must not read GitHub secrets")
    if "id-token" in raw_workflow:
        errors.append("workflow must not request OIDC permissions")
    if re.search(r"(?<![0-9])[0-9]{12}(?![0-9])", raw_workflow):
        errors.append("workflow must not contain a literal AWS account ID")

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
        if int(job.get("timeout-minutes", "0")) > 5:
            errors.append("smoke job timeout must not exceed 5 minutes")
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
        "verify-staging-security-headers.sh",
        "INPUT_PUBLIC_BASE_URL",
        "STAGING_PUBLIC_BASE_URL",
        "GITHUB_STEP_SUMMARY",
    ):
        if required not in step_text:
            errors.append(f"workflow is missing required smoke behavior: {required}")

    for required in (
        "https://",
        "strict-transport-security",
        "x-content-type-options",
        "x-frame-options",
        "referrer-policy",
        "content-security-policy",
        "permissions-policy",
        "/api/timeline?from=0&to=1",
    ):
        if required not in script_text.lower():
            errors.append(f"script is missing required security header behavior: {required}")

    for forbidden in ("POST ", "PUT ", "PATCH ", "DELETE ", "--request POST", "STAGING_ADMIN_PASSWORD"):
        if forbidden in script_text:
            errors.append("script must not use mutations or privileged secrets")

    for required in (
        "Strict-Transport-Security",
        "X-Content-Type-Options",
        "X-Frame-Options",
        "Referrer-Policy",
        "Content-Security-Policy",
        "Permissions-Policy",
    ):
        if required not in web_config_text:
            errors.append(f"web config is missing {required}")

    return errors


with open(sys.argv[1], encoding="utf-8") as source:
    workflow = yaml.load(source, Loader=yaml.BaseLoader)
with open(sys.argv[2], encoding="utf-8") as source:
    script_text = source.read()
with open(sys.argv[3], encoding="utf-8") as source:
    web_config_text = source.read()

errors = validate(workflow, script_text, web_config_text)
if errors:
    raise SystemExit("\n".join(f"- {error}" for error in errors))

mutated = copy.deepcopy(workflow)
mutated["on"]["push"] = {"branches": ["main"]}
if not validate(mutated, script_text, web_config_text):
    raise SystemExit("policy self-test failed to detect automatic trigger")

mutated = copy.deepcopy(workflow)
mutated["permissions"]["id-token"] = "write"
if not validate(mutated, script_text, web_config_text):
    raise SystemExit("policy self-test failed to detect OIDC permission")

mutated = copy.deepcopy(workflow)
mutated["jobs"]["smoke"].pop("environment", None)
if not validate(mutated, script_text, web_config_text):
    raise SystemExit("policy self-test failed to detect staging environment removal")

mutated_script = script_text.replace("strict-transport-security", "missing-header")
if not validate(workflow, mutated_script, web_config_text):
    raise SystemExit("policy self-test failed to detect missing HSTS check")

print("staging security headers smoke workflow policy validation passed")
PY

log "Staging security headers smoke workflow validation passed"
