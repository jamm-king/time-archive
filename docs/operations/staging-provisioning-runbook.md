# Staging Provisioning Runbook

## Purpose

This runbook prepares and reviews the first Time Archive staging AWS
CloudFormation change set. It separates read-only preflight, change-set
creation, and change-set execution so billable resources are never created as
an accidental side effect of validation.

The repository does not contain AWS credentials or staging secret values. The
commands in this document must be run by an authenticated operator from a
trusted workstation.

## Safety Boundaries

The process has three distinct boundaries:

1. Local and read-only AWS preflight: does not mutate AWS.
2. Change-set creation: creates reviewable CloudFormation state but does not
   provision the proposed EC2, RDS, ECR, IAM, CloudWatch, or SNS resources.
3. Change-set execution: creates billable external infrastructure and requires
   explicit project-owner approval after reviewing impact and rollback.

Do not replace this process with `aws cloudformation deploy`. That command can
create or update resources without preserving the intended review boundary.

## Required Operator Inputs

Collect these values before creating a local parameter file:

| Input | Requirement | Verification source |
| --- | --- | --- |
| AWS account ID | The intended 12-digit staging account. | `aws sts get-caller-identity` |
| AWS region | Exactly `ap-northeast-2`. | Repository staging policy |
| PostgreSQL engine version | Supported by RDS for `db.t4g.small` in the region and compatible with application migrations. | RDS orderable instance options |
| Docker Compose version | Reviewed stable release with a leading `v`. | Official Docker Compose release |
| Docker Compose SHA-256 | Checksum of that release's `docker-compose-linux-aarch64` binary. | Official release checksum asset |
| GitHub OIDC provider ARN | Account-level provider for `token.actions.githubusercontent.com`, with `sts.amazonaws.com` audience. | AWS IAM |
| Alert email | Optional monitored staging alert address. | Project owner |

The database master password must already exist as an SSM `SecureString` at:

```text
/time-archive/bootstrap/staging/database/master-password
```

Create it through an approved secret-entry process that does not expose the
value in the repository, shell history, screenshots, or logs. The application
container must never receive this master credential. The bootstrap path must
remain outside the EC2 role's `/time-archive/staging/*` runtime read boundary.

## Workstation Prerequisites

- AWS CLI v2.
- `bash`.
- Python 3.
- `cfn-lint` installed from
  `infra/cloudformation/requirements.txt` in an isolated environment.
- An AWS identity allowed to read IAM OIDC metadata, SSM parameter metadata,
  RDS offerings, and CloudFormation template validation.
- Separate, explicitly approved permissions for creating and executing the
  staging CloudFormation change set.

Confirm the authenticated identity before every AWS operation:

```bash
aws sts get-caller-identity --region ap-northeast-2
```

Do not proceed when the returned account differs from the intended staging
account.

## Prepare The Local Parameter File

Copy the committed example to the ignored operator path:

```bash
cp infra/cloudformation/staging.parameters.example.json \
  infra/cloudformation/staging.parameters.local.json
```

Replace every placeholder. The local file is ignored by Git. It contains
identifiers and checksums rather than secret values, but it is still treated as
operator-local configuration.

Confirm Git does not track it:

```bash
git check-ignore infra/cloudformation/staging.parameters.local.json
git status --short
```

## Validate Locally

Install the pinned validator and run repository policy checks:

```bash
python -m pip install --requirement infra/cloudformation/requirements.txt
./scripts/verify-staging-cloudformation.sh
```

Validate the real parameter file without contacting AWS:

```bash
AWS_ACCOUNT_ID=replace-with-12-digit-staging-account

./scripts/verify-staging-provisioning-inputs.sh \
  --parameters infra/cloudformation/staging.parameters.local.json \
  --expected-account-id "$AWS_ACCOUNT_ID"
```

This rejects placeholders, malformed values, unexpected parameters, and an
OIDC provider ARN from another account.

## Run Read-Only AWS Preflight

After configuring AWS CLI for the intended staging account, run:

```bash
./scripts/verify-staging-provisioning-inputs.sh \
  --parameters infra/cloudformation/staging.parameters.local.json \
  --expected-account-id "$AWS_ACCOUNT_ID" \
  --region ap-northeast-2 \
  --check-aws
```

