#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[bootstrap-staging-db-user] %s\n' "$1"
}

fail() {
  printf '[bootstrap-staging-db-user] ERROR: %s\n' "$1" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: scripts/bootstrap-staging-db-user.sh --expected-account-id ID [options]

Options:
  --expected-account-id ID  Required 12-digit AWS account ID.
  --region REGION           AWS region. Defaults to ap-northeast-2.
  --profile PROFILE         Optional AWS CLI profile.
  --stack-name NAME         CloudFormation stack. Defaults to time-archive-staging.
  --dry-run                 Validate inputs and render command payload; do not send SSM command.
  --allow-temporary-master-password-read
                            Temporarily allow the EC2 role to read the single
                            bootstrap master password parameter, then remove
                            that inline policy after execution.
  --help                    Show this help.

The script does not print database passwords.
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
ALLOW_TEMPORARY_MASTER_PASSWORD_READ=false
TEMPORARY_POLICY_ATTACHED=false
APPLICATION_ROLE_NAME=""
TEMPORARY_POLICY_NAME="time-archive-staging-db-user-bootstrap-master-read"

cleanup_temporary_policy() {
  if [[ "$TEMPORARY_POLICY_ATTACHED" == "true" && -n "$APPLICATION_ROLE_NAME" ]]; then
    "${AWS_CLI[@]}" iam delete-role-policy \
      --role-name "$APPLICATION_ROLE_NAME" \
      --policy-name "$TEMPORARY_POLICY_NAME" >/dev/null || true
    log "Removed temporary bootstrap master password read policy"
  fi
}

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
    --allow-temporary-master-password-read)
      ALLOW_TEMPORARY_MASTER_PASSWORD_READ=true
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
  fail "Staging database bootstrap must run in ap-northeast-2"

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
temporary_policy_json="$(mktemp)"
trap 'cleanup_temporary_policy; rm -f "$outputs_json" "$remote_script" "$send_command_json" "$temporary_policy_json"' EXIT
chmod 600 "$outputs_json" "$remote_script" "$send_command_json" "$temporary_policy_json"

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
    "ApplicationRoleArn",
    "DatabaseEndpoint",
    "DatabasePort",
    "RuntimeParameterPath",
]
missing = [key for key in required if key not in outputs]
if missing:
    raise SystemExit(f"missing stack outputs: {missing}")

print(outputs["ApplicationInstanceId"])
print(outputs["ApplicationRoleArn"])
print(outputs["DatabaseEndpoint"])
print(outputs["DatabasePort"])
print(outputs["RuntimeParameterPath"])
PY
)

INSTANCE_ID="${resolved[0]}"
APPLICATION_ROLE_ARN="${resolved[1]}"
DATABASE_ENDPOINT="${resolved[2]}"
DATABASE_PORT="${resolved[3]}"
RUNTIME_PARAMETER_PATH="${resolved[4]}"
INSTANCE_ID="${INSTANCE_ID//$'\r'/}"
APPLICATION_ROLE_ARN="${APPLICATION_ROLE_ARN//$'\r'/}"
DATABASE_ENDPOINT="${DATABASE_ENDPOINT//$'\r'/}"
DATABASE_PORT="${DATABASE_PORT//$'\r'/}"
RUNTIME_PARAMETER_PATH="${RUNTIME_PARAMETER_PATH//$'\r'/}"
APPLICATION_ROLE_NAME="${APPLICATION_ROLE_ARN##*/}"

[[ "$INSTANCE_ID" =~ ^i-[0-9a-f]+$ ]] || fail "Unexpected instance ID: $INSTANCE_ID"
[[ "$APPLICATION_ROLE_ARN" =~ ^arn:aws:iam::[0-9]{12}:role/.+ ]] ||
  fail "Unexpected application role ARN: $APPLICATION_ROLE_ARN"
[[ -n "$APPLICATION_ROLE_NAME" ]] || fail "Could not resolve application role name"
[[ "$DATABASE_PORT" == "5432" ]] || fail "Unexpected database port: $DATABASE_PORT"
[[ "$RUNTIME_PARAMETER_PATH" == "/time-archive/staging/" ]] ||
  fail "Unexpected runtime parameter path: $RUNTIME_PARAMETER_PATH"

cat > "$remote_script" <<REMOTE
#!/usr/bin/env bash
set -euo pipefail

log() {
  printf '[remote-bootstrap-staging-db-user] %s\n' "\$1"
}

