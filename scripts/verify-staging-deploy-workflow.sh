#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-deploy-workflow] %s\n' "$1"
}

fail() {
  printf '[verify-staging-deploy-workflow] ERROR: %s\n' "$1" >&2
  exit 1
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="$ROOT_DIR/.github/workflows/deploy-staging.yml"

[[ -f "$WORKFLOW" ]] || fail "Workflow not found: $WORKFLOW"

if command -v python3 >/dev/null 2>&1 && python3 -c 'import yaml' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'import yaml' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  fail "python3 or python with PyYAML is required"
fi

"$PYTHON_BIN" - "$WORKFLOW" <<'PY'
import copy
import re
import sys

import yaml


EXPECTED_ACTIONS = {
    "actions/checkout": "34e114876b0b11c390a56381ad16ebd13914f8d5",
    "aws-actions/configure-aws-credentials": "633526dcc9c26211a4282d6d8de3d8b5cb7ad07b",
}


def as_bool(value):
    return str(value).lower() == "true"


def find_action_steps(job, action_name):
    prefix = f"{action_name}@"
    return [
        step
        for step in job.get("steps", [])
        if isinstance(step, dict) and str(step.get("uses", "")).startswith(prefix)
    ]


def validate(workflow):
    errors = []
    triggers = workflow.get("on")
    if not isinstance(triggers, dict) or set(triggers) != {"workflow_dispatch"}:
        errors.append("workflow must be manual workflow_dispatch only")

    inputs = triggers.get("workflow_dispatch", {}).get("inputs", {})
    expected_inputs = {"image_sha", "redis_image", "cloudflared_image", "public_base_url"}
    if set(inputs) != expected_inputs:
        errors.append("workflow inputs must match the reviewed deployment contract")
    for required in ("image_sha", "redis_image", "cloudflared_image"):
        if not as_bool(inputs.get(required, {}).get("required")):
            errors.append(f"{required} must be required")
    if as_bool(inputs.get("public_base_url", {}).get("required")):
        errors.append("public_base_url must remain optional")

    permissions = workflow.get("permissions", {})
    if permissions != {"contents": "read", "id-token": "write"}:
        errors.append("workflow permissions must be contents:read and id-token:write only")

    concurrency = workflow.get("concurrency", {})
    if concurrency.get("group") != "staging-deployment":
        errors.append("workflow must serialize the staging deployment group")
    if as_bool(concurrency.get("cancel-in-progress")):
        errors.append("workflow must not cancel an in-progress deployment")

    jobs = workflow.get("jobs", {})
    if set(jobs) != {"deploy"}:
        errors.append("workflow must contain only the deploy job")
        return errors

    job = jobs["deploy"]
    if job.get("environment") != "staging":
        errors.append("deploy job must use the staging GitHub environment")
    if "refs/heads/main" not in str(job.get("if", "")):
        errors.append("deploy job must be limited to refs/heads/main")
    if job.get("runs-on") != "ubuntu-latest":
        errors.append("deploy job must use ubuntu-latest")
    try:
        if int(job.get("timeout-minutes", "0")) > 30:
            errors.append("deploy job timeout must not exceed 30 minutes")
    except ValueError:
        errors.append("deploy job timeout must be numeric")

    raw_workflow = yaml.dump(workflow)
    if "secrets." in raw_workflow:
        errors.append("workflow must not use long-lived GitHub secrets")
    if re.search(r"(?<![0-9])[0-9]{12}(?![0-9])", raw_workflow):
        errors.append("workflow must not contain a literal AWS account ID")
    if ":latest" in raw_workflow:
        errors.append("workflow must not use mutable latest tags")
    for forbidden in ("--with-decryption", "get-parameter", "get-parameters-by-path"):
        if forbidden in raw_workflow:
            errors.append("workflow must not read application secrets")

    seen_actions = {}
    for step in job.get("steps", []):
        action = step.get("uses") if isinstance(step, dict) else None
        if not action:
            continue
        if "@" not in action:
            errors.append(f"action is not pinned: {action}")
            continue
        name, revision = action.rsplit("@", 1)
        seen_actions.setdefault(name, []).append(revision)
        expected_revision = EXPECTED_ACTIONS.get(name)
        if expected_revision is None:
            errors.append(f"unexpected action dependency: {name}")
        elif revision != expected_revision:
            errors.append(f"action is not pinned to the reviewed revision: {name}")

    if set(seen_actions) != set(EXPECTED_ACTIONS):
        errors.append("workflow action dependency set does not match the reviewed set")

    credentials_steps = find_action_steps(job, "aws-actions/configure-aws-credentials")
    if len(credentials_steps) != 1:
        errors.append("workflow must configure AWS credentials exactly once")
    else:
        inputs = credentials_steps[0].get("with", {})
        if inputs.get("role-to-assume") != "${{ env.AWS_STAGING_DEPLOY_ROLE_ARN }}":
            errors.append("workflow must assume only the staging deploy role")
        if inputs.get("allowed-account-ids") != "${{ env.AWS_ACCOUNT_ID }}":
            errors.append("workflow must enforce the expected AWS account ID")
        if not as_bool(inputs.get("mask-aws-account-id")):
            errors.append("workflow must mask the AWS account ID")

    workflow_env = workflow.get("env", {})
    expected_env = {
        "AWS_ACCOUNT_ID": "${{ vars.AWS_ACCOUNT_ID }}",
        "AWS_REGION": "${{ vars.AWS_REGION }}",
        "AWS_STAGING_DEPLOY_ROLE_ARN": "${{ vars.AWS_STAGING_DEPLOY_ROLE_ARN }}",
        "STAGING_INSTANCE_ID": "${{ vars.STAGING_INSTANCE_ID }}",
        "ECR_REGISTRY": "${{ vars.AWS_ACCOUNT_ID }}.dkr.ecr.${{ vars.AWS_REGION }}.amazonaws.com",
        "API_REPOSITORY_NAME": "time-archive-staging-api",
        "WEB_REPOSITORY_NAME": "time-archive-staging-web",
    }
    if workflow_env != expected_env:
        errors.append("workflow environment must use only reviewed variables and repository names")

    step_text = "\n".join(str(step.get("run", "")) for step in job.get("steps", []) if isinstance(step, dict))
    for required in (
        "describe-images",
        "ssm send-command",
        "ssm wait command-executed",
        "get-command-invocation",
        "deploy.sh staging",
        "tar -C deploy/production",
        "base64 \"$bundle\"",
    ):
        if required not in step_text:
            errors.append(f"workflow is missing required deployment behavior: {required}")
    for regex in (
        r"IMAGE_SHA.*\^\[0-9a-f\]\{40\}",
        r"REDIS_IMAGE.*@sha256:\[0-9a-f\]\{64\}",
        r"CLOUDFLARED_IMAGE.*@sha256:\[0-9a-f\]\{64\}",
    ):
        if not re.search(regex, step_text):
            errors.append(f"workflow is missing validation pattern: {regex}")

    return errors


with open(sys.argv[1], encoding="utf-8") as source:
    workflow = yaml.load(source, Loader=yaml.BaseLoader)

errors = validate(workflow)
if errors:
    raise SystemExit("\n".join(f"- {error}" for error in errors))

self_tests = []

mutated = copy.deepcopy(workflow)
mutated["on"]["push"] = {"branches": ["main"]}
self_tests.append(("automatic trigger", mutated))

mutated = copy.deepcopy(workflow)
mutated["permissions"]["id-token"] = "read"
self_tests.append(("OIDC permission weakening", mutated))

mutated = copy.deepcopy(workflow)
mutated["jobs"]["deploy"].pop("environment", None)
self_tests.append(("staging environment removal", mutated))

mutated = copy.deepcopy(workflow)
mutated["jobs"]["deploy"]["if"] = "success()"
self_tests.append(("main branch guard removal", mutated))

mutated = copy.deepcopy(workflow)
mutated["env"]["STAGING_INSTANCE_ID"] = "i-0123456789abcdef0"
self_tests.append(("literal instance id", mutated))

for name, candidate in self_tests:
    if not validate(candidate):
        raise SystemExit(f"policy self-test failed to detect {name}")

print("staging deployment workflow policy validation passed")
PY

log "Staging deployment workflow validation passed"
