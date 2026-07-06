# Prepare Production Provisioning Execution

## Objective

Prepare the local production CloudFormation parameter file and the prerequisite
production RDS bootstrap master-password SSM parameter so the production
provisioning read-only preflight can run before change-set creation.

## Scope

- Create the ignored local production CloudFormation parameter file.
- Set the production alert email in the local parameter file.
- Generate a production RDS bootstrap master password without printing it.
- Store the bootstrap master password as an SSM `SecureString` only after
  explicit approval.
- Run local and read-only AWS production provisioning preflight.
- Create and inspect a review-only production CloudFormation change set after
  explicit approval.
- Execute the reviewed production CloudFormation change set after explicit
  approval.
- Update the ignored production runtime parameter input with the created RDS
  endpoint.
- Write production runtime SSM parameters after explicit approval.
- Validate live production runtime SSM metadata without decrypting values.
- Bootstrap the production database application user after explicit approval.
- Verify the temporary bootstrap IAM policy is removed after the database user
  bootstrap.
- Verify production runtime rendering on the production EC2 host without
  starting containers or running migrations.

Out of scope:

- Creating production EC2, RDS, ECR, IAM, CloudWatch, or SNS resources.
- Enabling PayPal Live payments.

## Relevant Files or Modules

- `infra/cloudformation/production.parameters.local.json` (ignored, not
  committed)
- `infra/cloudformation/production.parameters.example.json`
- `scripts/verify-production-provisioning-inputs.sh`
- `scripts/bootstrap-production-db-user.sh`
- `docs/operations/production-provisioning-runbook.md`
- `docs/implementation-plan/2026-07-06/prepare-production-provisioning-execution.md`

## Key Design Decisions

- Keep the production local parameter file ignored and out of commits.
- Use `jmcylove@gmail.com` as the production alert email unless the owner
  changes it before change-set creation.
- Generate the production RDS bootstrap master password locally and never print
  it.
- Store the bootstrap master password at
  `/time-archive/bootstrap/production/database/master-password`.
- Treat SSM `put-parameter` as a high-impact external-state change requiring
  explicit approval before execution.
- Treat production database user bootstrap as a high-impact external-state
  change requiring explicit approval, and use the repository script so temporary
  master-password read access is removed in cleanup.

## Step-by-Step Execution Plan

- [x] Confirm the repository is on a dedicated branch.
- [x] Inspect the committed production CloudFormation parameter example.
- [x] Create ignored production local parameter file.
- [x] Confirm the local parameter file is ignored by Git.
- [x] Validate the local parameter file without contacting AWS.
- [x] Generate and store the production bootstrap master password in SSM after
  approval.
- [x] Run read-only AWS production provisioning preflight.
- [x] Create a review-only production CloudFormation change set after approval.
- [x] Inspect the change-set status, proposed resources, and key safety
  properties.
- [x] Execute the reviewed production CloudFormation change set after approval.
- [x] Inspect production stack outputs and key resource settings.
- [x] Update ignored production runtime input with the production RDS endpoint.
- [x] Validate the completed ignored production runtime input without
  contacting AWS.
- [x] Write production runtime SSM parameters after approval.
- [x] Validate live production runtime SSM metadata without decrypting values.
- [x] Dry-run production database user bootstrap.
- [x] Bootstrap the production application database user after approval.
- [x] Verify the temporary bootstrap IAM policy was removed.
- [x] Verify production runtime rendering on the EC2 host.

## Risks and Rollback Strategy

- Risk: Local production parameter file could be committed.
  Mitigation: confirm `git check-ignore` and verify staged changes before any
  commit.
- Risk: Bootstrap master password could leak.
  Mitigation: generate it inside the shell process, do not print it, store it
  directly in SSM, and do not write it to repository files.
- Risk: Wrong SSM parameter value is written.
  Mitigation: overwrite only
  `/time-archive/bootstrap/production/database/master-password` with a newly
  generated value before stack creation. Do not delete the whole production
  path.