fail() {
  printf '[remote-bootstrap-staging-db-user] ERROR: %s\n' "\$1" >&2
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
MASTER_USERNAME="timearchive_admin"
BOOTSTRAP_MASTER_PASSWORD_PARAMETER="/time-archive/bootstrap/staging/database/master-password"
APP_USERNAME_PARAMETER="/time-archive/staging/database/username"
APP_PASSWORD_PARAMETER="/time-archive/staging/database/password"

get_secret() {
  aws ssm get-parameter \\
    --name "\$1" \\
    --with-decryption \\
    --query "Parameter.Value" \\
    --output text \\
    --region "\$AWS_REGION"
}

MASTER_PASSWORD="\$(get_secret "\$BOOTSTRAP_MASTER_PASSWORD_PARAMETER")"
APP_USERNAME="\$(get_secret "\$APP_USERNAME_PARAMETER")"
APP_PASSWORD="\$(get_secret "\$APP_PASSWORD_PARAMETER")"

[[ "\$APP_USERNAME" == "timearchive_app" ]] || fail "Unexpected app database username"
[[ -n "\$MASTER_PASSWORD" ]] || fail "Master password is empty"
[[ -n "\$APP_PASSWORD" ]] || fail "Application password is empty"

SQL_FILE="\$(mktemp)"
trap 'rm -f "\$SQL_FILE"' EXIT
chmod 600 "\$SQL_FILE"

cat > "\$SQL_FILE" <<'SQL'
select set_config('time_archive.app_username', :'app_username', false);
select set_config('time_archive.app_password', :'app_password', false);

do \$\$
declare
  target_username text := current_setting('time_archive.app_username');
  target_password text := current_setting('time_archive.app_password');
begin
  if not exists (select 1 from pg_roles where rolname = target_username) then
    execute format('create role %I login password %L', target_username, target_password);
  else
    execute format('alter role %I login password %L', target_username, target_password);
  end if;
end
\$\$;

grant connect on database time_archive to timearchive_app;
grant usage, create on schema public to timearchive_app;
grant select, insert, update, delete on all tables in schema public to timearchive_app;
grant usage, select, update on all sequences in schema public to timearchive_app;
alter default privileges in schema public
  grant select, insert, update, delete on tables to timearchive_app;
alter default privileges in schema public
  grant usage, select, update on sequences to timearchive_app;
SQL

log "Pulling PostgreSQL client image"
docker pull postgres:18-alpine >/dev/null

log "Applying staging database role and grants"
docker run --rm --network host \\
  -e PGPASSWORD="\$MASTER_PASSWORD" \\
  -v "\$SQL_FILE:/bootstrap.sql:ro" \\
  postgres:18-alpine \\
  psql \\
    --host "\$DB_HOST" \\
    --port "\$DB_PORT" \\
    --username "\$MASTER_USERNAME" \\
    --dbname "\$DB_NAME" \\
    --set ON_ERROR_STOP=on \\
    --set "app_username=\$APP_USERNAME" \\
    --set "app_password=\$APP_PASSWORD" \\
    --file /bootstrap.sql >/dev/null

log "Verifying staging database role login"
docker run --rm --network host \\
  -e PGPASSWORD="\$APP_PASSWORD" \\
  postgres:18-alpine \\
  psql \\
    --host "\$DB_HOST" \\
    --port "\$DB_PORT" \\
    --username "\$APP_USERNAME" \\
    --dbname "\$DB_NAME" \\
    --set ON_ERROR_STOP=on \\
    --command "create table if not exists bootstrap_permission_check(id integer); drop table bootstrap_permission_check;" >/dev/null

log "Staging database user bootstrap completed"
REMOTE

commands_json="$(json_string_array "$remote_script")"
"$PYTHON_BIN" - "$INSTANCE_ID" "$commands_json" "$send_command_json" <<'PY'
import json
import sys

instance_id, commands_json, output_path = sys.argv[1:4]
payload = {
    "DocumentName": "AWS-RunShellScript",
    "InstanceIds": [instance_id],
    "Comment": "Bootstrap Time Archive staging database user",
    "Parameters": {
        "commands": json.loads(commands_json),
        "executionTimeout": ["900"],
    },
}
with open(output_path, "w", encoding="utf-8") as target:
    json.dump(payload, target)
PY

if [[ "$DRY_RUN" == "true" ]]; then
  log "Dry run completed for instance $INSTANCE_ID and database endpoint $DATABASE_ENDPOINT"
  exit 0
fi

if [[ "$ALLOW_TEMPORARY_MASTER_PASSWORD_READ" != "true" ]]; then
  fail "Refusing to execute without --allow-temporary-master-password-read"
fi

"$PYTHON_BIN" - "$EXPECTED_ACCOUNT_ID" "$temporary_policy_json" <<'PY'
import json
import sys

account_id, output_path = sys.argv[1:3]
policy = {
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "ReadSingleBootstrapMasterPasswordForDbUserBootstrap",
            "Effect": "Allow",
            "Action": "ssm:GetParameter",
            "Resource": f"arn:aws:ssm:ap-northeast-2:{account_id}:parameter/time-archive/bootstrap/staging/database/master-password",
        }
    ],
}
with open(output_path, "w", encoding="utf-8") as target:
    json.dump(policy, target)
PY

"${AWS_CLI[@]}" iam put-role-policy \
  --role-name "$APPLICATION_ROLE_NAME" \
  --policy-name "$TEMPORARY_POLICY_NAME" \
  --policy-document "$(file_uri "$temporary_policy_json")" >/dev/null
TEMPORARY_POLICY_ATTACHED=true
log "Attached temporary bootstrap master password read policy"

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

log "Staging database user bootstrap succeeded"
