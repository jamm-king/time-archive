# Add Staging Provisioning Preflight

## Objective

Prepare a safe, reproducible preflight and change-set review process for the
first Time Archive staging CloudFormation stack without creating or executing
AWS resources in this task.

## Scope

- Add a shell preflight for operator-owned staging parameter files.
- Validate local parameter structure, placeholders, account consistency, and
  required value formats before AWS is contacted.
- Optionally verify the authenticated AWS account, region, GitHub OIDC
  provider, RDS engine/class availability, and database password SecureString.
- Document exact change-set creation, review, execution approval, and cleanup
  boundaries.
- Add static and fixture-based verification to CI.

Out of scope:

- Installing or configuring AWS CLI credentials.
- Creating the account-level GitHub OIDC provider or SSM parameters.
- Creating, executing, or deleting a CloudFormation change set.
- Provisioning EC2, RDS, ECR, IAM, CloudWatch, SNS, Cloudflare, or R2 resources.
- Adding the SSM staging deployment workflow.

Scope extension approved during execution:

- After the repository preflight was complete, the project owner explicitly
  approved creating the account-level GitHub OIDC provider and the staging RDS
  master password SSM SecureString required to run the real AWS preflight.
- The approval did not include creating or executing a CloudFormation change
  set.

## Relevant Files Or Modules