- Risk: Production stack later uses a wrong parameter file.
  Mitigation: run local validation and read-only AWS preflight before change-set
  creation.
- Risk: Production stack creation could leave partially created resources.
  Mitigation: rely on CloudFormation rollback for failed creates, inspect stack
  events/resources immediately, and require separate approval for destructive
  cleanup.
- Risk: Runtime SSM values could be incomplete or inconsistent.
  Mitigation: run local validate-only, dry-run, write with name/type-only logs,
  and live metadata validation without decryption.
- Risk: Temporary master-password read access could remain attached to the
  production application role after bootstrap.
  Mitigation: use the script cleanup path and verify inline policies after the
  command completes.

## Verification Plan

- Run `git check-ignore infra/cloudformation/production.parameters.local.json`.
- Run `scripts/verify-production-provisioning-inputs.sh` without `--check-aws`.
- Run `scripts/verify-production-provisioning-inputs.sh --check-aws` after SSM
  bootstrap password metadata exists.
- Create and inspect the review-only production CloudFormation change set.
- Execute the approved production CloudFormation change set.
- Inspect stack outputs, EC2/SSM, RDS, security groups, ECR, log groups, and SNS
  subscription state.
- Run production runtime parameter validate-only after filling the RDS endpoint.
- Run production runtime SSM write and metadata validation after explicit
  approval.
- Run production database user bootstrap dry-run and then execute it after
  explicit approval.
- Verify production application role inline policies after bootstrap.
- Send a production EC2 SSM command that installs the current renderer, renders
  `/run/time-archive/runtime.env`, verifies mode `0600`, checks required
  variables by name only, and does not print secret values.
- Run `git status --short` to confirm no local secret file is tracked.

## Open Questions

- Production change-set execution remains a later approval-gated step.
- Production SNS subscription confirmation will still require owner action
  after stack execution if an alert email is configured.

## Progress

- 2026-07-06: Confirmed production parameter example already contains the
  reviewed PostgreSQL version, Docker Compose version/checksum, and GitHub OIDC
  provider ARN for account `231851555445`.
- 2026-07-06: Created ignored
  `infra/cloudformation/production.parameters.local.json` with production
  alert email `jmcylove@gmail.com`.
- 2026-07-06: Local production provisioning input validation passed without
  contacting AWS.
- 2026-07-06: Created
  `/time-archive/bootstrap/production/database/master-password` as an SSM
  `SecureString`. The first write used a PowerShell random API that was not
  available in the local runtime; the parameter was immediately overwritten
  with a value generated by `RandomNumberGenerator.Create().GetBytes(...)`.
  The final stored version is version `2`.
- 2026-07-06: Read-only AWS production provisioning preflight passed for
  account `231851555445` in `ap-northeast-2`. The preflight verified STS
  account, GitHub OIDC metadata, bootstrap password SecureString metadata, RDS
  engine orderability, and CloudFormation template validity. It created no AWS
  resources.
- 2026-07-06: Created review-only production CloudFormation change set
  `production-foundation-b7c7052aa6b7`
  (`arn:aws:cloudformation:ap-northeast-2:231851555445:changeSet/production-foundation-b7c7052aa6b7/0193a4ca-fb93-4c72-b4c7-57b1fa059563`)
  after explicit approval. The change set reached `CREATE_COMPLETE` with
  `ExecutionStatus=AVAILABLE`.
- 2026-07-06: Confirmed the production stack remains `REVIEW_IN_PROGRESS` and
  `list-stack-resources` reports `0`, so no production infrastructure has been
  created yet.
- 2026-07-06: The change set proposes 34 `Add` changes: 1 EC2 instance, 1 RDS
  instance, 2 ECR repositories, 3 IAM roles, 1 instance profile,
  VPC/subnet/route/security-group resources, 6 log groups, 5 alarms, and SNS
  topic/subscription resources.
