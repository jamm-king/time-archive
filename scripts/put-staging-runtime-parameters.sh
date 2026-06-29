#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[put-staging-runtime-parameters] %s\n' "$1"
}

fail() {
  printf '[put-staging-runtime-parameters] ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: scripts/put-staging-runtime-parameters.sh --expected-account-id ID [options]

Options:
  --input FILE              Local parameter input file.
                            Defaults to deploy/staging/runtime-parameters.local.json.
  --expected-account-id ID  Required 12-digit AWS account ID.
  --region REGION           AWS region. Defaults to ap-northeast-2.
  --profile PROFILE         Optional AWS CLI profile.
  --validate-only           Validate the local input file; do not contact AWS.
  --dry-run                 Validate input and account only; do not write SSM parameters.
  --help                    Show this help.

The script never prints parameter values.
EOF
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

file_uri() {
  local path="$1"

  if command -v cygpath >/dev/null 2>&1; then
    printf 'file://%s\n' "$(cygpath -m "$path")"
  else
    printf 'file://%s\n' "$path"
  fi
}

if command -v python3 >/dev/null 2>&1 && python3 -c 'import json' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'import json' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  fail "python3 or python is required"
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT_FILE="$ROOT_DIR/deploy/staging/runtime-parameters.local.json"
CONTRACT_FILE="$ROOT_DIR/deploy/staging/ssm-parameters.example.json"
AWS_REGION_VALUE="ap-northeast-2"
AWS_PROFILE_VALUE=""
EXPECTED_ACCOUNT_ID=""
DRY_RUN=false
VALIDATE_ONLY=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --input)
      [[ $# -ge 2 ]] || fail "--input requires a value"
      INPUT_FILE="$2"
      shift 2
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
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --validate-only)
      VALIDATE_ONLY=true
      shift
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

[[ "$EXPECTED_ACCOUNT_ID" =~ ^[0-9]{12}$ ]] ||
  fail "--expected-account-id is required and must be a 12-digit account ID"
[[ "$AWS_REGION_VALUE" == "ap-northeast-2" ]] ||
  fail "Staging runtime parameters must be written in ap-northeast-2"
[[ -f "$CONTRACT_FILE" ]] || fail "Contract file not found: $CONTRACT_FILE"
[[ -f "$INPUT_FILE" ]] || fail "Input file not found: $INPUT_FILE"

bash -n "$0"

VALIDATED_PARAMETERS_FILE="$(mktemp)"
PUT_INPUT_FILE="$(mktemp)"
trap 'rm -f "$VALIDATED_PARAMETERS_FILE" "$PUT_INPUT_FILE"' EXIT
chmod 600 "$VALIDATED_PARAMETERS_FILE" "$PUT_INPUT_FILE"

"$PYTHON_BIN" - "$CONTRACT_FILE" "$INPUT_FILE" "$VALIDATED_PARAMETERS_FILE" <<'PY'
import json
import sys

contract_path, input_path, output_path = sys.argv[1:4]

with open(contract_path, encoding="utf-8-sig") as source:
    contract_payload = json.load(source)
with open(input_path, encoding="utf-8-sig") as source:
    input_payload = json.load(source)

contract_parameters = contract_payload.get("Parameters")
input_parameters = input_payload.get("Parameters")
if not isinstance(contract_parameters, list):
    raise SystemExit("contract Parameters must be a list")
if not isinstance(input_parameters, list):
    raise SystemExit("input Parameters must be a list")

expected = {
    item.get("Name"): item.get("Type")
    for item in contract_parameters
}
actual = {
    item.get("Name"): item
    for item in input_parameters
}

if None in expected or None in actual:
    raise SystemExit("all parameters must have a Name")

missing = sorted(set(expected) - set(actual))
unexpected = sorted(set(actual) - set(expected))
if missing:
    raise SystemExit(f"input is missing parameters: {missing}")
if unexpected:
    raise SystemExit(f"input has unexpected parameters: {unexpected}")

validated = []
for name in sorted(expected):
    item = actual[name]
    expected_type = expected[name]
    actual_type = item.get("Type")
    value = item.get("Value")
    if actual_type != expected_type:
        raise SystemExit(f"unexpected type for {name}: expected {expected_type}, got {actual_type}")
    if actual_type not in {"String", "SecureString"}:
        raise SystemExit(f"unsupported parameter type for {name}: {actual_type}")
    if not isinstance(value, str):
        raise SystemExit(f"value must be a string for {name}")
    if "\n" in value or "\r" in value:
        raise SystemExit(f"multiline value is not allowed for {name}")
    if not value and name != "/time-archive/staging/rate-limit/client-ip-header":
        raise SystemExit(f"empty value is not allowed for {name}")
    if "replace-with-" in value:
        raise SystemExit(f"placeholder value must be replaced for {name}")
    if name == "/time-archive/staging/rate-limit/client-ip-header" and value == "":
        continue
    validated.append({"Name": name, "Type": actual_type, "Value": value})

with open(output_path, "w", encoding="utf-8") as target:
    json.dump({"Parameters": validated}, target)

print(f"validated {len(validated)} staging runtime parameters")
PY

if [[ "$VALIDATE_ONLY" == "true" ]]; then
  log "Local input validation completed without contacting AWS"
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

if [[ "$DRY_RUN" == "true" ]]; then
  "$PYTHON_BIN" - "$VALIDATED_PARAMETERS_FILE" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    parameters = json.load(source)["Parameters"]

for item in parameters:
    print(f"dry-run: {item['Name']} ({item['Type']})")
PY
  log "Dry run completed without writing SSM parameters"
  exit 0
fi

"$PYTHON_BIN" - "$VALIDATED_PARAMETERS_FILE" <<'PY' |
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    parameters = json.load(source)["Parameters"]

for index, item in enumerate(parameters):
    print(json.dumps({"Index": index, "Name": item["Name"], "Type": item["Type"]}))
PY
while IFS= read -r summary; do
  index="$("$PYTHON_BIN" -c 'import json,sys; print(json.loads(sys.stdin.read())["Index"])' <<< "$summary")"
  name="$("$PYTHON_BIN" -c 'import json,sys; print(json.loads(sys.stdin.read())["Name"])' <<< "$summary")"
  type="$("$PYTHON_BIN" -c 'import json,sys; print(json.loads(sys.stdin.read())["Type"])' <<< "$summary")"

  "$PYTHON_BIN" - "$VALIDATED_PARAMETERS_FILE" "$index" "$PUT_INPUT_FILE" <<'PY'
import json
import sys

parameters_path, index, output_path = sys.argv[1:4]
with open(parameters_path, encoding="utf-8") as source:
    item = json.load(source)["Parameters"][int(index)]

payload = {
    "Name": item["Name"],
    "Type": item["Type"],
    "Value": item["Value"],
    "Overwrite": True,
}
with open(output_path, "w", encoding="utf-8") as target:
    json.dump(payload, target)
PY

  "${AWS_CLI[@]}" ssm put-parameter --cli-input-json "$(file_uri "$PUT_INPUT_FILE")" >/dev/null
  : > "$PUT_INPUT_FILE"
  log "Wrote $name ($type)"
done

log "Staging runtime parameters written"
