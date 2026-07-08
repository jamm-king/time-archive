#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[stop-staging-stack] %s\n' "$1"
}

fail() {
  printf '[stop-staging-stack] ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'USAGE'
Usage: scripts/stop-staging-stack.sh --expected-account-id ID [options]

Options:
  --expected-account-id ID  Required 12-digit AWS account ID.
  --region REGION           AWS region. Defaults to ap-northeast-2.
  --profile PROFILE         Optional AWS CLI profile.
  --stack-name NAME         CloudFormation stack. Defaults to time-archive-staging.
  --dry-run                 Validate inputs and resolved resources; do not stop resources.
  --help                    Show this help.

Stops only the staging EC2 application instance and staging RDS DB instance.
It never deletes the stack, snapshots, log groups, parameters, or production
resources.
USAGE
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

wait_for_rds_stopped() {
  local attempt status

  for attempt in {1..80}; do
    status="$("${AWS_CLI[@]}" rds describe-db-instances \
      --db-instance-identifier "$DB_INSTANCE_IDENTIFIER" \
      --query "DBInstances[0].DBInstanceStatus" \
      --output text)"

    case "$status" in
      stopped)
        log "RDS $DB_INSTANCE_IDENTIFIER is stopped"
        return 0
        ;;
      stopping)
        log "RDS $DB_INSTANCE_IDENTIFIER is stopping"
        sleep 30
        ;;
      *)
        fail "RDS $DB_INSTANCE_IDENTIFIER entered unexpected status while stopping: $status"
        ;;
    esac
  done

  fail "Timed out waiting for RDS $DB_INSTANCE_IDENTIFIER to stop"
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
DB_INSTANCE_IDENTIFIER="time-archive-staging-postgres"
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
  fail "Staging on-demand stop must run in ap-northeast-2"
[[ "$STACK_NAME" == "time-archive-staging" ]] ||
  fail "Refusing to stop non-staging stack: $STACK_NAME"
[[ "$DB_INSTANCE_IDENTIFIER" == "time-archive-staging-postgres" ]] ||
  fail "Refusing to stop unexpected DB instance: $DB_INSTANCE_IDENTIFIER"

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
trap 'rm -f "$outputs_json"' EXIT
chmod 600 "$outputs_json"

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

ec2_state="$("${AWS_CLI[@]}" ec2 describe-instances \
  --instance-ids "$INSTANCE_ID" \
  --query "Reservations[0].Instances[0].State.Name" \
  --output text)"

db_status="$("${AWS_CLI[@]}" rds describe-db-instances \
  --db-instance-identifier "$DB_INSTANCE_IDENTIFIER" \
  --query "DBInstances[0].DBInstanceStatus" \
  --output text)"

if [[ "$DRY_RUN" == "true" ]]; then
  log "Dry run completed for EC2 $INSTANCE_ID state=$ec2_state and RDS $DB_INSTANCE_IDENTIFIER status=$db_status"
  exit 0
fi

case "$ec2_state" in
  stopped)
    log "EC2 $INSTANCE_ID is already stopped"
    ;;
  stopping)
    log "EC2 $INSTANCE_ID is already stopping"
    ;;
  running)
    log "Stopping EC2 $INSTANCE_ID"
    "${AWS_CLI[@]}" ec2 stop-instances \
      --instance-ids "$INSTANCE_ID" >/dev/null
    ;;
  *)
    fail "EC2 $INSTANCE_ID is not stoppable from state: $ec2_state"
    ;;
esac

log "Waiting for EC2 $INSTANCE_ID to be stopped"
"${AWS_CLI[@]}" ec2 wait instance-stopped \
  --instance-ids "$INSTANCE_ID"

case "$db_status" in
  stopped)
    log "RDS $DB_INSTANCE_IDENTIFIER is already stopped"
    ;;
  stopping)
    log "RDS $DB_INSTANCE_IDENTIFIER is already stopping"
    ;;
  available)
    log "Stopping RDS $DB_INSTANCE_IDENTIFIER"
    "${AWS_CLI[@]}" rds stop-db-instance \
      --db-instance-identifier "$DB_INSTANCE_IDENTIFIER" >/dev/null
    ;;
  *)
    fail "RDS $DB_INSTANCE_IDENTIFIER is not stoppable from status: $db_status"
    ;;
esac

log "Waiting for RDS $DB_INSTANCE_IDENTIFIER to be stopped"
wait_for_rds_stopped

log "Staging resources are stopped. RDS can automatically restart after seven consecutive stopped days."
