#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-production-public-smoke-workflows] %s\n' "$1"
}

fail() {
  printf '[verify-production-public-smoke-workflows] ERROR: %s\n' "$1" >&2
  exit 1
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if command -v python3 >/dev/null 2>&1 && python3 -c 'import yaml' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'import yaml' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  fail "python3 or python with PyYAML is required"
fi

"$PYTHON_BIN" - "$ROOT_DIR" <<'PY'
import copy
import re
import sys
from pathlib import Path

import yaml


ROOT = Path(sys.argv[1])
CHECKOUT_REVISION = "34e114876b0b11c390a56381ad16ebd13914f8d5"
ENVIRONMENT = "production"
BASE_URL_VARIABLE = "PRODUCTION_PUBLIC_BASE_URL"

CASES = {
    "public": {
        "workflow": ".github/workflows/smoke-production-public.yml",
        "script": "scripts/verify-production-public-smoke.sh",
        "group": "production-public-smoke",
        "script_name": "verify-production-public-smoke.sh",
        "required_script": (
            "https://",
            "/api/timeline?from=0&to=1",
            "curl --fail --location --silent --show-error",
            "json.load",
        ),
        "forbidden_script": ("POST ", "PUT ", "PATCH ", "DELETE ", "--request POST"),
    },
    "security": {
        "workflow": ".github/workflows/smoke-production-security-headers.yml",
        "script": "scripts/verify-production-security-headers.sh",
        "group": "production-security-headers-smoke",
        "script_name": "verify-production-security-headers.sh",
        "required_script": (
            "https://",
            "/api/timeline?from=0&to=1",
            "strict-transport-security",
            "content-security-policy",
            "x-frame-options",
            "permissions-policy",
        ),
        "forbidden_script": ("POST ", "PUT ", "PATCH ", "DELETE ", "--request POST"),
    },
    "auth": {
        "workflow": ".github/workflows/smoke-production-auth.yml",
        "script": "scripts/verify-production-auth-smoke.sh",
        "group": "production-auth-smoke",
        "script_name": "verify-production-auth-smoke.sh",
        "required_script": (
            "https://",
            "/api/csrf",
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/logout",
            "/api/me",
            "without-csrf",
            "httponly",
            "secure",
            "samesite=lax",
            "production-auth-smoke-",
        ),
        "forbidden_script": (),
    },
}


def as_bool(value):
    return str(value).lower() == "true"


def load_case(case):
    workflow_path = ROOT / case["workflow"]
    script_path = ROOT / case["script"]
    if not workflow_path.is_file():
        raise SystemExit(f"Workflow not found: {workflow_path}")
    if not script_path.is_file():
        raise SystemExit(f"Smoke script not found: {script_path}")
    with workflow_path.open(encoding="utf-8") as source:
        workflow = yaml.load(source, Loader=yaml.BaseLoader)
    script_text = script_path.read_text(encoding="utf-8")
    return workflow, script_text


def validate(case_name, workflow, script_text):
    case = CASES[case_name]
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
    if concurrency.get("group") != case["group"]:
        errors.append(f"workflow must serialize {case['group']}")
    if as_bool(concurrency.get("cancel-in-progress")):
        errors.append("workflow must not cancel an in-progress smoke check")

    env = workflow.get("env", {})
    if env != {BASE_URL_VARIABLE: f"${{{{ vars.{BASE_URL_VARIABLE} }}}}"}:
        errors.append("workflow must read only the reviewed production public URL variable")

    raw_workflow = yaml.dump(workflow)
    if "staging" in raw_workflow.lower():
        errors.append("production smoke workflow must not reference staging")
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
    if job.get("environment") != ENVIRONMENT:
        errors.append("smoke job must use the production environment")
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
        case["script_name"],
        "INPUT_PUBLIC_BASE_URL",
        BASE_URL_VARIABLE,
        "GITHUB_STEP_SUMMARY",
    ):
        if required not in step_text:
            errors.append(f"workflow is missing required smoke behavior: {required}")

    lower_script = script_text.lower()
    for required in case["required_script"]:
        if required.lower() not in lower_script:
            errors.append(f"script is missing required smoke behavior: {required}")

    for forbidden in case["forbidden_script"]:
        if forbidden in script_text:
            errors.append("read-only smoke script must not use mutating HTTP methods")

    if "PRODUCTION_PUBLIC_BASE_URL" not in script_text:
        errors.append("script must default to PRODUCTION_PUBLIC_BASE_URL")
    if "STAGING_PUBLIC_BASE_URL" in script_text or "staging-auth-smoke-" in script_text:
        errors.append("production script must not reference staging runtime values")

    return errors


for case_name, case in CASES.items():
    workflow, script_text = load_case(case)
    errors = validate(case_name, workflow, script_text)
    if errors:
        raise SystemExit(
            "\n".join(f"- {case_name}: {error}" for error in errors)
        )

    mutated = copy.deepcopy(workflow)
    mutated["on"]["push"] = {"branches": ["main"]}
    if not validate(case_name, mutated, script_text):
        raise SystemExit(f"policy self-test failed to detect automatic trigger for {case_name}")

    mutated = copy.deepcopy(workflow)
    mutated["permissions"]["id-token"] = "write"
    if not validate(case_name, mutated, script_text):
        raise SystemExit(f"policy self-test failed to detect OIDC permission for {case_name}")

    mutated = copy.deepcopy(workflow)
    mutated["jobs"]["smoke"].pop("environment", None)
    if not validate(case_name, mutated, script_text):
        raise SystemExit(f"policy self-test failed to detect environment removal for {case_name}")

    mutated = copy.deepcopy(workflow)
    mutated["env"] = {"STAGING_PUBLIC_BASE_URL": "${{ vars.STAGING_PUBLIC_BASE_URL }}"}
    if not validate(case_name, mutated, script_text):
        raise SystemExit(f"policy self-test failed to detect staging URL variable for {case_name}")

print("production public smoke workflow policy validation passed")
PY

log "Production public smoke workflow validation passed"
