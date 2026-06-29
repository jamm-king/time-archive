# Add Staging SSM Deploy Workflow

## Objective

Add a manual GitHub Actions workflow that deploys already-published staging
images to the staging EC2 host through AWS Systems Manager Run Command.

## Scope

- Add a `workflow_dispatch` staging deployment workflow.
- Package the reviewed deployment bundle from `deploy/production` and transfer
  it through SSM Run Command.
- Validate immutable image references before deployment.
- Add the minimum deploy-role ECR permission required for image verification.
- Add static policy validation for the new workflow.
- Update deployment documentation and release readiness status.

Out of scope:

- Running the first staging deployment from this branch.
- Creating GitHub repository or environment variables.
- Implementing automatic rollback.

## Relevant Files

- `.github/workflows/deploy-staging.yml`
- `deploy/production/deploy.sh`
- `scripts/verify-staging-deploy-workflow.sh`
- `.github/workflows/ci.yml`
- `docs/operations/staging-deployment.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- The workflow is manual only and runs only from `main`.
- The job uses GitHub Environment `staging` so the OIDC subject matches the
  CloudFormation deploy role trust.
- API and Web images are selected by one full Git SHA tag and verified in ECR
  before deployment.
- Redis and `cloudflared` images must be digest-pinned workflow inputs.
- The workflow does not read application secrets. EC2 reads runtime parameters
  from SSM through its instance role.
- The deployment bundle is transferred through SSM Run Command because the
  current deploy role intentionally has no S3 write access and SSH is disabled.
- The deploy role gets only `ecr:DescribeImages` on the staging API and Web
  repositories so the workflow can fail before SSM execution when a requested
  image tag was not published.

## Step-by-step Execution Plan

1. Inspect existing image publication and deployment scripts.
2. Add the manual staging deployment workflow.
3. Add the minimum CloudFormation IAM permission for ECR image verification.
4. Add workflow and CloudFormation policy validation.
5. Update CI to run the new validation.
6. Update operations documentation and release readiness status.
7. Run local validation.

## Risks And Rollback Strategy

- Risk: deploying an image that was not published by the staging image workflow.
  Mitigation: verify both API and Web SHA tags exist in ECR before sending SSM.
- Risk: mutable third-party images. Mitigation: require Redis and `cloudflared`
  digest references.
- Risk: SSM command payload becomes too large. Mitigation: transfer only the
  small reviewed deployment bundle and validate the workflow statically.
- Risk: failed migration or service health. Mitigation: `deploy.sh` stops on
  migration or health failure and records the release only after verification.
- Rollback: use the previous release record on EC2 to rerun deployment with the
  previous image references. Automated rollback remains a follow-up.

## Verification Plan

- Run `scripts/verify-staging-deploy-workflow.sh`.
- Run `scripts/verify-production-deployment.sh`.
- Run `scripts/verify-staging-deployment-runtime.sh`.
- Run shell syntax validation.
- Run `git diff --check`.

## Open Questions

- The first staging deployment requires reviewed Redis and `cloudflared`
  digest-pinned image references.
- Public hostname smoke verification remains optional until the Cloudflare
  Tunnel route is confirmed.

## Progress

- Created the dedicated branch from latest `main`.
- Added the manual staging deployment workflow.
- Added CI policy validation for the staging deployment workflow.
- Added limited `ecr:DescribeImages` permission to the staging deploy role.
- Updated operational documentation and the release readiness checklist.

## Completion Summary

Implemented a manual staging deployment path that deploys already-published
API and Web images to the staging EC2 instance through SSM Run Command. The
workflow is restricted to `main`, uses the `staging` GitHub Environment for
OIDC, validates immutable application images and digest-pinned infrastructure
images, and sends only the reviewed deployment bundle to the EC2 host.

The staging CloudFormation template now grants the deploy role only
`ecr:DescribeImages` on the staging API and Web repositories so image
verification can happen before SSM execution.

## Files Changed

- `.github/workflows/deploy-staging.yml`
- `.github/workflows/ci.yml`
- `deploy/production/deploy.sh`
- `infra/cloudformation/staging.yml`
- `scripts/verify-staging-cloudformation.sh`
- `scripts/verify-staging-deploy-workflow.sh`
- `docs/operations/staging-deployment.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `docs/operations/production-deployment-foundation.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/staging-image-publication.md`

## Tests Run And Results

- `bash -n scripts/verify-staging-deploy-workflow.sh`: passed.
- `scripts/verify-staging-deploy-workflow.sh`: passed with local
  `PYTHONPATH=temp/cfn-lint`.
- `scripts/verify-staging-cloudformation.sh`: passed with local
  `PYTHONPATH=temp/cfn-lint` and `CFN_LINT_BIN`.
- `scripts/verify-staging-image-publish-workflow.sh`: passed with local
  `PYTHONPATH=temp/cfn-lint`.
- `scripts/verify-staging-deployment-runtime.sh`: passed.
- `scripts/verify-production-deployment.sh`: passed.
- `git diff --check`: passed.

## Manual Verification Results

No live staging deployment was run from this branch. The first deployment must
run only after this change is merged to `main`, the staging CloudFormation
stack is updated, GitHub repository variables are configured, and the selected
API/Web image SHA plus Redis/cloudflared digest references are reviewed.

## Known Limitations

- Automated rollback remains deferred.
- Public staging smoke verification remains optional until the Cloudflare
  Tunnel hostname is confirmed.
- ECR scan findings are not yet an automated deployment gate.

## Follow-up Recommendations

- Merge this branch, update the staging CloudFormation stack, and run the
  workflow once with the last successfully published staging image SHA.
- Add a staged rollback drill after the first successful deployment.
