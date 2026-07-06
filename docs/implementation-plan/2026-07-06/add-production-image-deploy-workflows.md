# Add Production Image And Deploy Workflows

## Objective

Add manual GitHub Actions workflows for production image publication and
production deployment, plus CI policy checks that prevent accidental staging
resource use, mutable images, long-lived secrets, and automatic production
deployment.

## Scope

- Add production API/Web image publication workflow.
- Add production deployment workflow through SSM Run Command.
- Add workflow policy verification scripts.
- Wire the verification scripts into CI.
- Update production deployment documentation and release readiness state.

Out of scope:

- Executing production image publication.
- Executing production deployment.
- Creating or changing production AWS, Cloudflare, R2, or PayPal resources.
- Enabling paid production traffic.

## Relevant Files Or Modules

- `.github/workflows/publish-production-images.yml`
- `.github/workflows/deploy-production.yml`
- `.github/workflows/ci.yml`
- `scripts/verify-production-image-publish-workflow.sh`
- `scripts/verify-production-deploy-workflow.sh`
- `docs/operations/production-deployment-foundation.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- Production workflows are manual `workflow_dispatch` only.
- Production deployment uses the GitHub `production` environment.
- Production image publication does not use an environment so its OIDC subject
  stays branch-scoped, matching the existing staging image-publisher pattern.
- Workflows use short-lived GitHub OIDC AWS credentials only.
- Production repository variable names are:
  - `AWS_PRODUCTION_IMAGE_PUBLISH_ROLE_ARN`
  - `AWS_PRODUCTION_DEPLOY_ROLE_ARN`
  - `PRODUCTION_INSTANCE_ID`
- API/Web images are published only as immutable full Git SHA tags.
- Redis and cloudflared deployment inputs must be digest-pinned.
- Deployment execution is intentionally separate from this implementation and
  requires explicit operator approval.

## Step-By-Step Execution Plan

- [x] Create a dedicated feature branch.
- [x] Review staging image publication and deployment workflow patterns.
- [x] Add production image publication workflow.
- [x] Add production deployment workflow.
- [x] Add production workflow policy verification scripts.
- [x] Wire production workflow checks into CI.
- [x] Update operations documentation and release readiness checklist.
- [x] Run local workflow verification scripts.
- [x] Run relevant static checks.
- [x] Record completion summary.

## Risks And Rollback Strategy

- Risk: Production workflow could accidentally target staging resources.
  Mitigation: add CI policy checks for production repository names, production
  role variables, production environment, and production instance variable.
- Risk: Production workflow could deploy mutable or partial images.
  Mitigation: require full Git SHA tags for API/Web and digest pins for Redis
  and cloudflared; verify ECR image digests before deployment.
- Risk: Production workflow could expose long-lived secrets.
  Mitigation: forbid `secrets.*` usage and rely on short-lived OIDC credentials
  plus EC2 SSM runtime rendering.
- Risk: Manual deployment could start production containers before final
  readiness.
  Mitigation: keep workflow manual and use GitHub production environment
  approval rules.

Rollback:

- Revert the workflow and verification script commit before use.
- If a production deployment is executed later and fails, use the deployment
  logs, current/previous release files, and the documented production rollback
  procedure before paid traffic is enabled.

## Verification Plan

- Run `scripts/verify-production-image-publish-workflow.sh`.
- Run `scripts/verify-production-deploy-workflow.sh`.
- Run `scripts/verify-production-deployment.sh`.
- Run `git diff --check`.
- Confirm no local configuration, logs, generated artifacts, or secrets are
  staged.

## Open Questions

- None for workflow implementation. Actual workflow execution remains a later
  approval-gated operation.

## Progress

- 2026-07-06: Created branch
  `feature/production-image-deploy-workflow`.
- 2026-07-06: Reviewed existing staging image publication and deployment
  workflows, plus their CI policy verification scripts.
- 2026-07-06: Added manual production image publication workflow using
  `AWS_PRODUCTION_IMAGE_PUBLISH_ROLE_ARN` and production ECR repository names.
- 2026-07-06: Added manual production deployment workflow using the GitHub
  `production` environment, `AWS_PRODUCTION_DEPLOY_ROLE_ARN`, and
  `PRODUCTION_INSTANCE_ID`.
- 2026-07-06: Added production workflow policy verification scripts and wired
  them into CI.
- 2026-07-06: Updated production deployment documentation and release readiness
  checklist with the new workflow readiness state.
- 2026-07-06: Ran production workflow verification scripts using a temporary
  local PyYAML virtualenv because the default local Python does not have
  PyYAML installed. Both production workflow policy checks passed.
- 2026-07-06: Ran production deployment static validation and shell syntax
  checks successfully.

## Completion Summary

Manual production image publication and deployment workflows are now present.
The image workflow publishes API/Web ARM64 images to production ECR repositories
with immutable full Git SHA tags, provenance, SBOM, and digest verification.
The deployment workflow is manual, main-branch-only, gated by the GitHub
`production` environment, and deploys through SSM Run Command to the production
EC2 instance variable.

The new policy verification scripts are wired into CI so production workflow
changes are checked for staging resource drift, long-lived secret usage,
unreviewed actions, mutable image references, missing main-branch guards, and
missing production environment gating.

## Files Changed

- `.github/workflows/publish-production-images.yml`
- `.github/workflows/deploy-production.yml`
- `.github/workflows/ci.yml`
- `scripts/verify-production-image-publish-workflow.sh`
- `scripts/verify-production-deploy-workflow.sh`
- `docs/operations/production-deployment-foundation.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-06/add-production-image-deploy-workflows.md`

## Tests Run And Results

- `scripts/verify-production-image-publish-workflow.sh` passed using a
  temporary local PyYAML virtualenv.
- `scripts/verify-production-deploy-workflow.sh` passed using a temporary local
  PyYAML virtualenv.
- `scripts/verify-production-deployment.sh` passed.
- `bash -n scripts/verify-production-image-publish-workflow.sh
  scripts/verify-production-deploy-workflow.sh` passed.

## Manual Verification Results

- Confirmed the new workflows are manual `workflow_dispatch` workflows only.
- Confirmed the production deploy workflow uses the GitHub `production`
  environment.
- Confirmed production workflow policy checks are connected to CI.

## Known Limitations

- The workflows have not been executed yet. Production image publication and
  deployment remain explicit operator actions.
- Local verification required a temporary PyYAML virtualenv because the default
  local Python environment does not include PyYAML. CI installs the repository
  CloudFormation validation requirements before running these checks.

## Follow-Up Recommendations

- Configure repository variables if they are not already present:
  `AWS_PRODUCTION_IMAGE_PUBLISH_ROLE_ARN`, `AWS_PRODUCTION_DEPLOY_ROLE_ARN`,
  and `PRODUCTION_INSTANCE_ID`.
- After PR merge, run `Publish production images` from `main`.
- After image publication succeeds, run `Deploy production` with the published
  full Git SHA and reviewed digest-pinned Redis/cloudflared image references.
- Run production smoke workflows after deployment before paid traffic is
  enabled.
