#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-image-publish-workflow] %s\n' "$1"
}

fail() {
  printf '[verify-staging-image-publish-workflow] ERROR: %s\n' "$1" >&2
  exit 1
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="$ROOT_DIR/.github/workflows/publish-staging-images.yml"

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
    "aws-actions/amazon-ecr-login": "d539f0932e70871a027e9d5a9d8fc38589180a64",
    "aws-actions/configure-aws-credentials": "633526dcc9c26211a4282d6d8de3d8b5cb7ad07b",
    "docker/build-push-action": "f9f3042f7e2789586610d6e8b85c8f03e5195baf",
    "docker/setup-buildx-action": "d7f5e7f509e45cec5c76c4d5afdd7de93d0b3df5",
    "docker/setup-qemu-action": "06116385d9baf250c9f4dcb4858b16962ea869c3",
}


def as_bool(value):
    return str(value).lower() == "true"


def steps_by_id(job):
    return {
        step["id"]: step
        for step in job.get("steps", [])
        if isinstance(step, dict) and step.get("id")
    }


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

    permissions = workflow.get("permissions", {})
    if permissions != {"contents": "read", "id-token": "write"}:
        errors.append("workflow permissions must be contents:read and id-token:write only")

    concurrency = workflow.get("concurrency", {})
    if concurrency.get("group") != "staging-image-publication":
        errors.append("workflow must serialize the staging publication group")
    if as_bool(concurrency.get("cancel-in-progress")):
        errors.append("workflow must not cancel an in-progress image push")

    jobs = workflow.get("jobs", {})
    if set(jobs) != {"publish"}:
        errors.append("workflow must contain only the publish job")
        return errors

    job = jobs["publish"]
    if "refs/heads/main" not in str(job.get("if", "")):
        errors.append("publish job must be limited to refs/heads/main")
    if "environment" in job:
        errors.append("image publication must not change the main-branch OIDC subject with an environment")
    if job.get("runs-on") != "ubuntu-latest":
        errors.append("publish job must use the reviewed GitHub-hosted runner")
    try:
        if int(job.get("timeout-minutes", "0")) > 45:
            errors.append("publish job timeout must not exceed 45 minutes")
    except ValueError:
        errors.append("publish job timeout must be numeric")

    raw_workflow = yaml.dump(workflow)
    if "secrets." in raw_workflow:
        errors.append("workflow must not use long-lived GitHub secrets")
    if re.search(r"(?<![0-9])[0-9]{12}(?![0-9])", raw_workflow):
        errors.append("workflow must not contain a literal AWS account ID")
    if ":latest" in raw_workflow or "latest," in raw_workflow:
        errors.append("workflow must not publish mutable latest tags")

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
        if inputs.get("role-to-assume") != "${{ env.AWS_STAGING_IMAGE_PUBLISH_ROLE_ARN }}":
            errors.append("workflow must assume only the configured staging image role")
        if inputs.get("allowed-account-ids") != "${{ env.AWS_ACCOUNT_ID }}":
            errors.append("workflow must enforce the expected AWS account ID")
        if not as_bool(inputs.get("mask-aws-account-id")):
            errors.append("workflow must mask the AWS account ID")

    login_steps = find_action_steps(job, "aws-actions/amazon-ecr-login")
    if len(login_steps) != 1 or not as_bool(login_steps[0].get("with", {}).get("mask-password")):
        errors.append("workflow must perform one masked private ECR login")

    qemu_steps = find_action_steps(job, "docker/setup-qemu-action")
    if len(qemu_steps) != 1 or qemu_steps[0].get("with", {}).get("platforms") != "arm64":
        errors.append("workflow must configure QEMU for arm64 only")

    build_steps = find_action_steps(job, "docker/build-push-action")
    if len(build_steps) != 2:
        errors.append("workflow must build exactly API and Web images")
    expected_builds = {
        "build-api": {
            "context": "apps/api",
            "file": "apps/api/Dockerfile",
            "repository": "API_REPOSITORY_NAME",
        },
        "build-web": {
            "context": "apps/web",
            "file": "apps/web/Dockerfile",
            "repository": "WEB_REPOSITORY_NAME",
        },
    }
    indexed_steps = steps_by_id(job)
    for step_id, expected in expected_builds.items():
        step = indexed_steps.get(step_id, {})
        inputs = step.get("with", {})
        if inputs.get("context") != expected["context"] or inputs.get("file") != expected["file"]:
            errors.append(f"{step_id} must use its reviewed context and Dockerfile")
        if inputs.get("platforms") != "linux/arm64":
            errors.append(f"{step_id} must target linux/arm64 only")
        if not as_bool(inputs.get("push")) or not as_bool(inputs.get("pull")):
            errors.append(f"{step_id} must pull base images and push its result")
        expected_tag = (
            "${{ env.ECR_REGISTRY }}/${{ env."
            f"{expected['repository']}"
            " }}:${{ github.sha }}"
        )
        if inputs.get("tags") != expected_tag:
            errors.append(f"{step_id} must publish only the immutable full Git SHA tag")
        if inputs.get("provenance") != "mode=max" or not as_bool(inputs.get("sbom")):
            errors.append(f"{step_id} must publish provenance and SBOM attestations")
        if "publication-state.outputs.publish_required" not in str(step.get("if", "")):
            errors.append(f"{step_id} must respect immutable publication preflight")

    publication_state = indexed_steps.get("publication-state", {}).get("run", "")
    if "describe-images" not in publication_state or "Partial image publication" not in publication_state:
        errors.append("workflow must detect complete, absent, and partial immutable publication states")

    verify_step = indexed_steps.get("verify-images", {}).get("run", "")
    if "describe-images" not in verify_step or "does not match Buildx output" not in verify_step:
        errors.append("workflow must verify ECR digests against Buildx outputs")

    workflow_env = workflow.get("env", {})
    expected_env = {
        "AWS_ACCOUNT_ID": "${{ vars.AWS_ACCOUNT_ID }}",
        "AWS_REGION": "${{ vars.AWS_REGION }}",
        "AWS_STAGING_IMAGE_PUBLISH_ROLE_ARN": "${{ vars.AWS_STAGING_IMAGE_PUBLISH_ROLE_ARN }}",
        "ECR_REGISTRY": "${{ vars.AWS_ACCOUNT_ID }}.dkr.ecr.${{ vars.AWS_REGION }}.amazonaws.com",
        "API_REPOSITORY_NAME": "time-archive-staging-api",
        "WEB_REPOSITORY_NAME": "time-archive-staging-web",
    }
    if workflow_env != expected_env:
        errors.append("workflow environment must use only reviewed variables and repository names")

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
steps_by_id(mutated["jobs"]["publish"])["build-api"]["with"]["platforms"] = "linux/amd64"
self_tests.append(("non-ARM64 build", mutated))

mutated = copy.deepcopy(workflow)
steps_by_id(mutated["jobs"]["publish"])["build-web"]["with"]["tags"] = "example:latest"
self_tests.append(("mutable tag", mutated))

mutated = copy.deepcopy(workflow)
mutated["jobs"]["publish"]["if"] = "success()"
self_tests.append(("main branch guard removal", mutated))

for name, candidate in self_tests:
    if not validate(candidate):
        raise SystemExit(f"policy self-test failed to detect {name}")

print("staging image publication workflow policy validation passed")
PY

log "Staging image publication workflow validation passed"
