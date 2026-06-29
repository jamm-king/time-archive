#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-runtime-parameters] %s\n' "$1"
}

fail() {
  printf '[verify-staging-runtime-parameters] ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: scripts/verify-staging-runtime-parameters.sh [options]

Options:
  --check-aws                 Verify live SSM parameter metadata without decryption.
  --expected-account-id ID    Required with --check-aws.
  --region REGION             AWS region. Defaults to ap-northeast-2.
  --profile PROFILE           Optional AWS CLI profile.
  --help                      Show this help.
EOF
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

CHECK_AWS=false
EXPECTED_ACCOUNT_ID=""
AWS_REGION_VALUE="ap-northeast-2"
AWS_PROFILE_VALUE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check-aws)
      CHECK_AWS=true
      shift
      ;;
    --expected-account-id)
      [[ $# -ge 2 ]] || fail "--expected-account-id requires a value"
      EXPECTED_ACCOUNT_ID="$2"
      shift 2
      ;;
    --region)
      [[ $# -ge 2 ]] || fail "--region requires a value"
      AWS_REGION_VALUE="$2"
      shift 2
      ;;
    --profile)
      [[ $# -ge 2 ]] || fail "--profile requires a value"
      AWS_PROFILE_VALUE="$2"
      shift 2
      ;;
    --help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown argument: $1"
      ;;
  esac
done

[[ "$AWS_REGION_VALUE" == "ap-northeast-2" ]] ||
  fail "Staging runtime parameters must be verified in ap-northeast-2"

if [[ "$CHECK_AWS" == "true" ]]; then
  [[ "$EXPECTED_ACCOUNT_ID" =~ ^[0-9]{12}$ ]] ||
    fail "--expected-account-id must be a 12-digit account ID when --check-aws is used"
fi

if command -v python3 >/dev/null 2>&1 && python3 -c 'import json' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'import json' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  fail "python3 or python is required"
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="$ROOT_DIR/deploy/staging/ssm-parameters.example.json"
RUNTIME_EXAMPLE="$ROOT_DIR/deploy/staging/runtime.env.example"
RENDER_SCRIPT="$ROOT_DIR/deploy/production/render-runtime-env.sh"

for file in "$FIXTURE" "$RUNTIME_EXAMPLE" "$RENDER_SCRIPT"; do
  [[ -f "$file" ]] || fail "Required file is missing: $file"
done

bash -n "$0"
bash -n "$RENDER_SCRIPT"

EXPECTED_JSON="$(mktemp)"
AWS_METADATA_JSON="$(mktemp)"
trap 'rm -f "$EXPECTED_JSON" "$AWS_METADATA_JSON"' EXIT
chmod 600 "$EXPECTED_JSON" "$AWS_METADATA_JSON"

"$PYTHON_BIN" - "$FIXTURE" "$RUNTIME_EXAMPLE" "$EXPECTED_JSON" <<'PY'
import json
import re
import sys

fixture_path, runtime_path, output_path = sys.argv[1:4]

with open(fixture_path, encoding="utf-8") as source:
    payload = json.load(source)

parameters = payload.get("Parameters")
if not isinstance(parameters, list):
    raise SystemExit("fixture Parameters must be a list")

expected = {
    "/time-archive/staging/aws/region": "String",
    "/time-archive/staging/cloudwatch/log-group-prefix": "String",
    "/time-archive/staging/database/url": "String",
    "/time-archive/staging/database/username": "SecureString",
    "/time-archive/staging/database/password": "SecureString",
    "/time-archive/staging/r2/endpoint": "String",
    "/time-archive/staging/r2/presigned-url-endpoint": "String",
    "/time-archive/staging/r2/public-base-url": "String",
    "/time-archive/staging/r2/bucket": "String",
    "/time-archive/staging/r2/access-key": "SecureString",
    "/time-archive/staging/r2/secret-key": "SecureString",
    "/time-archive/staging/rate-limit/key-salt": "SecureString",
    "/time-archive/staging/rate-limit/client-ip-header": "String",
    "/time-archive/staging/cloudflare/tunnel-token": "SecureString",
}
actual = {item.get("Name"): item for item in parameters}

if set(actual) != set(expected):
    missing = sorted(set(expected) - set(actual))
    unexpected = sorted(set(actual) - set(expected))
    raise SystemExit(f"fixture parameter mismatch; missing={missing}; unexpected={unexpected}")

for name, expected_type in expected.items():
    item = actual[name]
    if item.get("Type") != expected_type:
        raise SystemExit(f"unexpected fixture type for {name}: {item.get('Type')}")
    value = item.get("Value")
    if not isinstance(value, str):
        raise SystemExit(f"fixture value must be a string: {name}")
    if not value and name != "/time-archive/staging/rate-limit/client-ip-header":
        raise SystemExit(f"empty fixture value is not allowed: {name}")
    if "\n" in value or "\r" in value:
        raise SystemExit(f"multiline fixture value is not allowed: {name}")

runtime = {}
export_re = re.compile(r"^export ([A-Z0-9_]+)=(.*)$")
with open(runtime_path, encoding="utf-8") as source:
    for line in source:
        match = export_re.match(line.strip())
        if match:
            runtime[match.group(1)] = match.group(2)

required_runtime = {
    "TIME_ARCHIVE_ENVIRONMENT",
    "AWS_REGION",
    "TIME_ARCHIVE_CLOUDWATCH_LOG_GROUP_PREFIX",
    "TIME_ARCHIVE_DATABASE_URL",
    "TIME_ARCHIVE_DATABASE_USERNAME",
    "TIME_ARCHIVE_DATABASE_PASSWORD",
    "TIME_ARCHIVE_STORAGE_S3_ENDPOINT",
    "TIME_ARCHIVE_STORAGE_S3_PRESIGNED_URL_ENDPOINT",
    "TIME_ARCHIVE_STORAGE_S3_PUBLIC_BASE_URL",
    "TIME_ARCHIVE_STORAGE_S3_BUCKET",
    "TIME_ARCHIVE_STORAGE_S3_ACCESS_KEY",
    "TIME_ARCHIVE_STORAGE_S3_SECRET_KEY",
    "TIME_ARCHIVE_RATE_LIMIT_KEY_SALT",
    "TIME_ARCHIVE_CLOUDFLARE_TUNNEL_TOKEN",
}
missing_runtime = sorted(required_runtime - set(runtime))
if missing_runtime:
    raise SystemExit(f"runtime example is missing exports: {missing_runtime}")

if runtime.get("TIME_ARCHIVE_ENVIRONMENT") != "staging":
    raise SystemExit("runtime example must target staging")
if runtime.get("AWS_REGION") != "ap-northeast-2":
    raise SystemExit("runtime example must use ap-northeast-2")

with open(output_path, "w", encoding="utf-8") as target:
    json.dump(
        [{"Name": name, "Type": expected[name]} for name in sorted(expected)],
        target,
        indent=2,
    )

print("staging runtime parameter fixture validation passed")
PY

if [[ "$CHECK_AWS" != "true" ]]; then
  log "Local staging runtime parameter validation passed"
  exit 0
fi

require_command "${AWS_CLI_BIN:-aws}"
AWS_CLI=("${AWS_CLI_BIN:-aws}" --region "$AWS_REGION_VALUE")
if [[ -n "$AWS_PROFILE_VALUE" ]]; then
  AWS_CLI+=(--profile "$AWS_PROFILE_VALUE")
fi

account_id="$("${AWS_CLI[@]}" sts get-caller-identity --query Account --output text)"
[[ "$account_id" == "$EXPECTED_ACCOUNT_ID" ]] ||
  fail "Authenticated AWS account $account_id does not match expected $EXPECTED_ACCOUNT_ID"

"${AWS_CLI[@]}" ssm describe-parameters \
  --parameter-filters "Key=Name,Option=BeginsWith,Values=/time-archive/staging/" \
  --query "Parameters[].{Name:Name,Type:Type}" \
  --output json > "$AWS_METADATA_JSON"

"$PYTHON_BIN" - "$EXPECTED_JSON" "$AWS_METADATA_JSON" <<'PY'
import json
import sys

expected_path, actual_path = sys.argv[1:3]
with open(expected_path, encoding="utf-8") as source:
    expected = {item["Name"]: item["Type"] for item in json.load(source)}
with open(actual_path, encoding="utf-8") as source:
    actual_payload = json.load(source)

if not isinstance(actual_payload, list):
    raise SystemExit("AWS SSM metadata response must be a list")

actual = {item.get("Name"): item.get("Type") for item in actual_payload}
missing = sorted(set(expected) - set(actual))
wrong_type = sorted(
    name for name, expected_type in expected.items()
    if name in actual and actual[name] != expected_type
)
unexpected = sorted(
    name for name in actual
    if name.startswith("/time-archive/staging/") and name not in expected
)

if missing:
    raise SystemExit(f"missing SSM parameters: {missing}")
if wrong_type:
    raise SystemExit(
        "SSM parameter type mismatch: "
        + ", ".join(f"{name} expected {expected[name]} got {actual[name]}" for name in wrong_type)
    )
if unexpected:
    raise SystemExit(f"unexpected SSM parameters under staging path: {unexpected}")

print("staging SSM parameter metadata validation passed")
PY

log "AWS staging runtime parameter metadata validation passed"
