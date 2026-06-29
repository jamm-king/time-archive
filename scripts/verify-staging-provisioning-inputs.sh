#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-provisioning-inputs] %s\n' "$1"
}

fail() {
  printf '[verify-staging-provisioning-inputs] ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  ./scripts/verify-staging-provisioning-inputs.sh \
    --parameters <path> \
    --expected-account-id <12-digit-id> \
    [--region ap-northeast-2] \
    [--check-aws]

The default mode validates only local files. --check-aws additionally calls
read-only STS, IAM, SSM, RDS, and CloudFormation APIs. This script never creates,
updates, executes, or deletes AWS resources.
EOF
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE="$ROOT_DIR/infra/cloudformation/staging.yml"
PARAMETERS=""
EXPECTED_ACCOUNT_ID=""
REGION="ap-northeast-2"
CHECK_AWS=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --parameters)
      [[ $# -ge 2 ]] || fail "--parameters requires a value"
      PARAMETERS="$2"
      shift 2
      ;;
    --expected-account-id)
      [[ $# -ge 2 ]] || fail "--expected-account-id requires a value"
      EXPECTED_ACCOUNT_ID="$2"
      shift 2
      ;;
    --region)
      [[ $# -ge 2 ]] || fail "--region requires a value"
      REGION="$2"
      shift 2
      ;;
    --check-aws)
      CHECK_AWS=true
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      usage >&2
      fail "Unknown argument: $1"
      ;;
  esac
done

[[ -n "$PARAMETERS" ]] || fail "--parameters is required"
[[ -n "$EXPECTED_ACCOUNT_ID" ]] || fail "--expected-account-id is required"
[[ "$EXPECTED_ACCOUNT_ID" =~ ^[0-9]{12}$ ]] || fail "Expected account ID must contain exactly 12 digits"
[[ "$REGION" == "ap-northeast-2" ]] || fail "Staging region must be ap-northeast-2"
[[ -f "$PARAMETERS" ]] || fail "Parameter file not found: $PARAMETERS"
[[ -f "$TEMPLATE" ]] || fail "CloudFormation template not found: $TEMPLATE"

AWS_TEMPLATE_PATH="$TEMPLATE"
if command -v cygpath >/dev/null 2>&1; then
  AWS_TEMPLATE_PATH="$(cygpath -m "$TEMPLATE")"
fi

if command -v python3 >/dev/null 2>&1 && [[ "$(python3 -c 'import json; print("ready")' 2>/dev/null)" == "ready" ]]; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && [[ "$(python -c 'import json; print("ready")' 2>/dev/null)" == "ready" ]]; then
  PYTHON_BIN=python
else
  fail "python3 or python is required"
fi

mapfile -t VALIDATED_VALUES < <("$PYTHON_BIN" - "$PARAMETERS" "$EXPECTED_ACCOUNT_ID" <<'PY'
import json
import re
import sys

path, expected_account_id = sys.argv[1:]

with open(path, encoding="utf-8") as source:
    raw = json.load(source)

if not isinstance(raw, list):
    raise SystemExit("parameter file must contain a JSON list")

expected_keys = {
    "AlertEmail",
    "DatabaseEngineVersion",
    "DockerComposeSha256",
    "DockerComposeVersion",
    "GitHubOidcProviderArn",
}
values = {}
for item in raw:
    if not isinstance(item, dict) or set(item) != {"ParameterKey", "ParameterValue"}:
        raise SystemExit("every parameter must contain only ParameterKey and ParameterValue")
    key = item["ParameterKey"]
    value = item["ParameterValue"]
    if not isinstance(key, str) or not isinstance(value, str):
        raise SystemExit("parameter keys and values must be strings")
    if key in values:
        raise SystemExit(f"duplicate parameter key: {key}")
    values[key] = value

if set(values) != expected_keys:
    missing = sorted(expected_keys - set(values))
    unexpected = sorted(set(values) - expected_keys)
    raise SystemExit(f"parameter keys differ; missing={missing}, unexpected={unexpected}")

if any("REPLACE_WITH" in value or "000000000000" in value for value in values.values()):
    raise SystemExit("parameter file contains an example placeholder")

if not re.fullmatch(r"\d+(?:\.\d+){0,2}", values["DatabaseEngineVersion"]):
    raise SystemExit("DatabaseEngineVersion has an invalid format")
if not re.fullmatch(r"v\d+\.\d+\.\d+", values["DockerComposeVersion"]):
    raise SystemExit("DockerComposeVersion must include a leading v and three numeric components")
if not re.fullmatch(r"[0-9a-f]{64}", values["DockerComposeSha256"]):
    raise SystemExit("DockerComposeSha256 must contain 64 lowercase hexadecimal characters")

oidc_pattern = re.compile(
    r"arn:(?:aws|aws-us-gov|aws-cn):iam::(\d{12}):"
    r"oidc-provider/token\.actions\.githubusercontent\.com"
)
match = oidc_pattern.fullmatch(values["GitHubOidcProviderArn"])
if not match:
    raise SystemExit("GitHubOidcProviderArn has an invalid format")
if match.group(1) != expected_account_id:
    raise SystemExit("GitHub OIDC provider account does not match the expected AWS account")

alert_email = values["AlertEmail"]
if alert_email and not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", alert_email):
    raise SystemExit("AlertEmail must be empty or a valid email address")

for key in (
    "DatabaseEngineVersion",
    "DockerComposeVersion",
    "GitHubOidcProviderArn",
):
    print(values[key])
PY
) || fail "Local staging parameter validation failed"

[[ ${#VALIDATED_VALUES[@]} -eq 3 ]] || fail "Validated parameter extraction failed"
DATABASE_ENGINE_VERSION="${VALIDATED_VALUES[0]%$'\r'}"
DOCKER_COMPOSE_VERSION="${VALIDATED_VALUES[1]%$'\r'}"
GITHUB_OIDC_PROVIDER_ARN="${VALIDATED_VALUES[2]%$'\r'}"

log "Local staging parameter validation passed"
log "Selected PostgreSQL engine version: $DATABASE_ENGINE_VERSION"
log "Selected Docker Compose version: $DOCKER_COMPOSE_VERSION"

if [[ "$CHECK_AWS" != true ]]; then
  log "AWS checks skipped; pass --check-aws after configuring AWS CLI"
  exit 0
fi

require_command aws

AUTHENTICATED_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text --region "$REGION")"
AUTHENTICATED_ACCOUNT_ID="${AUTHENTICATED_ACCOUNT_ID%$'\r'}"
[[ "$AUTHENTICATED_ACCOUNT_ID" == "$EXPECTED_ACCOUNT_ID" ]] || fail "Authenticated AWS account does not match the expected account"
log "Authenticated AWS account and region validation passed"

OIDC_URL="$(aws iam get-open-id-connect-provider \
  --open-id-connect-provider-arn "$GITHUB_OIDC_PROVIDER_ARN" \
  --query Url \
  --output text \
  --region "$REGION")"
OIDC_URL="${OIDC_URL%$'\r'}"
[[ "$OIDC_URL" == "token.actions.githubusercontent.com" ]] || fail "GitHub OIDC provider URL is invalid"

OIDC_AUDIENCE_PRESENT="$(aws iam get-open-id-connect-provider \
  --open-id-connect-provider-arn "$GITHUB_OIDC_PROVIDER_ARN" \
  --query "contains(ClientIDList, 'sts.amazonaws.com')" \
  --output text \
  --region "$REGION")"
OIDC_AUDIENCE_PRESENT="${OIDC_AUDIENCE_PRESENT%$'\r'}"
[[ "$OIDC_AUDIENCE_PRESENT" == "True" ]] || fail "GitHub OIDC provider must allow sts.amazonaws.com"
log "GitHub OIDC provider validation passed"

PASSWORD_PARAMETER_TYPE="$(aws ssm describe-parameters \
  --parameter-filters '[{"Key":"Name","Option":"Equals","Values":["/time-archive/bootstrap/staging/database/master-password"]}]' \
  --query 'Parameters[0].Type' \
  --output text \
  --region "$REGION")"
PASSWORD_PARAMETER_TYPE="${PASSWORD_PARAMETER_TYPE%$'\r'}"
[[ "$PASSWORD_PARAMETER_TYPE" == "SecureString" ]] || fail "Infrastructure-only staging database master password parameter must exist as SecureString"
log "Database password SecureString metadata validation passed"

ORDERABLE_OPTION_COUNT="$(aws rds describe-orderable-db-instance-options \
  --engine postgres \
  --engine-version "$DATABASE_ENGINE_VERSION" \
  --db-instance-class db.t4g.small \
  --query 'length(OrderableDBInstanceOptions)' \
  --output text \
  --region "$REGION")"
ORDERABLE_OPTION_COUNT="${ORDERABLE_OPTION_COUNT%$'\r'}"
[[ "$ORDERABLE_OPTION_COUNT" =~ ^[0-9]+$ ]] || fail "RDS availability query returned an invalid result"
(( ORDERABLE_OPTION_COUNT > 0 )) || fail "Selected PostgreSQL engine and db.t4g.small are not orderable in $REGION"
log "RDS engine and instance class availability validation passed"

aws cloudformation validate-template \
  --template-body "file://$AWS_TEMPLATE_PATH" \
  --region "$REGION" \
  >/dev/null
log "AWS CloudFormation template validation passed"
log "Staging provisioning preflight passed; no AWS resources were changed"
