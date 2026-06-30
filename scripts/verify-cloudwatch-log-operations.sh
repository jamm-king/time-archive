#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-cloudwatch-log-operations] %s\n' "$1"
}

fail() {
  printf '[verify-cloudwatch-log-operations] ERROR: %s\n' "$1" >&2
  exit 1
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE="$ROOT_DIR/infra/cloudformation/staging.yml"
COMPOSE_FILE="$ROOT_DIR/deploy/production/docker-compose.yml"
RUNBOOK="$ROOT_DIR/docs/operations/cloudwatch-log-operations.md"
LOGGING_POLICY="$ROOT_DIR/docs/operations/logging-policy.md"

for file in "$TEMPLATE" "$COMPOSE_FILE" "$RUNBOOK" "$LOGGING_POLICY"; do
  [[ -f "$file" ]] || fail "Required file is missing: $file"
done

bash -n "$0"
log "Shell syntax passed"

if command -v python3 >/dev/null 2>&1 && python3 -c 'import yaml' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'import yaml' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  fail "python3 or python with PyYAML is required"
fi

"$PYTHON_BIN" - "$TEMPLATE" "$COMPOSE_FILE" "$RUNBOOK" "$LOGGING_POLICY" <<'PY'
import re
import sys

import yaml


class CloudFormationLoader(yaml.SafeLoader):
    pass


TAG_NAMES = {
    "Base64": "Fn::Base64",
    "Equals": "Fn::Equals",
    "GetAtt": "Fn::GetAtt",
    "GetAZs": "Fn::GetAZs",
    "If": "Fn::If",
    "Join": "Fn::Join",
    "Not": "Fn::Not",
    "Ref": "Ref",
    "Select": "Fn::Select",
    "Sub": "Fn::Sub",
}


def construct_cloudformation_tag(loader, tag_suffix, node):
    key = TAG_NAMES.get(tag_suffix, f"Fn::{tag_suffix}")
    if isinstance(node, yaml.ScalarNode):
        value = loader.construct_scalar(node)
    elif isinstance(node, yaml.SequenceNode):
        value = loader.construct_sequence(node)
    else:
        value = loader.construct_mapping(node)
    return {key: value}


CloudFormationLoader.add_multi_constructor("!", construct_cloudformation_tag)


template_path, compose_path, runbook_path, policy_path = sys.argv[1:5]
with open(template_path, encoding="utf-8") as source:
    template = yaml.load(source, Loader=CloudFormationLoader)
with open(compose_path, encoding="utf-8") as source:
    compose = yaml.safe_load(source)
with open(runbook_path, encoding="utf-8") as source:
    runbook = source.read()
with open(policy_path, encoding="utf-8") as source:
    policy = source.read()

errors = []
resources = template.get("Resources", {})

expected_log_groups = {
    "ApplicationLogGroup": "/time-archive/${EnvironmentName}/api",
    "WebLogGroup": "/time-archive/${EnvironmentName}/web",
    "RedisLogGroup": "/time-archive/${EnvironmentName}/redis",
    "CloudflaredLogGroup": "/time-archive/${EnvironmentName}/cloudflared",
    "MigrationLogGroup": "/time-archive/${EnvironmentName}/migration",
    "DatabaseLogGroup": "/aws/rds/instance/time-archive-${EnvironmentName}-postgres/postgresql",
}

for logical_id, expected_name in expected_log_groups.items():
    resource = resources.get(logical_id)
    if not resource:
        errors.append(f"missing log group resource: {logical_id}")
        continue
    if resource.get("Type") != "AWS::Logs::LogGroup":
        errors.append(f"{logical_id} must be AWS::Logs::LogGroup")
        continue
    properties = resource.get("Properties", {})
    name = properties.get("LogGroupName")
    if name != {"Fn::Sub": expected_name}:
        errors.append(f"{logical_id} has unexpected name: {name}")
    if properties.get("RetentionInDays") != 14:
        errors.append(f"{logical_id} must use 14-day retention")
    if resource.get("DeletionPolicy") != "Delete":
        errors.append(f"{logical_id} must remain staging-cleanup friendly")
    if resource.get("UpdateReplacePolicy") != "Delete":
        errors.append(f"{logical_id} update replacement policy must be Delete")

services = compose.get("services", {})
expected_service_groups = {
    "api": "${TIME_ARCHIVE_CLOUDWATCH_LOG_GROUP_PREFIX:?Set TIME_ARCHIVE_CLOUDWATCH_LOG_GROUP_PREFIX}/api",
    "web": "${TIME_ARCHIVE_CLOUDWATCH_LOG_GROUP_PREFIX:?Set TIME_ARCHIVE_CLOUDWATCH_LOG_GROUP_PREFIX}/web",
    "redis": "${TIME_ARCHIVE_CLOUDWATCH_LOG_GROUP_PREFIX:?Set TIME_ARCHIVE_CLOUDWATCH_LOG_GROUP_PREFIX}/redis",
    "cloudflared": "${TIME_ARCHIVE_CLOUDWATCH_LOG_GROUP_PREFIX:?Set TIME_ARCHIVE_CLOUDWATCH_LOG_GROUP_PREFIX}/cloudflared",
    "migrate": "${TIME_ARCHIVE_CLOUDWATCH_LOG_GROUP_PREFIX:?Set TIME_ARCHIVE_CLOUDWATCH_LOG_GROUP_PREFIX}/migration",
}
for service_name, expected_group in expected_service_groups.items():
    service = services.get(service_name)
    if not service:
        errors.append(f"missing compose service: {service_name}")
        continue
    logging = service.get("logging", {})
    if logging.get("driver") != "awslogs":
        errors.append(f"{service_name} must use awslogs")
    options = logging.get("options", {})
    if options.get("awslogs-region") != "${AWS_REGION:?Set AWS_REGION}":
        errors.append(f"{service_name} must require AWS_REGION for awslogs")
    if options.get("awslogs-group") != expected_group:
        errors.append(f"{service_name} has unexpected awslogs group")
    if "awslogs-create-group" in options:
        errors.append(f"{service_name} must not create unmanaged log groups")

required_runbook_text = [
    "/time-archive/staging/api",
    "/time-archive/staging/web",
    "/time-archive/staging/redis",
    "/time-archive/staging/cloudflared",
    "/time-archive/staging/migration",
    "/aws/rds/instance/time-archive-staging-postgres/postgresql",
    "X-Request-Id",
    "filter-log-events",
    "retention-in-days",
    "14 days",
    "passwords",
    "presigned",
]
for text in required_runbook_text:
    if text not in runbook:
        errors.append(f"runbook is missing required text: {text}")

required_policy_text = [
    "Staging: 14 days.",
    "Production MVP: 14 days.",
    "Log groups have the approved retention period.",
    "Operators can search by request ID during an incident.",
]
for text in required_policy_text:
    if text not in policy:
        errors.append(f"logging policy is missing required text: {text}")

if re.search(r"(?<![0-9])[0-9]{12}(?![0-9])", runbook):
    errors.append("runbook must not contain a literal AWS account ID")

if errors:
    raise SystemExit("\n".join(f"- {error}" for error in errors))

print("CloudWatch log operations policy validation passed")
PY

log "CloudWatch log operations validation passed"