The script verifies:

- authenticated STS account;
- fixed staging region;
- GitHub OIDC provider URL and STS audience;
- database master password parameter metadata and `SecureString` type;
- selected PostgreSQL engine availability for `db.t4g.small`;
- CloudFormation template validity in the target account and region.

It never requests SSM decryption and never creates, updates, executes, or
deletes AWS resources.

## Create A Review-Only Change Set

Creating a change set mutates CloudFormation review state but does not execute
the proposed resources. Record the operator, source commit, account, region,
and reason in the implementation or release record before running it.

Use a unique name tied to the source commit:

```bash
GIT_SHA="$(git rev-parse HEAD)"
CHANGE_SET_NAME="staging-foundation-${GIT_SHA}"

aws cloudformation create-change-set \
  --stack-name time-archive-staging \
  --change-set-name "$CHANGE_SET_NAME" \
  --change-set-type CREATE \
  --description "Time Archive staging foundation from ${GIT_SHA}" \
  --template-body file://infra/cloudformation/staging.yml \
  --parameters file://infra/cloudformation/staging.parameters.local.json \
  --capabilities CAPABILITY_NAMED_IAM \
  --tags Key=Project,Value=time-archive Key=Environment,Value=staging \
  --region ap-northeast-2

aws cloudformation wait change-set-create-complete \
  --stack-name time-archive-staging \
  --change-set-name "$CHANGE_SET_NAME" \
  --region ap-northeast-2
```

Save the description under the ignored `temp` directory for review:

```bash
mkdir -p temp
aws cloudformation describe-change-set \
  --stack-name time-archive-staging \
  --change-set-name "$CHANGE_SET_NAME" \
  --include-property-values \
  --region ap-northeast-2 \
  > "temp/${CHANGE_SET_NAME}.json"
```

Do not commit this output. It may contain account and resource identifiers.

## Mandatory Review

Before requesting execution approval, review and record:

- stack type is `CREATE`, status is `CREATE_COMPLETE`, and execution status is
  `AVAILABLE`;
- every proposed resource matches the documented staging topology;
- IAM role trust is limited to the expected GitHub repository, branch, or
  staging environment;
- the EC2 instance is ARM64 and has no inbound security group rules;
- RDS is private, encrypted, Single-AZ, and limited to the application security
  group;
- ECR repositories are immutable and scan on push;
- log retention, alarms, optional SNS email, and deletion behavior are
  acceptable;
- expected monthly staging cost and operating window are approved;
- stack deletion will leave a billable final RDS snapshot until explicitly
  reviewed and removed.

Also inspect the parameter summary without printing secret values:

```bash
aws cloudformation describe-change-set \
  --stack-name time-archive-staging \
  --change-set-name "$CHANGE_SET_NAME" \
  --query '{Status:Status,ExecutionStatus:ExecutionStatus,Changes:Changes[*].ResourceChange.{Action:Action,LogicalId:LogicalResourceId,Type:ResourceType,Replacement:Replacement}}' \
  --output table \
  --region ap-northeast-2
```

## Execution Approval Boundary

Execution requires a separate explicit approval containing:

- reason: create the isolated staging environment required for ECR publication
  and deployment verification;
- impact: billable EC2, RDS, public IPv4, EBS, ECR, CloudWatch, snapshot, and
  related AWS state will be created;
- rollback: do not execute when review fails; after execution, stop workloads
  where possible or delete the stack through a separately approved teardown,
  preserving and reviewing the final RDS snapshot;
- alternative: continue local-only verification and defer staging, at the cost
  of leaving OIDC, ECR, RDS, Tunnel, and deployment behavior unverified.

Only after that approval may an operator run:

```bash
aws cloudformation execute-change-set \
  --stack-name time-archive-staging \
  --change-set-name "$CHANGE_SET_NAME" \
  --region ap-northeast-2
```

The execution command is intentionally not wrapped by a repository script.

## Abandoning A Change Set

If review fails, do not execute it. Delete only the review change set:

```bash
aws cloudformation delete-change-set \
  --stack-name time-archive-staging \
  --change-set-name "$CHANGE_SET_NAME" \
  --region ap-northeast-2
```

