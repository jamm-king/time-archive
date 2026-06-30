#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[grant-staging-owned-range] %s\n' "$1"
}

fail() {
  printf '[grant-staging-owned-range] ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: scripts/grant-staging-owned-range.sh --expected-account-id ID --email EMAIL --start-second N --end-second N [options]

Options:
  --expected-account-id ID  Required 12-digit AWS account ID.
  --email EMAIL             Existing staging user email to receive the range.
  --start-second N          Inclusive start second, 0 <= N < 86400.
  --end-second N            Exclusive end second, start < N <= 86400.
  --region REGION           AWS region. Defaults to ap-northeast-2.
  --profile PROFILE         Optional AWS CLI profile.
  --stack-name NAME         CloudFormation stack. Defaults to time-archive-staging.
  --dry-run                 Validate inputs and render command payload; do not send SSM command.
  --help                    Show this help.

The script creates staging-only ADMIN_GRANT ownership data. It does not touch
purchase or payment tables and does not print database credentials.
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

if command -v python3 >/dev/null 2>&1 && python3 -c 'import json, uuid' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'import json, uuid' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  fail "python3 or python is required"
fi

EXPECTED_ACCOUNT_ID=""
TARGET_EMAIL=""
START_SECOND=""
END_SECOND=""
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
    --email)
      [[ $# -ge 2 ]] || fail "--email requires a value"
      TARGET_EMAIL="$2"
      shift 2
      ;;
    --start-second)
      [[ $# -ge 2 ]] || fail "--start-second requires a value"
      START_SECOND="$2"
      shift 2
      ;;
    --end-second)
      [[ $# -ge 2 ]] || fail "--end-second requires a value"
      END_SECOND="$2"
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
  fail "Staging owned range grant must run in ap-northeast-2"
[[ "$TARGET_EMAIL" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]] ||
  fail "--email is required and must look like an email address"
[[ "$START_SECOND" =~ ^[0-9]+$ ]] || fail "--start-second must be a non-negative integer"
[[ "$END_SECOND" =~ ^[0-9]+$ ]] || fail "--end-second must be a positive integer"
(( START_SECOND >= 0 )) || fail "--start-second must be >= 0"
(( END_SECOND > START_SECOND )) || fail "--end-second must be greater than --start-second"
(( END_SECOND <= 86400 )) || fail "--end-second must be <= 86400"

readarray -t normalized < <("$PYTHON_BIN" - "$TARGET_EMAIL" <<'PY'
import sys
import uuid

print(sys.argv[1].strip().lower())
print(uuid.uuid4())
PY
)
TARGET_EMAIL="${normalized[0]//$'\r'/}"
GRANT_ID="${normalized[1]//$'\r'/}"

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
    "DatabaseEndpoint",
    "DatabasePort",
    "RuntimeParameterPath",
]
missing = [key for key in required if key not in outputs]
if missing:
    raise SystemExit(f"missing stack outputs: {missing}")

print(outputs["ApplicationInstanceId"])
print(outputs["DatabaseEndpoint"])
print(outputs["DatabasePort"])
print(outputs["RuntimeParameterPath"])
PY
)

INSTANCE_ID="${resolved[0]//$'\r'/}"
DATABASE_ENDPOINT="${resolved[1]//$'\r'/}"
DATABASE_PORT="${resolved[2]//$'\r'/}"
RUNTIME_PARAMETER_PATH="${resolved[3]//$'\r'/}"

[[ "$INSTANCE_ID" =~ ^i-[0-9a-f]+$ ]] || fail "Unexpected instance ID: $INSTANCE_ID"
[[ "$DATABASE_PORT" == "5432" ]] || fail "Unexpected database port: $DATABASE_PORT"
[[ "$RUNTIME_PARAMETER_PATH" == "/time-archive/staging/" ]] ||
  fail "Unexpected runtime parameter path: $RUNTIME_PARAMETER_PATH"

cat > "$remote_script" <<REMOTE
#!/usr/bin/env bash
set -euo pipefail

log() {
  printf '[remote-grant-staging-owned-range] %s\n' "\$1"
}

fail() {
  printf '[remote-grant-staging-owned-range] ERROR: %s\n' "\$1" >&2
  exit 1
}

require_command() {
  command -v "\$1" >/dev/null 2>&1 || fail "Required command not found: \$1"
}

require_command aws
require_command docker

AWS_REGION="$AWS_REGION_VALUE"
DB_HOST="$DATABASE_ENDPOINT"
DB_PORT="$DATABASE_PORT"
DB_NAME="time_archive"
APP_USERNAME_PARAMETER="/time-archive/staging/database/username"
APP_PASSWORD_PARAMETER="/time-archive/staging/database/password"
TARGET_EMAIL="$TARGET_EMAIL"
GRANT_ID="$GRANT_ID"
START_SECOND="$START_SECOND"
END_SECOND="$END_SECOND"

get_secret() {
  aws ssm get-parameter \\
    --name "\$1" \\
    --with-decryption \\
    --query "Parameter.Value" \\
    --output text \\
    --region "\$AWS_REGION"
}

APP_USERNAME="\$(get_secret "\$APP_USERNAME_PARAMETER")"
APP_PASSWORD="\$(get_secret "\$APP_PASSWORD_PARAMETER")"

[[ "\$APP_USERNAME" == "timearchive_app" ]] || fail "Unexpected app database username"
[[ -n "\$APP_PASSWORD" ]] || fail "Application password is empty"
[[ "\$TARGET_EMAIL" =~ ^[^[:space:]@]+@[^[:space:]@]+\\.[^[:space:]@]+$ ]] ||
  fail "Target email is invalid"
[[ "\$START_SECOND" =~ ^[0-9]+$ ]] || fail "Start second is invalid"
[[ "\$END_SECOND" =~ ^[0-9]+$ ]] || fail "End second is invalid"
(( START_SECOND >= 0 )) || fail "Start second must be >= 0"
(( END_SECOND > START_SECOND )) || fail "End second must be greater than start second"
(( END_SECOND <= 86400 )) || fail "End second must be <= 86400"

SQL_FILE="\$(mktemp)"
trap 'rm -f "\$SQL_FILE"' EXIT
chmod 600 "\$SQL_FILE"

cat > "\$SQL_FILE" <<'SQL'
select set_config('time_archive.target_email', :'target_email', false);
select set_config('time_archive.grant_id', :'grant_id', false);
select set_config('time_archive.start_second', :'start_second', false);
select set_config('time_archive.end_second', :'end_second', false);

do \$\$
declare
  selected_user_id uuid;
  existing_grant_id uuid;
  overlapping_count integer;
  range_start bigint := current_setting('time_archive.start_second')::bigint;
  range_end bigint := current_setting('time_archive.end_second')::bigint;
begin
  select id
    into selected_user_id
    from users
    where normalized_email = lower(current_setting('time_archive.target_email'));

  if selected_user_id is null then
    raise exception 'target user does not exist';
  end if;

  select id
    into existing_grant_id
    from ownership_records
    where owner_id = selected_user_id
      and start_second = range_start
      and end_second = range_end
      and status = 'ACTIVE'
      and valid_until is null
      and acquisition_type = 'ADMIN_GRANT';

  if existing_grant_id is not null then
    return;
  end if;

  select count(*)
    into overlapping_count
    from ownership_records
    where status = 'ACTIVE'
      and valid_until is null
      and int8range(start_second, end_second, '[)') && int8range(range_start, range_end, '[)');

  if overlapping_count > 0 then
    raise exception 'target range overlaps active ownership';
  end if;

  insert into ownership_records (
      id,
      start_second,
      end_second,
      owner_id,
      status,
      valid_from,
      valid_until,
      acquisition_type,
      source_purchase_id,
      source_transaction_id,
      created_at,
      updated_at
  )
  values (
      current_setting('time_archive.grant_id')::uuid,
      range_start,
      range_end,
      selected_user_id,
      'ACTIVE',
      now(),
      null,
      'ADMIN_GRANT',
      null,
      null,
      now(),
      now()
  );
end
\$\$;

select o.id, u.email, o.start_second, o.end_second, o.status, o.acquisition_type
  from ownership_records o
  join users u on u.id = o.owner_id
  where u.normalized_email = lower(current_setting('time_archive.target_email'))
    and o.start_second = current_setting('time_archive.start_second')::bigint
    and o.end_second = current_setting('time_archive.end_second')::bigint
    and o.status = 'ACTIVE'
    and o.valid_until is null
    and o.acquisition_type = 'ADMIN_GRANT';
SQL

log "Pulling PostgreSQL client image"
docker pull postgres:18-alpine >/dev/null

log "Granting ADMIN_GRANT ownership range to existing staging user"
docker run --rm --network host \\
  -e PGPASSWORD="\$APP_PASSWORD" \\
  -v "\$SQL_FILE:/grant-owned-range.sql:ro" \\
  postgres:18-alpine \\
  psql \\
    --host "\$DB_HOST" \\
    --port "\$DB_PORT" \\
    --username "\$APP_USERNAME" \\
    --dbname "\$DB_NAME" \\
    --set ON_ERROR_STOP=on \\
    --set "target_email=\$TARGET_EMAIL" \\
    --set "grant_id=\$GRANT_ID" \\
    --set "start_second=\$START_SECOND" \\
    --set "end_second=\$END_SECOND" \\
    --file /grant-owned-range.sql

log "Staging owned range grant completed"
REMOTE

commands_json="$(json_string_array "$remote_script")"
"$PYTHON_BIN" - "$INSTANCE_ID" "$commands_json" "$send_command_json" <<'PY'
import json
import sys

instance_id, commands_json, output_path = sys.argv[1:4]
payload = {
    "DocumentName": "AWS-RunShellScript",
    "InstanceIds": [instance_id],
    "Comment": "Grant Time Archive staging owned range",
    "Parameters": {
        "commands": json.loads(commands_json),
        "executionTimeout": ["900"],
    },
}
with open(output_path, "w", encoding="utf-8") as target:
    json.dump(payload, target)
PY

if [[ "$DRY_RUN" == "true" ]]; then
  log "Dry run completed for instance $INSTANCE_ID, target $TARGET_EMAIL, range [$START_SECOND, $END_SECOND)"
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

log "Staging owned range grant succeeded"
