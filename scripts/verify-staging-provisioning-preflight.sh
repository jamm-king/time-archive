#!/usr/bin/env bash

set -euo pipefail

log() {
  printf '[verify-staging-provisioning-preflight] %s\n' "$1"
}

fail() {
  printf '[verify-staging-provisioning-preflight] ERROR: %s\n' "$1" >&2
  exit 1
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="$ROOT_DIR/scripts/verify-staging-provisioning-inputs.sh"
FIXTURE="$ROOT_DIR/infra/cloudformation/staging.parameters.test.json"

[[ -x "$SCRIPT" ]] || fail "Preflight script is not executable: $SCRIPT"
[[ -f "$FIXTURE" ]] || fail "Test parameter fixture not found: $FIXTURE"

if command -v python3 >/dev/null 2>&1 && [[ "$(python3 -c 'import json; print("ready")' 2>/dev/null)" == "ready" ]]; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && [[ "$(python -c 'import json; print("ready")' 2>/dev/null)" == "ready" ]]; then
  PYTHON_BIN=python
else
  fail "python3 or python is required"
fi

"$SCRIPT" \
  --parameters "$FIXTURE" \
  --expected-account-id 123456789012
log "Valid fixture passed"

for forbidden in create-change-set execute-change-set delete-change-set create-stack update-stack delete-stack put-parameter; do
  if grep -Eq "aws[[:space:]]+[^#]*${forbidden}" "$SCRIPT"; then
    fail "Preflight script contains forbidden mutating AWS command: $forbidden"
  fi
done
log "Read-only AWS command policy passed"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

expect_failure() {
  local name="$1"
  local file="$2"
  local account_id="${3:-123456789012}"

  if "$SCRIPT" --parameters "$file" --expected-account-id "$account_id" >/dev/null 2>&1; then
    fail "Negative fixture unexpectedly passed: $name"
  fi
  log "Negative fixture rejected: $name"
}

python_mutation() {
  local name="$1"
  local expression="$2"
  local target="$TMP_DIR/$name.json"

  "$PYTHON_BIN" - "$FIXTURE" "$target" "$expression" <<'PY'
import json
import sys

source_path, target_path, expression = sys.argv[1:]
with open(source_path, encoding="utf-8") as source:
    parameters = json.load(source)

values = {item["ParameterKey"]: item for item in parameters}
exec(expression, {"parameters": parameters, "values": values})

with open(target_path, "w", encoding="utf-8") as target:
    json.dump(parameters, target)
PY
  [[ -f "$target" ]] || fail "Negative fixture was not created: $name"
  printf '%s\n' "$target"
}

PLACEHOLDER_FILE="$(python_mutation placeholder 'values["DockerComposeVersion"]["ParameterValue"] = "REPLACE_WITH_REVIEWED_VERSION"')"
expect_failure placeholder "$PLACEHOLDER_FILE"

CHECKSUM_FILE="$(python_mutation checksum 'values["DockerComposeSha256"]["ParameterValue"] = "abc"')"
expect_failure checksum "$CHECKSUM_FILE"

ACCOUNT_FILE="$(python_mutation account 'values["GitHubOidcProviderArn"]["ParameterValue"] = "arn:aws:iam::999999999999:oidc-provider/token.actions.githubusercontent.com"')"
expect_failure account-mismatch "$ACCOUNT_FILE"

UNEXPECTED_FILE="$(python_mutation unexpected-key 'parameters.append({"ParameterKey": "VpcId", "ParameterValue": "vpc-123"})')"
expect_failure unexpected-key "$UNEXPECTED_FILE"

SECRET_FILE="$(python_mutation secret-field 'parameters.append({"ParameterKey": "DatabasePassword", "ParameterValue": "not-a-real-secret"})')"
expect_failure secret-field "$SECRET_FILE"

FAKE_BIN="$TMP_DIR/bin"
mkdir -p "$FAKE_BIN"
cat > "$FAKE_BIN/aws" <<'AWS'
#!/usr/bin/env bash

set -euo pipefail

[[ "$*" != *$'\r'* ]] || {
  printf 'fake aws rejected a carriage return in arguments\n' >&2
  exit 2
}

[[ " $* " == *" --region ap-northeast-2 "* ]] || {
  printf 'fake aws expected ap-northeast-2: %s\n' "$*" >&2
  exit 2
}

case "$1 $2" in
  "sts get-caller-identity")
    printf '%s\r\n' "${FAKE_AWS_ACCOUNT_ID:-123456789012}"
    ;;
  "iam get-open-id-connect-provider")
    if [[ " $* " == *" --query Url "* ]]; then
      printf '%s\r\n' "${FAKE_OIDC_URL:-token.actions.githubusercontent.com}"
    elif [[ " $* " == *"contains(ClientIDList, 'sts.amazonaws.com')"* ]]; then
      printf '%s\r\n' "${FAKE_OIDC_AUDIENCE_PRESENT:-True}"
    else
      printf 'fake aws received an unexpected IAM query: %s\n' "$*" >&2
      exit 2
    fi
    ;;
  "ssm describe-parameters")
    [[ " $* " == *"/time-archive/bootstrap/staging/database/master-password"* ]] || exit 2
    printf '%s\r\n' "${FAKE_SSM_TYPE:-SecureString}"
    ;;
  "rds describe-orderable-db-instance-options")
    printf '%s\r\n' "${FAKE_RDS_OPTION_COUNT:-1}"
    ;;
  "cloudformation validate-template")
    ;;
  *)
    printf 'fake aws received an unexpected command: %s\n' "$*" >&2
    exit 2
    ;;
esac
AWS
chmod +x "$FAKE_BIN/aws"

PATH="$FAKE_BIN:$PATH" "$SCRIPT" \
  --parameters "$FIXTURE" \
  --expected-account-id 123456789012 \
  --check-aws
log "Read-only AWS preflight happy path passed with fake AWS CLI"

if PATH="$FAKE_BIN:$PATH" FAKE_AWS_ACCOUNT_ID=999999999999 "$SCRIPT" \
  --parameters "$FIXTURE" \
  --expected-account-id 123456789012 \
  --check-aws \
  >/dev/null 2>&1; then
  fail "AWS preflight accepted an authenticated account mismatch"
fi
log "AWS account mismatch was rejected"

if PATH="$FAKE_BIN:$PATH" FAKE_SSM_TYPE=String "$SCRIPT" \
  --parameters "$FIXTURE" \
  --expected-account-id 123456789012 \
  --check-aws \
  >/dev/null 2>&1; then
  fail "AWS preflight accepted a non-SecureString database password parameter"
fi
log "Non-SecureString database parameter was rejected"

if PATH="$FAKE_BIN:$PATH" FAKE_RDS_OPTION_COUNT=0 "$SCRIPT" \
  --parameters "$FIXTURE" \
  --expected-account-id 123456789012 \
  --check-aws \
  >/dev/null 2>&1; then
  fail "AWS preflight accepted an unavailable RDS engine and instance class"
fi
log "Unavailable RDS engine and instance class were rejected"

log "Staging provisioning preflight policy validation passed"
