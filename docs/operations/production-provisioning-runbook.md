# Production Provisioning Runbook

## Purpose

This runbook prepares and reviews the first Time Archive production AWS
CloudFormation change set. It separates read-only preflight, change-set
creation, and change-set execution so billable production infrastructure is
never created accidentally.

The repository does not contain AWS credentials or production secret values.
Run these commands only from a trusted workstation with an approved production
operator identity.

## Safety Boundaries

The process has three distinct boundaries:

1. Local and read-only AWS preflight: does not mutate AWS.
2. Change-set creation: creates reviewable CloudFormation state but does not
   provision EC2, RDS, ECR, IAM, CloudWatch, or SNS resources.
3. Change-set execution: creates billable durable production infrastructure and
   requires explicit project-owner approval after review.

Do not replace this process with `aws cloudformation deploy`. That command can
create or update resources without preserving the intended review boundary.

## Required Operator Inputs

Collect these values before creating a local parameter file:

| Input | Requirement | Verification source |
| --- | --- | --- |
| AWS account ID | The intended 12-digit production account. | `aws sts get-caller-identity` |
| AWS region | Exactly `ap-northeast-2`. | Repository production policy |
| PostgreSQL engine version | Supported by RDS for `db.t4g.small` in the region and compatible with application migrations. | RDS orderable instance options |
| Docker Compose version | Reviewed release with a leading `v`. | Staging-proven value |
| Docker Compose SHA-256 | SHA-256 of the reviewed `docker-compose-linux-aarch64` binary. | Staging-proven value |
| GitHub OIDC provider ARN | Account-level provider for `token.actions.githubusercontent.com`, with `sts.amazonaws.com` audience. | AWS IAM |
| Alert email | Optional monitored production alert address. | Project owner |

The database master password must already exist as an SSM `SecureString` at:

```text
/time-archive/bootstrap/production/database/master-password
```

Create it through an approved secret-entry process that does not expose the
value in Git, shell history, screenshots, logs, or chat. The application
container must never receive this master credential.

## Prepare The Local Parameter File

Copy the committed example to the ignored operator path:

```bash
cp infra/cloudformation/production.parameters.example.json \
  infra/cloudformation/production.parameters.local.json
```

Replace every placeholder if any. Confirm Git does not track it:

```bash
git check-ignore infra/cloudformation/production.parameters.local.json
git status --short
```

## Validate Locally

Install the pinned validator and run repository policy checks:

```bash
python -m pip install --requirement infra/cloudformation/requirements.txt
./scripts/verify-production-cloudformation.sh
```

Validate the real parameter file without contacting AWS:

```bash
AWS_ACCOUNT_ID=replace-with-12-digit-production-account

./scripts/verify-production-provisioning-inputs.sh \
  --parameters infra/cloudformation/production.parameters.local.json \
  --expected-account-id "$AWS_ACCOUNT_ID"
```

## Run Read-Only AWS Preflight

After configuring AWS CLI for the intended production account, run:

```bash
./scripts/verify-production-provisioning-inputs.sh \
  --parameters infra/cloudformation/production.parameters.local.json \
  --expected-account-id "$AWS_ACCOUNT_ID" \
  --region ap-northeast-2 \
  --check-aws
```

The script verifies STS account, GitHub OIDC metadata, bootstrap master
password metadata, RDS orderability, and CloudFormation template validity. It
never requests SSM decryption and never creates, updates, executes, or deletes
AWS resources.

## Create A Review-Only Change Set

Creating a change set mutates CloudFormation review state but does not execute
the proposed resources. Record the operator, source commit, account, region,
and reason before running it.

Use a unique name tied to the source commit:

```bash
GIT_SHA="$(git rev-parse --short=12 HEAD)"
CHANGE_SET_NAME="production-foundation-${GIT_SHA}"

aws cloudformation create-change-set \
  --stack-name time-archive-production \
  --change-set-name "$CHANGE_SET_NAME" \
  --change-set-type CREATE \
  --description "Time Archive production foundation from ${GIT_SHA}" \
  --template-body file://infra/cloudformation/production.yml \
  --parameters file://infra/cloudformation/production.parameters.local.json \
  --capabilities CAPABILITY_NAMED_IAM \
  --tags Key=Project,Value=time-archive Key=Environment,Value=production \
  --region ap-northeast-2

aws cloudformation wait change-set-create-complete \
  --stack-name time-archive-production \
  --change-set-name "$CHANGE_SET_NAME" \
  --region ap-northeast-2
```

Save the description under the ignored `temp` directory for review:

```bash
mkdir -p temp
aws cloudformation describe-change-set \
  --stack-name time-archive-production \
  --change-set-name "$CHANGE_SET_NAME" \
  --include-property-values \
  --region ap-northeast-2 \
  > "temp/${CHANGE_SET_NAME}.json"
```

Do not commit this output. It may contain account and resource identifiers.

## Mandatory Review

Before execution approval, review and record:

- stack type is `CREATE`, status is `CREATE_COMPLETE`, and execution status is
  `AVAILABLE`;
- every proposed resource matches the documented production topology;
- IAM role trust is limited to the expected GitHub repository, main branch, or
  production environment;
- EC2 is ARM64 and has no inbound security group rules;
- RDS is private, encrypted, Single-AZ, limited to the application security
  group, deletion-protected, and configured for at least 7 days of backups;
- ECR repositories are immutable and scan on push;
- log retention, alarms, optional SNS email, estimated monthly cost, and
  snapshot behavior are acceptable.

Inspect the parameter summary without printing secret values:

```bash
aws cloudformation describe-change-set \
  --stack-name time-archive-production \
  --change-set-name "$CHANGE_SET_NAME" \
  --query '{Status:Status,ExecutionStatus:ExecutionStatus,Changes:Changes[*].ResourceChange.{Action:Action,LogicalId:LogicalResourceId,Type:ResourceType,Replacement:Replacement}}' \
  --output table \
  --region ap-northeast-2
```

## Execution Approval Boundary

Execution requires a separate explicit approval containing:

- reason: create isolated production infrastructure required for paid launch;
- impact: billable EC2, RDS, EBS, public IPv4, ECR, CloudWatch, SNS, snapshot,
  and related AWS state will be created;
- rollback: do not execute when review fails; after execution, stack deletion
  requires separate approval because RDS deletion protection and snapshots must
  be reviewed;
- alternative: continue staging-only verification and defer paid production.

Only after that approval may an operator run:

```bash
aws cloudformation execute-change-set \
  --stack-name time-archive-production \
  --change-set-name "$CHANGE_SET_NAME" \
  --region ap-northeast-2
```

## Post-Execution Handoff

After a successful stack creation:

1. Record stack ID, source Git SHA, parameters, outputs, cost decision, and
   CloudFormation result.
2. Confirm EC2 bootstrap succeeded and the instance is managed by SSM.
3. Confirm RDS is private and reachable only from the application security
   group.
4. Use the `DatabaseEndpoint` output to complete
   `deploy/production/runtime-parameters.local.json`.
5. Run production runtime parameter validate-only.
6. Bootstrap the production database user using
   [Production Database User](production-database-user.md).
7. Write production runtime SSM parameters only after explicit approval.

## Current Status

The repository-side production CloudFormation foundation, local validation, and
read-only preflight are prepared. No production change set has been created and
no production AWS resources have been provisioned.