- 2026-07-06: Reviewed key safety properties from the change set/template: RDS
  backup retention is 7 days, deletion protection is enabled, automated backups
  are retained, RDS is not publicly accessible, RDS storage is encrypted,
  database ingress is sourced from the application security group, and GitHub
  deployment trust targets the `production` environment.
- 2026-07-06: After explicit approval, executed change set
  `production-foundation-b7c7052aa6b7`. Stack creation failed and rolled back to
  `ROLLBACK_COMPLETE` because RDS rejected the bootstrap master password:
  `MasterUserPassword is not a valid password`. CloudFormation rollback deleted
  all proposed resources; `list-stack-resources` shows every created resource as
  `DELETE_COMPLETE`.
- 2026-07-06: Replaced
  `/time-archive/bootstrap/production/database/master-password` with a new
  RDS-compatible random value using printable characters excluding `/`, `@`,
  `"`, and spaces. The final stored version is version `3`.
- 2026-07-06: Deleted the empty `ROLLBACK_COMPLETE` production stack record
  after confirming all proposed resources were `DELETE_COMPLETE`.
- 2026-07-06: Re-ran read-only production provisioning preflight successfully
  after the SSM password correction.
- 2026-07-06: Created a new review-only production CloudFormation change set
  `production-foundation-b7c7052aa6b7-113454`
  (`arn:aws:cloudformation:ap-northeast-2:231851555445:changeSet/production-foundation-b7c7052aa6b7-113454/b067c936-e7e0-475c-b180-1cd8c2af1f4f`).
  The change set reached `CREATE_COMPLETE` with `ExecutionStatus=AVAILABLE`,
  the stack remains `REVIEW_IN_PROGRESS`, and `list-stack-resources` reports
  `0`.
- 2026-07-06: After explicit approval, executed change set
  `production-foundation-b7c7052aa6b7-113454`. The stack reached
  `CREATE_COMPLETE`.
- 2026-07-06: Production stack outputs include application instance
  `i-07b694fcc70b19da8`, API repository
  `231851555445.dkr.ecr.ap-northeast-2.amazonaws.com/time-archive-production-api`,
  Web repository
  `231851555445.dkr.ecr.ap-northeast-2.amazonaws.com/time-archive-production-web`,
  runtime parameter path `/time-archive/production/`, and database endpoint
  `time-archive-production-postgres.c1qg8kcesrjt.ap-northeast-2.rds.amazonaws.com`.
- 2026-07-06: Verified production RDS is `available`, `db.t4g.small`,
  PostgreSQL `18.4`, database `time_archive`, private, encrypted, deletion
  protected, and configured with 7-day backup retention.
- 2026-07-06: Verified production EC2 instance `i-07b694fcc70b19da8` is running
  as `t4g.medium`/ARM64 and is online in SSM. A terminated instance from the
  rolled-back first attempt still appears in EC2 history but is not the active
  production host.
- 2026-07-06: Verified the production application security group has no ingress
  rules and the production database security group allows TCP 5432 only from
  the application security group. AWS default egress-all remains on the
  database security group, matching the earlier staging observation.
- 2026-07-06: Verified production ECR repositories are immutable and scan on
  push. Verified production application and RDS log groups exist with 14-day
  retention.
- 2026-07-06: Production SNS email subscription for `jmcylove@gmail.com` is
  `PendingConfirmation`.
- 2026-07-06: Updated ignored
  `deploy/production/runtime-parameters.local.json` with the production RDS
  endpoint and confirmed `scripts/put-production-runtime-parameters.sh
  --validate-only` passes without contacting AWS.
- 2026-07-06: Confirmed production SNS email subscription for
  `jmcylove@gmail.com` is no longer pending.
- 2026-07-06: After explicit approval, wrote 20 production runtime SSM
  parameters under `/time-archive/production/`. Live metadata validation first
  failed because `/time-archive/production/rate-limit/client-ip-header` was
  missing.