- `scripts/verify-staging-provisioning-inputs.sh`
- `scripts/verify-staging-provisioning-preflight.sh`
- `infra/cloudformation/staging.parameters.example.json`
- `infra/cloudformation/staging.parameters.test.json`
- `.github/workflows/ci.yml`
- `.gitignore`
- `docs/operations/staging-provisioning-runbook.md`
- `docs/operations/staging-cloudformation-foundation.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- The repository script is preflight-only. It does not call
  `create-change-set`, `execute-change-set`, or any other mutating AWS API.
- Local validation runs before AWS CLI checks so malformed or placeholder
  inputs fail without contacting AWS.
- AWS verification uses read-only APIs and checks the expected account and
  fixed `ap-northeast-2` region.
- The RDS master password value is never read. Preflight checks only parameter
  metadata and requires `SecureString`.
- A real operator parameter file is ignored by Git. A committed synthetic
  fixture provides deterministic CI coverage.
- Change-set creation and execution remain separate commands. Execution is a
  high-impact approval boundary because it creates billable infrastructure.

## Step-By-Step Execution Plan

1. [Completed] Inspect the staging template, example parameters, current
   CloudFormation validation, deployment documentation, and local toolchain.
2. [Completed] Add the implementation plan and define the preflight contract.
3. [Completed] Add local and optional read-only AWS input verification.
4. [Completed] Add fixture-based policy tests and CI coverage.
5. [Completed] Add the provisioning and change-set review runbook.
6. [Completed] Update staging and release-readiness documentation.
7. [Completed] Run shell, fixture, CloudFormation, documentation, and Git checks.

## Risks And Rollback Strategy

- A wrong AWS account or region could create resources in the wrong boundary.
  Preflight compares the authenticated account, OIDC ARN account, expected
  account argument, and fixed region before the runbook permits a change set.
- Parameter files may leak environment identifiers or credentials. Real
  parameter files are ignored, and the CloudFormation input contract contains
  no secret value fields.
- Reading a SecureString could expose the database password. The preflight uses
  metadata-only APIs and never requests decryption or prints parameter values.
- RDS engine versions and instance offerings vary by region. Read-only
  preflight checks the selected engine/class pair in the target region.
- Repository rollback is a Git revert. No AWS rollback is required because
  this task does not mutate AWS.

## Verification Plan

- Run the preflight against a committed synthetic fixture without AWS calls.
- Run negative fixture tests for placeholders, malformed checksums, account
  mismatch, unexpected keys, and accidental secret fields.
- Run `bash -n` for all shell scripts.
- Run `./scripts/verify-staging-cloudformation.sh`.
- Parse all GitHub Actions YAML files.
- Run local Markdown link validation and `git diff --check`.
- Confirm no account credentials, secret values, or real operator parameter
  files are committed.

## Open Questions

- Whether to create the first CloudFormation change set remains a separate
  approval decision after preflight completion.
- AWS CLI v2 was installed after the repository implementation. Browser login
  and STS identity verification succeeded, but the resulting profile uses the
  AWS account root principal and must not be used for provisioning.

## Progress

- Confirmed `main` includes PR #64 and the immutable ARM64 ECR publication
  workflow.
- Confirmed the local environment has Python but does not have AWS CLI or `jq`.
- Confirmed the existing staging parameter example contains only non-secret
  inputs and the database password is referenced by parameter name.
- Added deterministic local validation for the exact staging parameter
  contract, placeholders, value formats, and OIDC account consistency.
- Added optional read-only AWS verification for STS, IAM OIDC metadata, SSM
  parameter metadata, RDS offerings, and CloudFormation template validation.
- Added a fake AWS CLI test harness covering the read-only happy path, account
  mismatch, non-SecureString database parameter, and unavailable RDS option.
- Documented change-set creation, mandatory review, execution approval,
  abandonment, and post-execution handoff without wrapping mutating commands.
- Confirmed AWS CLI v2 `2.35.11` is installed and recorded the staging account
  ID through a read-only STS call.
- Blocked further AWS inspection and provisioning because the browser login
  profile resolves to the account root principal rather than a dedicated
  operator identity.
- Logged out the root-backed CLI profile immediately after identity
  verification; its short-lived access token expires within 15 minutes.
- Verified the replacement IAM Identity Center profile resolves to the
  non-root `AWSReservedSSO_AdministratorAccess` role in account `231851555445`.
- Read-only AWS discovery confirmed that the account has no GitHub OIDC
  provider, no staging database master password parameter, and no existing
  `time-archive-staging` stack.
- Selected PostgreSQL `18.4`, which is currently orderable for
  `db.t4g.small` in `ap-northeast-2` and aligns with the PostgreSQL 18 local
  baseline.
- Selected Docker Compose `v2.40.3` and verified the official
  `docker-compose-linux-aarch64` SHA-256. Docker Compose v5 was not adopted
  because it is a major version change requiring separate approval.
- Created the ignored `staging.parameters.local.json` and passed local input
  validation with the real staging account boundary.
- Created the account-level GitHub OIDC provider with only the
  `sts.amazonaws.com` audience after explicit approval.
- Created the staging RDS master password as an SSM Standard `SecureString`
  after explicit approval. The first local random-generation API was
  unsupported and produced an unusable initial value; no database or stack
  consumed it. It was immediately replaced by Version 2 using
  `RandomNumberGenerator.Create().GetBytes()` before any RDS operation.
- Fixed Windows Git Bash interoperability discovered by real preflight:
  carriage-return normalization, SSM filter protection from MSYS path
  conversion, and Windows template path conversion through `cygpath`.
- Completed the full read-only AWS preflight against account `231851555445` in
  `ap-northeast-2`.
- Created the separately approved review-only CloudFormation change set
  `staging-foundation-cdd2c29`. It reached `CREATE_COMPLETE` and remains
  `AVAILABLE`; it has not been executed.
- Reviewed all 33 proposed resources and identified an execution blocker: the
  EC2 application role can read `/time-archive/staging/*`, which currently
  contains the RDS master password prerequisite. A compromised application host
  could therefore retrieve the database administrator credential even though
  the runtime renderer does not map that parameter.
- Prohibited execution of the current change set pending approval to move the
  master password outside the application-readable runtime path and regenerate
  the review-only change set.
- After explicit approval, moved the master password prerequisite to
  `/time-archive/bootstrap/staging/database/master-password`, outside the EC2
  role's `/time-archive/staging/*` runtime read boundary.
- Added CloudFormation policy validation and a negative self-test that reject a
  master password under the application-readable runtime path.
- Created and verified the new bootstrap SecureString as Version 1, deleted the
  rejected change set and its empty review stack record, and removed the old
  runtime-path parameter.
- Created corrected review-only change set
  `staging-foundation-0dbb58d74e6b`, tied to template SHA-256
  `0dbb58d74e6bbc8f1ace726c31bd89b7844ab8752dfa8b5879949a1264dcee0c`.
  It reached `CREATE_COMPLETE / AVAILABLE` with 33 proposed resources and has
  not been executed.
- Confirmed from actual change-set properties that CloudFormation uses the
  bootstrap path while the EC2 role can read only the runtime path.
- Replaced the corrected 33-resource review state after the project owner chose
  an alert destination. The current review-only change set is
  `staging-foundation-0dbb58d7-667a49a5`, with template SHA-256
  `0dbb58d74e6bbc8f1ace726c31bd89b7844ab8752dfa8b5879949a1264dcee0c`
  and ignored parameter-file SHA-256
  `667a49a5e2ec862313d2a336bdfe629f0225b3a8ed9c71a8e64d5b4f2897414f`.
- Confirmed the current change set is `CREATE_COMPLETE / AVAILABLE` with 34
  resources, including one email SNS subscription to the owner-provided alert
  destination. The email address remains only in the ignored local parameter
  file and AWS review state.
- Executed the explicitly approved 34-resource change set. The EC2 application
  instance sent a failure signal during user-data bootstrap, and CloudFormation
  completed automatic rollback to `ROLLBACK_COMPLETE`.
- Confirmed RDS was never created and no ECR repository, SNS topic, stack IAM
  role, or running EC2 instance survived rollback.
- The terminated instance's final cloud-init failure detail was unavailable.
  Added persistent bootstrap logging to the EC2 console and local log plus
  failed line, command, and exit code in `cfn-signal --reason` before any retry.
- Deleted the resource-empty `ROLLBACK_COMPLETE` stack record after preserving
  the failure outcome in this plan and operations documentation.
- Re-ran real AWS preflight and created diagnostic review-only change set
  `staging-foundation-d91a71c5-667a49a5`, using template SHA-256
  `d91a71c513f90fd47bb4f61b2cf353500a20c674a5c96fdc47d6bc354428aed5`
  and the unchanged ignored parameter-file SHA-256
  `667a49a5e2ec862313d2a336bdfe629f0225b3a8ed9c71a8e64d5b4f2897414f`.
- Confirmed the new 34-resource change set is `CREATE_COMPLETE / AVAILABLE`
  and includes bootstrap console logging, detailed failure reasons, the alert
  subscription, and the infrastructure-only master password path.
- Executed the approved diagnostic change set and captured the exact bootstrap
  failure from EC2 console output: Amazon Linux 2023's preinstalled
  `curl-minimal` conflicts with explicitly installing the separate `curl`
  package in the same DNF transaction.
- Removed `curl` from the package installation list while retaining the
  preinstalled `curl` command, and added policy validation that rejects the
  conflicting package request.
- Created corrected review-only change set
  `staging-foundation-ad3b63dc-667a49a5`, using template SHA-256
  `ad3b63dcaeab7ec4df56eacb740ecae13a5695c73d390b18627bb195b7eb1ccf`
  and the unchanged ignored parameter-file SHA-256
  `667a49a5e2ec862313d2a336bdfe629f0225b3a8ed9c71a8e64d5b4f2897414f`.
- Confirmed the 34-resource change set is `CREATE_COMPLETE / AVAILABLE`, the
  reviewed package line excludes the conflicting `curl` package, diagnostics
  remain enabled, and alert and credential boundaries are unchanged.
- Executed the explicitly approved corrected change set. EC2 bootstrap passed,
  RDS creation completed, and the stack reached `CREATE_COMPLETE`.
- Verified the ARM64 `t4g.medium` EC2 instance, encrypted gp3 volume, IMDSv2,
  no application ingress, SSM Online state, Docker, Compose checksum,
  CloudWatch Agent, protected deployment directories, and TCP connectivity to
  the private RDS endpoint.
- Verified PostgreSQL `18.4` on private encrypted Single-AZ `db.t4g.small`, 20
  GiB gp3 storage, one-day staging backups, log export, and application-security
  group-only ingress on port 5432.
- Verified immutable scan-on-push ECR repositories, 30-image lifecycle rules,
  14-day log retention, alarms wired to SNS, and scoped actual IAM/OIDC trust
  policies.
- Confirmed the alert email subscription is `PendingConfirmation` and requires
  the project owner to confirm the AWS email.
- Found that AWS created the default all-protocol egress rule for the database
  security group despite an empty CloudFormation egress list. This does not
  open database ingress, but it differs from the intended egress-none policy
  and is recorded for a targeted hardening change set.

## Completion Summary

The repository now provides a staging provisioning preflight that validates an
ignored operator parameter file before any CloudFormation review state is
created. Operators can run local validation first and explicitly enable
read-only AWS checks to verify the authenticated account, fixed region, GitHub
OIDC provider, database password SecureString metadata, RDS engine/class
availability, and target-account CloudFormation template validity.

A separate runbook defines change-set preparation, mandatory review, explicit
execution approval, abandonment, and post-execution handoff. No mutating AWS
operation is implemented in the preflight. The separately approved OIDC and
SSM prerequisites were created. After two automatically rolled-back bootstrap
attempts and a diagnosed Amazon Linux package conflict, the corrected stack
completed successfully and now provides the staging AWS foundation.

## Files Changed

- Added `scripts/verify-staging-provisioning-inputs.sh`.
- Added `scripts/verify-staging-provisioning-preflight.sh`.
- Added `infra/cloudformation/staging.parameters.test.json`.
- Added `docs/operations/staging-provisioning-runbook.md`.
- Ignored `infra/cloudformation/staging.parameters.local.json`.
- Added provisioning preflight validation to `.github/workflows/ci.yml`.
- Updated README, CI/CD strategy, staging foundation, and release-readiness
  documentation.
- Added and completed this implementation plan.

## Tests Run And Results

- Staging provisioning local valid fixture: passed.
- Negative parameter fixtures for placeholder, malformed checksum, OIDC account
  mismatch, unexpected key, and secret field: passed by being rejected.
- Fake AWS CLI read-only preflight happy path: passed.
- Fake AWS CLI account mismatch, non-SecureString, and unavailable RDS option:
  passed by being rejected.
- Windows Git Bash CRLF, SSM filter, and template path compatibility: passed.
- Real AWS preflight for STS, IAM OIDC, SSM metadata, RDS availability, and
  CloudFormation template validation: passed.
- Mutating AWS command policy scan: passed.
- Staging CloudFormation `cfn-lint` and architecture policy validation: passed.
- Bootstrap diagnostic logging and failure-reason policy validation: passed.
- Corrected EC2 bootstrap and complete CloudFormation stack creation: passed.
- EC2, SSM, Docker, Compose checksum, CloudWatch Agent, directory permissions,
  and RDS TCP connectivity verification: passed.
- RDS, ECR, IAM/OIDC, security group, EBS, log retention, alarm, and lifecycle
  policy verification: passed with the database egress caveat below.
- Shell syntax validation for repository shell scripts: passed.
- GitHub Actions YAML parsing: passed.
- Local Markdown link validation: passed.
- `git diff --check`: passed.

## Manual Verification Results

The ignored local parameter path was confirmed with `git check-ignore`. The
runbook and script output were manually reviewed for secret handling and AWS
mutation boundaries. AWS CLI v2 installation and browser login were confirmed,
and `sts get-caller-identity` initially returned the account root principal.
That session was logged out. A replacement IAM Identity Center profile was
configured and verified as the non-root `AWSReservedSSO_AdministratorAccess`
role in account `231851555445`.

Read-only IAM, SSM, RDS, and CloudFormation discovery then confirmed no GitHub
OIDC provider, no staging database master password parameter, no existing
staging stack, and PostgreSQL `18.4` availability for `db.t4g.small`. The
ignored real parameter file passed local preflight.

After explicit approval, the missing OIDC provider and SSM SecureString were
created. The complete read-only preflight then passed. The SSM secret value was
never printed, written to the repository, or requested with decryption during
verification.

The reviewed 34-resource change set was executed. CloudFormation reported an
EC2 bootstrap failure and automatically rolled back. RDS did not start
creation. Post-rollback AWS reads confirmed no ECR repository, SNS topic, stack
IAM role, or running EC2 instance remained.

The diagnostic retry identified the `curl-minimal` package conflict and also
rolled back cleanly. After correcting that package request, the final approved
change set reached `CREATE_COMPLETE`. SSM-based host verification and read-only
AWS resource inspection passed for the resulting stack.

## Known Limitations

- The SNS email subscription remains pending owner confirmation.
- Database security group ingress is correctly restricted, but AWS supplied a
  default outbound-all rule despite the empty egress list in the template.
- No application image has been published or deployed, so application runtime,
  migration, Cloudflare Tunnel, R2, and public health behavior remain
  unverified.
- Runtime and migration database identities and runtime SSM parameters are not
  provisioned yet.

## Follow-Up Recommendations

1. Confirm the SNS email subscription.
2. Add and review a targeted database security-group egress hardening update.
3. Configure GitHub repository variables from stack outputs and run the staging
   image publication workflow.
4. Add the SSM staging deployment workflow and provision isolated runtime
   parameters before deploying application images.
