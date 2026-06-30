#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[inspect-staging-release-state] %s\n' "$1"
}

fail() {
  printf '[inspect-staging-release-state] ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: scripts/inspect-staging-release-state.sh --expected-account-id ID [options]

Options:
  --expected-account-id ID  Required 12-digit AWS account ID.
  --region REGION           AWS region. Defaults to ap-northeast-2.
  --profile PROFILE         Optional AWS CLI profile.
  --stack-name NAME         CloudFormation stack. Defaults to time-archive-staging.
  --dry-run                 Validate inputs and render command payload; do not send SSM command.
  --help                    Show this help.

The script is read-only. It inspects current.env and previous.env release
metadata on the staging EC2 host and does not print application secrets.
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

json_string_array() {
  local input_file="$1"

  "$PYTHON_BIN" - "$input_file" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    lines = source.read().splitlines()

print(json.dumps(lines))
PY
}

if command -v python3 >/dev/null 2>&1 && python3 -c 'import json' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'import json' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  fail "python3 or python is required"
fi

EXPECTED_ACCOUNT_ID=""
AWS_REGION_VALUE="ap-northeast-2"
AWS_PROFILE_VALUE=""
STACK_NAME="time-archive-staging"
DRY_RUN=false

while [[ $# -gt 0 ]]; do
  case "$1" in
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
    --stack-name)
      [[ $# -ge 2 ]] || fail "--stack-name requires a value"
      STACK_NAME="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=true
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
  fail "Staging release-state inspection must run in ap-northeast-2"

require_command "${AWS_CLI_BIN:-aws}"
bash -n "$0"

AWS_CLI=("${AWS_CLI_BIN:-aws}" --region "$AWS_REGION_VALUE")
if [[ -n "$AWS_PROFILE_VALUE" ]]; then
  AWS_CLI+=(--profile "$AWS_PROFILE_VALUE")
fi

account_id="$("${AWS_CLI[@]}" sts get-caller-identity --query Account --output text)"
[[ "$account_id" == "$EXPECTED_ACCOUNT_ID" ]] ||
  fail "Authenticated AWS account $account_id does not match expected $EXPECTED_ACCOUNT_ID"

outputs_json="$(mktemp)"
remote_script="$(mktemp)"
send_command_json="$(mktemp)"
trap 'rm -f "$outputs_json" "$remote_script" "$send_command_json"' EXIT
chmod 600 "$outputs_json" "$remote_script" "$send_command_json"

"${AWS_CLI[@]}" cloudformation describe-stacks \
  --stack-name "$STACK_NAME" \
  --query "Stacks[0].Outputs" \
  --output json > "$outputs_json"

readarray -t resolved < <("$PYTHON_BIN" - "$outputs_json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    outputs = {
        item["OutputKey"]: item["OutputValue"]
        for item in json.load(source)
    }

required = [
    "ApplicationInstanceId",
    "RuntimeParameterPath",
]
missing = [key for key in required if key not in outputs]
if missing:
    raise SystemExit(f"missing stack outputs: {missing}")

print(outputs["ApplicationInstanceId"])
print(outputs["RuntimeParameterPath"])
PY
)

INSTANCE_ID="${resolved[0]//$'\r'/}"
RUNTIME_PARAMETER_PATH="${resolved[1]//$'\r'/}"

[[ "$INSTANCE_ID" =~ ^i-[0-9a-f]+$ ]] || fail "Unexpected instance ID: $INSTANCE_ID"
[[ "$RUNTIME_PARAMETER_PATH" == "/time-archive/staging/" ]] ||
  fail "Unexpected runtime parameter path: $RUNTIME_PARAMETER_PATH"

cat > "$remote_script" <<'REMOTE'
#!/usr/bin/env bash
set -euo pipefail

release_dir="/var/lib/time-archive/deployments"

print_release() {
  local name="$1"
  local path="$release_dir/${name}.env"

  printf '## %s\n' "$name"
  if [[ ! -f "$path" ]]; then
    printf 'missing=true\n'
    return
  fi

  grep -E '^export TIME_ARCHIVE_(ENVIRONMENT|API_IMAGE|WEB_IMAGE|REDIS_IMAGE|CLOUDFLARED_IMAGE|DEPLOYED_AT)=' "$path" |
    sed 's/^export //'
}

print_release current
print_release previous
REMOTE

commands_json="$(json_string_array "$remote_script")"
"$PYTHON_BIN" - "$INSTANCE_ID" "$commands_json" "$send_command_json" <<'PY'
import json
import sys

instance_id, commands_json, output_path = sys.argv[1:4]
payload = {
    "DocumentName": "AWS-RunShellScript",
    "InstanceIds": [instance_id],
    "Comment": "Inspect Time Archive staging release state",
    "Parameters": {
        "commands": json.loads(commands_json),
        "executionTimeout": ["120"],
    },
}
with open(output_path, "w", encoding="utf-8") as target:
    json.dump(payload, target)
PY

if [[ "$DRY_RUN" == "true" ]]; then
  log "Dry run completed for instance $INSTANCE_ID"
  exit 0
fi

command_id="$("${AWS_CLI[@]}" ssm send-command \
  --cli-input-json "$(file_uri "$send_command_json")" \
  --query "Command.CommandId" \
  --output text)"
log "Sent SSM command $command_id"

"${AWS_CLI[@]}" ssm wait command-executed \
  --command-id "$command_id" \
  --instance-id "$INSTANCE_ID"

invocation_json="$("${AWS_CLI[@]}" ssm get-command-invocation \
  --command-id "$command_id" \
  --instance-id "$INSTANCE_ID" \
  --output json)"

status="$("$PYTHON_BIN" -c 'import json,sys; print(json.load(sys.stdin)["Status"])' <<< "$invocation_json")"
if [[ "$status" != "Success" ]]; then
  "$PYTHON_BIN" -c 'import json,sys; payload=json.load(sys.stdin); print(payload.get("StandardErrorContent",""))' <<< "$invocation_json" >&2
  fail "SSM command failed with status $status"
fi

"$PYTHON_BIN" -c 'import json,sys; payload=json.load(sys.stdin); print(payload.get("StandardOutputContent",""))' <<< "$invocation_json" |
  sed '/^$/d'

log "Staging release-state inspection completed"