- 2026-07-06: Set the ignored local production runtime input value
  `/time-archive/production/rate-limit/client-ip-header=CF-Connecting-IP`,
  matching the Cloudflare Tunnel ingress model. Re-ran local validate-only,
  wrote 21 production runtime SSM parameters, and live metadata validation
  passed without decrypting or printing values.
- 2026-07-06: Ran production database user bootstrap dry-run successfully for
  EC2 instance `i-07b694fcc70b19da8` and database endpoint
  `time-archive-production-postgres.c1qg8kcesrjt.ap-northeast-2.rds.amazonaws.com`.
- 2026-07-06: After explicit approval, ran production database user bootstrap.
  The script attached temporary master-password read access, sent SSM command
  `ec7d4a0e-4618-4b95-85e9-749b8de52118`, applied the application database
  role and grants, verified application role login, and removed the temporary
  bootstrap master-password read policy.
- 2026-07-06: Verified the production application role inline policies after
  bootstrap. Only persistent runtime policies remain:
  `production-ecr-pull` and `production-parameter-read`.
- 2026-07-06: Verified production runtime rendering on EC2 instance
  `i-07b694fcc70b19da8` with SSM command
  `358148f6-0d47-4af2-9d35-96f9db1deb7f`. The command installed the current
  `render-runtime-env.sh`, rendered `/run/time-archive/runtime.env`, confirmed
  mode `0600`, checked required production variables without printing values,
  and confirmed production-only values such as the RDS endpoint, R2 bucket,
  Cloudflare client IP header, and PayPal Live URLs.

## Completion Summary

The local production CloudFormation parameter file is prepared and ignored by
Git. The production RDS bootstrap master-password parameter exists in SSM as a
`SecureString`, read-only production provisioning preflight passed, and the
approved production CloudFormation change set has been executed successfully.
The production runtime local input now includes the generated RDS endpoint and
passes local validate-only. Production runtime SSM parameters are provisioned
and live metadata validation passes. The production application database user
has been bootstrapped and verified. Production runtime rendering on the EC2
host has also been verified without starting containers or running migrations.
The project is ready for the next approval-gated step: production image
publishing and deployment workflow preparation.

## Files Changed

- `docs/implementation-plan/2026-07-06/prepare-production-provisioning-execution.md`

Ignored local files updated but not committed:

- `infra/cloudformation/production.parameters.local.json`

External AWS state changed after explicit approval:

- `/time-archive/bootstrap/production/database/master-password` was written as
  an SSM `SecureString` and overwritten once to ensure the final value was
  generated with the compatible random API.
- Production runtime SSM parameters under `/time-archive/production/` were
  written.
- Production database application user and grants were created or updated.

## Tests Run and Results

- `git check-ignore infra/cloudformation/production.parameters.local.json`
  passed.
- `scripts/verify-production-provisioning-inputs.sh --parameters infra/cloudformation/production.parameters.local.json --expected-account-id 231851555445`
  passed without contacting AWS.
- `aws ssm describe-parameters` confirmed
  `/time-archive/bootstrap/production/database/master-password` exists as
  `SecureString` without decrypting its value.
- `scripts/verify-production-provisioning-inputs.sh --parameters infra/cloudformation/production.parameters.local.json --expected-account-id 231851555445 --region ap-northeast-2 --check-aws`
  passed with read-only AWS calls.
- `aws cloudformation create-change-set` created
  `production-foundation-b7c7052aa6b7`.
- `aws cloudformation wait change-set-create-complete` completed
  successfully.
- `aws cloudformation describe-change-set` confirmed
  `CREATE_COMPLETE`, `AVAILABLE`, and 34 proposed `Add` changes.
- `aws cloudformation list-stack-resources` returned `0`, confirming no
  production resources were created by the change-set review state.
- `aws cloudformation wait stack-create-complete` failed with
  `ROLLBACK_COMPLETE` after RDS password validation failed.
- `aws cloudformation list-stack-resources` after rollback showed all proposed
  resources as `DELETE_COMPLETE`.
