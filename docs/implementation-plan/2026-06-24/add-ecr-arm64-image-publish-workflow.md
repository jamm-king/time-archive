# Add ECR ARM64 Image Publish Workflow

## Objective

Add a manually triggered GitHub Actions workflow that uses short-lived GitHub
OIDC credentials to build the API and Web images for `linux/arm64`, push them to
the staging ECR repositories under an immutable full Git SHA tag, and verify the
published digests.

## Scope

- Add the staging image publication workflow.
- Add static policy validation for the workflow without requiring AWS
  credentials or provisioned ECR repositories.
- Extend the staging image-publisher IAM role only with the read permission
  required to verify a completed push.
- Add CI coverage for the workflow policy.
- Update staging deployment and release-readiness documentation.

Out of scope:

- Creating ECR repositories, the GitHub OIDC provider, IAM roles, repository
  variables, or GitHub environments.
- Running the publication workflow or pushing an image.
- Adding the staging SSM deployment workflow.
- Deploying the published images to EC2.
- Changing application dependencies or Dockerfiles.

## Relevant Files Or Modules

- `.github/workflows/publish-staging-images.yml`
- `.github/workflows/ci.yml`
- `scripts/verify-staging-image-publish-workflow.sh`
- `infra/cloudformation/staging.yml`
- `scripts/verify-staging-cloudformation.sh`
- `docs/operations/staging-image-publication.md`
- `docs/operations/staging-cloudformation-foundation.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- Publication is `workflow_dispatch` only until staging AWS resources exist.
  Automatic publication on every `main` push is deferred to avoid predictable
  failures and unintended ECR storage use.
- The workflow can assume AWS credentials only when dispatched from `main`.
  The IAM OIDC subject remains the authoritative enforcement boundary.
- No long-lived AWS access key or GitHub secret is used. The workflow reads only
  non-secret repository variables for AWS account ID, region, and role ARN.
- ECR repository URIs are constructed from the validated account and region and
  the CloudFormation-owned repository names. Operators cannot redirect a run to
  an arbitrary repository through workflow inputs.
- Images use only the full `github.sha` tag. Mutable `latest`, branch, staging,
  and release aliases are not published.
- API and Web builds target only `linux/arm64`, matching the selected Graviton
  host.
- BuildKit provenance and SBOM attestations are published with each image.
- The workflow queries ECR after each push and requires the ECR digest to match
  the Buildx output before reporting success.
- Concurrent staging publication runs are serialized and never cancel an
  in-progress push.

## Action Dependency Review

New workflow actions were checked from their official GitHub repositories on
2026-06-24 and are pinned to full commit SHAs:

| Action | Release | License | Reason |
| --- | --- | --- | --- |
| `actions/checkout` | v4 | MIT | Check out the exact `main` workflow commit. |
| `docker/setup-qemu-action` | v4 | Apache-2.0 | Execute ARM64 build stages on GitHub's AMD64 runner. |
| `docker/setup-buildx-action` | v4 | Apache-2.0 | Create the BuildKit builder used for registry output and attestations. |
| `aws-actions/configure-aws-credentials` | v6 | MIT | Exchange GitHub OIDC identity for the scoped AWS role. |
| `aws-actions/amazon-ecr-login` | v2 | MIT | Authenticate Docker to private ECR without exposing a password. |
| `docker/build-push-action` | v7 | Apache-2.0 | Build, attest, cache, and push the two ARM64 images. |

Using shell-only AWS STS and Docker login commands was considered. The official
AWS actions were selected because they implement OIDC token exchange, credential
cleanup, account validation, password masking, and ECR login behavior without
maintaining custom credential code. Existing Docker actions are reused rather
than introducing another build system. Existing CI action versions are not
upgraded in this task.

## Required Repository Variables

The future workflow run requires:

- `AWS_ACCOUNT_ID`: the 12-digit staging AWS account ID.
- `AWS_REGION`: `ap-northeast-2` for the current staging template.
- `AWS_STAGING_IMAGE_PUBLISH_ROLE_ARN`: the
  `GitHubImagePublisherRoleArn` CloudFormation output.

These values are identifiers, not secrets. The workflow rejects malformed or
cross-account combinations before requesting an OIDC token.

## Step-By-Step Execution Plan

1. [Completed] Inspect current CI, Dockerfiles, ECR resources, OIDC trust, and
   operations documentation.
2. [Completed] Verify current official action majors, runtimes, commit SHAs, and
   license compatibility.
3. [Completed] Add the manual staging image publication workflow.
4. [Completed] Add static workflow policy validation and CI coverage.
5. [Completed] Extend CloudFormation IAM and policy validation for digest reads.
6. [Completed] Update operations and release-readiness documentation.
7. [Completed] Run workflow parsing, policy self-tests, CloudFormation linting,
   shell checks, and repository diff checks.

## Risks And Rollback Strategy

- A workflow running untrusted branch code with AWS credentials could publish a
  malicious image. Manual runs are restricted to `main`, and IAM trust allows
  only the `main` OIDC subject.
- Repository variables could point to another account or registry. Account,
  region, role ARN, registry hostname, and fixed repository names are validated
  before credentials or builds are used.
- A mutable tag could overwrite deployment history. Only the full Git SHA is
  used against immutable ECR repositories.
- One image could publish while the other fails. The workflow reports failure
  and does not deploy anything. A later rerun is idempotent only when both tags
  are absent; immutable repositories reject overwriting an existing tag, so
  partial publication requires operator review before retry.
- Build or registry attestations can increase ECR storage. The staging ECR
  lifecycle policy bounds retained images, and actual storage must be measured
  after provisioning.
- Repository rollback is a Git revert. No external rollback is required because
  this task does not execute the workflow.

## Verification Plan

- Parse the new and existing GitHub Actions YAML files.
- Run `./scripts/verify-staging-image-publish-workflow.sh`.
- Run policy self-tests for mutable tags, non-ARM64 platforms, missing OIDC
  permission, automatic triggers, and branch trust weakening.
- Run `./scripts/verify-staging-cloudformation.sh` after the IAM extension.
- Run `bash -n` for all shell scripts.
- Run `git diff --check` and inspect executable modes.
- Confirm no AWS account ID, access key, role ARN, registry password, or secret
  is committed.

## Open Questions

- Publication cannot be tested end to end until the staging stack, account OIDC
  provider, and repository variables exist.
- Partial publication recovery under immutable ECR tags needs an operator
  runbook before the first real run.
- Whether every successful `main` build should publish automatically remains a
  later cost and release-cadence decision.

## Progress

- Confirmed `main` includes PR #63 and the staging ECR/IAM foundation.
- Confirmed both Dockerfiles already build successfully for ARM64 in CI.
- Confirmed official current action majors use Node 24 where available and
  recorded their full release tag commit SHAs.
- Added immutable publication preflight behavior for complete, absent, and
  partial API/Web tag states.
- Added policy self-tests for automatic triggers, OIDC permission weakening,
  non-ARM64 builds, mutable tags, and main-branch guard removal.
- Documented repository variables, OIDC trust, immutable tag behavior, digest
  handoff, and approval-gated partial publication recovery.

## Completion Summary

The repository now contains a manual, `main`-only staging image publication
workflow. It exchanges GitHub OIDC identity for the scoped staging publisher
role, validates account and registry configuration, builds API and Web images
for `linux/arm64`, pushes only immutable full Git SHA tags, publishes provenance
and SBOM attestations, and verifies ECR digests against Buildx outputs.

The workflow safely handles complete and absent paired tags and rejects partial
API/Web publication for operator review. It does not deploy images or create
AWS resources.

## Files Changed

- Added `.github/workflows/publish-staging-images.yml`.
- Added `scripts/verify-staging-image-publish-workflow.sh`.
- Added `docs/operations/staging-image-publication.md`.
- Extended the staging ECR publisher IAM role with `ecr:DescribeImages`.
- Extended CloudFormation policy validation for digest-read permission.
- Added publication workflow validation to `.github/workflows/ci.yml`.
- Updated staging infrastructure, CI/CD, deployment architecture,
  release-readiness, and README documentation.
- Added this implementation plan.

## Tests Run And Results

- Staging image publication workflow policy validation: passed.
- Workflow policy negative self-tests for trigger, OIDC permission, platform,
  tag, and branch guard changes: passed.
- Staging CloudFormation `cfn-lint` and architecture policy validation: passed.
- GitHub Actions YAML parsing for all workflows: passed.
- Shell syntax validation for all repository shell scripts: passed.
- Local Markdown link validation: passed.
- Secret, AWS account, and credential pattern checks: passed.
- `git diff --check`: passed.

## Manual Verification Results

No workflow was dispatched, OIDC token requested, AWS role assumed, image
built for publication, or ECR object created. These actions require the staging
stack, account OIDC provider, repository variables, and explicit operator
execution from `main`.

## Known Limitations

- End-to-end OIDC, ECR login, image push, digest lookup, provenance, SBOM, and
  scan behavior are not verified against AWS.
- ECR scan findings are not an automated gate.
- Paired repository publication is not transactional. Partial recovery requires
  operator review and a separately approved destructive image deletion when
  appropriate.
- The workflow is manual; automatic publication cadence is undecided.
- The staging deployment workflow does not exist yet.

## Follow-Up Recommendations

1. Apply and verify the staging CloudFormation stack through an approved change
   set.
2. Configure the account OIDC provider and required repository variables.
3. Run the publication workflow once from a CI-green `main` commit and inspect
   images, attestations, scans, digests, logs, and cost.
4. Add the SSM Run Command staging deployment workflow using exact image SHA
   references only after publication is verified.
