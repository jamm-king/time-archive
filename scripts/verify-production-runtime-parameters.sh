#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-production-runtime-parameters] %s\n' "$1"
}

fail() {
  printf '[verify-production-runtime-parameters] ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: scripts/verify-production-runtime-parameters.sh [options]

Options:
  --check-aws                 Verify live SSM parameter metadata without decryption.
  --expected-account-id ID    Required with --check-aws.
  --region REGION             AWS region. Defaults to ap-northeast-2.
  --profile PROFILE           Optional AWS CLI profile.
  --help                      Show this help.

This script validates names and types only. It never decrypts or prints
production parameter values.
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
  fail "Production runtime parameters must be verified in ap-northeast-2"

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
FIXTURE="$ROOT_DIR/deploy/production/ssm-parameters.example.json"
RENDER_SCRIPT="$ROOT_DIR/deploy/production/render-runtime-env.sh"
RUNTIME_DOC="$ROOT_DIR/docs/operations/production-runtime-parameters.md"

for file in "$FIXTURE" "$RENDER_SCRIPT" "$RUNTIME_DOC"; do
  [[ -f "$file" ]] || fail "Required file is missing: $file"
done

bash -n "$0"
bash -n "$RENDER_SCRIPT"

EXPECTED_JSON="$(mktemp)"
AWS_METADATA_JSON="$(mktemp)"
trap 'rm -f "$EXPECTED_JSON" "$AWS_METADATA_JSON"' EXIT
chmod 600 "$EXPECTED_JSON" "$AWS_METADATA_JSON"

"$PYTHON_BIN" - "$FIXTURE" "$RUNTIME_DOC" "$EXPECTED_JSON" <<'PY'
import json
import re
import sys

fixture_path, runtime_doc_path, output_path = sys.argv[1:4]

expected = {
    "/time-archive/production/aws/region": "String",
    "/time-archive/production/cloudwatch/log-group-prefix": "String",
    "/time-archive/production/database/url": "String",
    "/time-archive/production/database/username": "SecureString",
    "/time-archive/production/database/password": "SecureString",
    "/time-archive/production/r2/endpoint": "String",
    "/time-archive/production/r2/presigned-url-endpoint": "String",
    "/time-archive/production/r2/public-base-url": "String",
    "/time-archive/production/r2/bucket": "String",
    "/time-archive/production/r2/access-key": "SecureString",
    "/time-archive/production/r2/secret-key": "SecureString",
    "/time-archive/production/rate-limit/key-salt": "SecureString",
    "/time-archive/production/rate-limit/client-ip-header": "String",
    "/time-archive/production/cloudflare/tunnel-token": "SecureString",
    "/time-archive/production/paypal/enabled": "String",
    "/time-archive/production/paypal/api-base-url": "String",
    "/time-archive/production/paypal/client-id": "SecureString",
    "/time-archive/production/paypal/client-secret": "SecureString",
    "/time-archive/production/paypal/return-url": "String",
    "/time-archive/production/paypal/cancel-url": "String",
    "/time-archive/production/paypal/webhook-id": "SecureString",
}

with open(fixture_path, encoding="utf-8") as source:
    payload = json.load(source)

parameters = payload.get("Parameters")
if not isinstance(parameters, list):
    raise SystemExit("fixture Parameters must be a list")

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
    if "\n" in value or "\r" in value:
        raise SystemExit(f"multiline fixture value is not allowed: {name}")
    if not value and name != "/time-archive/production/rate-limit/client-ip-header":
        raise SystemExit(f"empty fixture value is not allowed: {name}")
    if "/staging/" in name or "staging" in value.lower():
        raise SystemExit(f"production fixture must not reference staging: {name}")
    if expected_type == "SecureString" and not (
        value.startswith("replace-with-") or value == "false"
    ):
        raise SystemExit(f"committed SecureString fixture must be a placeholder: {name}")

fixed_values = {
    "/time-archive/production/aws/region": "ap-northeast-2",
    "/time-archive/production/cloudwatch/log-group-prefix": "/time-archive/production",
    "/time-archive/production/paypal/enabled": "false",
    "/time-archive/production/paypal/api-base-url": "https://api-m.paypal.com",
    "/time-archive/production/paypal/return-url": "https://time-archive.com/payments/paypal/return",
    "/time-archive/production/paypal/cancel-url": "https://time-archive.com/payments/paypal/cancel",
}
for name, fixed_value in fixed_values.items():
    if actual[name].get("Value") != fixed_value:
        raise SystemExit(f"unexpected fixed fixture value for {name}")

with open(runtime_doc_path, encoding="utf-8") as source:
    runtime_doc = source.read()

missing_from_doc = sorted(name for name in expected if name not in runtime_doc)
if missing_from_doc:
    raise SystemExit(f"runtime parameter doc is missing names: {missing_from_doc}")

required_renderer_relatives = {
    "aws/region",
    "cloudwatch/log-group-prefix",
    "database/url",
    "database/username",
    "database/password",
    "r2/endpoint",
    "r2/presigned-url-endpoint",
    "r2/public-base-url",
    "r2/bucket",
    "r2/access-key",
    "r2/secret-key",
    "rate-limit/key-salt",
    "cloudflare/tunnel-token",
}
renderer_path = fixture_path.replace("ssm-parameters.example.json", "render-runtime-env.sh")
with open(renderer_path, encoding="utf-8") as source:
    renderer = source.read()
for relative_name in required_renderer_relatives:
    pattern = rf"write_required_parameter\s+\S+\s+{re.escape(relative_name)}"
    if not re.search(pattern, renderer):
        raise SystemExit(f"renderer does not require expected parameter: {relative_name}")

with open(output_path, "w", encoding="utf-8") as target:
    json.dump(
        [{"Name": name, "Type": expected[name]} for name in sorted(expected)],
        target,
        indent=2,
    )

print("production runtime parameter fixture validation passed")
PY

if [[ "$CHECK_AWS" != "true" ]]; then
  log "Local production runtime parameter validation passed"
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

MSYS2_ARG_CONV_EXCL="*" "${AWS_CLI[@]}" ssm describe-parameters \
  --parameter-filters "Key=Name,Option=BeginsWith,Values=/time-archive/production/" \
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
    if name.startswith("/time-archive/production/") and name not in expected
)

if missing:
    raise SystemExit(f"missing SSM parameters: {missing}")
if wrong_type:
    raise SystemExit(
        "SSM parameter type mismatch: "
        + ", ".join(f"{name} expected {expected[name]} got {actual[name]}" for name in wrong_type)
    )
if unexpected:
    raise SystemExit(f"unexpected SSM parameters under production path: {unexpected}")

print("production SSM parameter metadata validation passed")
PY

log "AWS production runtime parameter metadata validation passed"