- `aws cloudformation delete-stack` removed the empty rollback stack record.
- `scripts/verify-production-provisioning-inputs.sh --parameters infra/cloudformation/production.parameters.local.json --expected-account-id 231851555445 --region ap-northeast-2 --check-aws`
  passed again after the password correction.
- `aws cloudformation create-change-set` created
  `production-foundation-b7c7052aa6b7-113454`.
- `aws cloudformation wait change-set-create-complete` completed successfully
  for the new change set.
- `aws cloudformation execute-change-set` executed
  `production-foundation-b7c7052aa6b7-113454`.
- `aws cloudformation wait stack-create-complete` completed successfully.
- `aws cloudformation describe-stacks` confirmed `CREATE_COMPLETE` and captured
  production outputs.
- `aws rds describe-db-instances` confirmed production RDS availability,
  privacy, encryption, deletion protection, and 7-day backup retention.
- `aws ssm describe-instance-information` confirmed the production EC2 instance
  is online in SSM.
- `aws ec2 describe-security-groups` confirmed production application ingress is
  empty and database ingress is application-security-group-only on 5432.
- `aws ecr describe-repositories` confirmed production repositories are
  immutable and scan on push.
- `aws logs describe-log-groups` confirmed production application and RDS log
  group retention.
- `scripts/put-production-runtime-parameters.sh --validate-only
  --expected-account-id 231851555445` passed without contacting AWS.
- `scripts/put-production-runtime-parameters.sh --dry-run --expected-account-id 231851555445 --profile time-archive-staging-admin --region ap-northeast-2`
  passed and printed only names/types.
- `scripts/put-production-runtime-parameters.sh --expected-account-id 231851555445 --profile time-archive-staging-admin --region ap-northeast-2`
  wrote production runtime SSM parameters with name/type-only logs.
- `scripts/verify-production-runtime-parameters.sh --check-aws --expected-account-id 231851555445 --profile time-archive-staging-admin --region ap-northeast-2`
  first failed because `rate-limit/client-ip-header` was missing, then passed
  after setting `CF-Connecting-IP` and rewriting runtime parameters.
- `scripts/bootstrap-production-db-user.sh --dry-run --expected-account-id 231851555445 --profile time-archive-staging-admin --region ap-northeast-2`
  passed.
- `scripts/bootstrap-production-db-user.sh --expected-account-id 231851555445 --allow-temporary-master-password-read --profile time-archive-staging-admin --region ap-northeast-2`
  passed and printed no secrets.
- `aws iam list-role-policies --profile time-archive-staging-admin --role-name time-archive-production-ApplicationRole-yuTmdprxaftd --query "PolicyNames" --output json`
  confirmed the temporary bootstrap policy was removed.
- SSM command `358148f6-0d47-4af2-9d35-96f9db1deb7f` rendered the production
  runtime environment on EC2 and completed with `Status=Success`.

## Manual Verification Results

- Confirmed local production parameter file remains ignored and does not appear
  in `git status`.
- Confirmed change-set creation created only CloudFormation review state. The
  production stack is `REVIEW_IN_PROGRESS` with zero created resources.
- Confirmed approved change-set execution created the production foundation and
  surfaced the RDS endpoint required for runtime parameter completion.
- Confirmed ignored local runtime parameter file remains ignored after adding
  the production database URL.
- Confirmed production runtime SSM metadata contains every expected parameter
  name with the expected type.
- Confirmed production database application role login verification completed
  through the bootstrap script.
- Confirmed the production application role no longer has the temporary
  bootstrap master-password read policy.
- Confirmed production runtime rendering created `/run/time-archive/runtime.env`
  with mode `0600` and required production variables present, without printing
  secret values.

## Known Limitations

- The first production stack execution attempt rolled back completely because
  the initial bootstrap master password used a character not accepted by RDS.
  This has been corrected in SSM, and the second execution completed.
- Production containers have not been deployed or started yet.

## Follow-Up Recommendations

- Prepare production image publishing and deployment workflow execution.