Then inspect the stack state. A first `CREATE` change set can leave an empty
`REVIEW_IN_PROGRESS` stack record. Remove that empty record only after
confirming no resources were created and receiving approval for the cleanup.

## Post-Execution Handoff

After a successful stack creation:

1. Record stack ID, source Git SHA, parameters, outputs, cost decision, and
   CloudFormation event result.
2. Confirm EC2 bootstrap succeeded and the instance is managed by SSM.
3. Confirm RDS is private and reachable only from the application security
   group.
4. Configure GitHub repository variables from the CloudFormation outputs.
5. Run the staging image publication workflow from a CI-green `main` commit.
6. Inspect ECR tags, digests, scan results, provenance, and SBOM.
7. Implement and review the SSM staging deployment workflow before deploying
   the images.

## Current Status

The repository preflight and runbook are prepared. On 2026-06-24, non-root IAM
Identity Center authentication and the complete read-only AWS preflight passed
for account `231851555445` in `ap-northeast-2`. The required GitHub OIDC
provider and staging RDS master password SecureString exist. PostgreSQL `18.4`
and Docker Compose `v2.40.3` are selected in the ignored operator file.

Corrected 34-resource change set `staging-foundation-0dbb58d7-667a49a5` used
template SHA-256
`0dbb58d74e6bbc8f1ace726c31bd89b7844ab8752dfa8b5879949a1264dcee0c`
and ignored operator parameter-file SHA-256
`667a49a5e2ec862313d2a336bdfe629f0225b3a8ed9c71a8e64d5b4f2897414f`.
Review confirmed that the EC2 runtime path cannot access the infrastructure-only
RDS master password path and that the owner-provided alert destination produces
one email SNS subscription.

The change set was executed on 2026-06-24. EC2 bootstrap sent a failure signal,
and CloudFormation completed automatic rollback. RDS was never created, and
post-rollback checks found no surviving ECR repository, SNS topic, stack IAM
role, or running EC2 instance. The failed instance log was unavailable after
termination, so the template now adds persistent console logging and detailed
failure reasons before another reviewed attempt. The resource-empty rollback
stack record was deleted after the outcome was documented.

Diagnostic review-only change set `staging-foundation-d91a71c5-667a49a5` is
`CREATE_COMPLETE / AVAILABLE`. Its template SHA-256 is
`d91a71c513f90fd47bb4f61b2cf353500a20c674a5c96fdc47d6bc354428aed5`;
the ignored operator parameter-file SHA-256 remains
`667a49a5e2ec862313d2a336bdfe629f0225b3a8ed9c71a8e64d5b4f2897414f`.
The 34-resource review confirmed bootstrap diagnostics, alert subscription,
and the isolated master credential path. It has not been executed.

The diagnostic execution captured the bootstrap failure in EC2 console output:
Amazon Linux 2023's preinstalled `curl-minimal` conflicted with an explicit
request for the separate `curl` package. The corrected template removes that
package request while continuing to use the preinstalled `curl` command. The
diagnostic rollback completed without residual resources, and the empty stack
record was deleted.

Corrected review-only change set `staging-foundation-ad3b63dc-667a49a5` is
`CREATE_COMPLETE / AVAILABLE`. Its template SHA-256 is
`ad3b63dcaeab7ec4df56eacb740ecae13a5695c73d390b18627bb195b7eb1ccf`;
the ignored parameter-file SHA-256 remains
`667a49a5e2ec862313d2a336bdfe629f0225b3a8ed9c71a8e64d5b4f2897414f`.
The 34-resource review confirmed the corrected package line, bootstrap
diagnostics, alert subscription, and credential isolation. It has not been
executed at the time of that review.

The change set was subsequently executed with explicit approval, and the stack
reached `CREATE_COMPLETE`. Actual EC2, SSM, Docker, Compose checksum,
CloudWatch Agent, RDS, ECR, IAM/OIDC, log, alarm, and network checks passed.
The alert email remains pending confirmation. Database ingress is isolated as
designed, but AWS added default outbound-all to the database security group;
record and review a targeted hardening update before application deployment.
