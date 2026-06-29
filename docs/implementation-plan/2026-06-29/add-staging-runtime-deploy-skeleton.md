# Add Staging Runtime Deployment Skeleton

## Objective

Define and verify the staging runtime parameter contract needed for the EC2
Docker Compose deployment path.

## Scope

- Add staging-specific placeholder runtime and SSM parameter fixtures.
- Add local/CI validation for staging runtime rendering.
- Update deployment documentation and release readiness status.
- Reuse the existing shared EC2 deployment scripts instead of duplicating a
  second staging script set.

Out of scope:

- Creating or updating real SSM parameters.
- Running SSM Run Command against the staging EC2 instance.
- Adding the GitHub Actions staging deployment workflow.
- Creating Cloudflare Tunnel, R2, PayPal, or production resources.

## Relevant Files

- `deploy/production/render-runtime-env.sh`
- `deploy/production/deploy.sh`
- `deploy/production/docker-compose.yml`
- `deploy/staging/runtime.env.example`
- `deploy/staging/ssm-parameters.example.json`
- `scripts/verify-staging-deployment-runtime.sh`
- `.github/workflows/ci.yml`
- `docs/operations/production-deployment-foundation.md`
- `docs/operations/ec2-rds-deployment-architecture.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- The existing deployment scripts already accept `staging` and `production`.
  Staging should use the same deployment path to avoid drift.
- Staging gets its own non-secret fixtures under `deploy/staging/` so the SSM
  path, bucket names, log group prefix, and placeholder values are explicit.
- CI validates staging rendering without contacting AWS or printing secrets.
- The real deployment workflow remains deferred until runtime parameters and
  Cloudflare Tunnel values are provisioned.

## Step-by-step Execution Plan

1. Inspect the existing deployment scripts and documentation.
2. Add staging runtime placeholder files.
3. Add a staging runtime verification script.
4. Wire the verification into CI.
5. Update operational documentation and release readiness status.
6. Run focused verification.

## Risks And Rollback Strategy

- Risk: duplicated staging and production contracts drift. Mitigation: reuse the
  shared renderer and Compose file, and validate the staging fixture through the
  same path.
- Risk: accidental secret leakage. Mitigation: fixtures use placeholders only,
  and no AWS calls are made.
- Rollback: revert this repository-only commit. No external resource is changed.

## Verification Plan

- Run `scripts/verify-staging-deployment-runtime.sh`.
- Run `scripts/verify-production-deployment.sh` if Docker is available.
- Run shell syntax validation for changed shell scripts.
- Run `git diff --check`.

## Open Questions

- Which exact staging public hostname should be used for
  `TIME_ARCHIVE_PUBLIC_BASE_URL` during deployment verification?

## Progress

- Confirmed the existing `deploy/production` scripts already support the
  `staging` argument.
- Confirmed the staging CloudFormation host bootstrap creates the directories
  expected by the deployment scripts.
- Added staging runtime and SSM parameter placeholder fixtures.
- Added staging runtime verification that validates the fixture, renders the
  runtime environment through the shared renderer, and checks Compose policy.
- Added the staging runtime verification to CI.
- Updated deployment architecture, deployment foundation, and release readiness
  documentation.

## Completion Summary

The repository now has an explicit staging runtime parameter contract and a
CI-backed verification path for rendering `/time-archive/staging/...`
parameters into the shared EC2 Docker Compose deployment model. No external AWS,
Cloudflare, R2, or SSM state was changed.

## Files Changed

- `.github/workflows/ci.yml`
- `deploy/staging/runtime.env.example`
- `deploy/staging/ssm-parameters.example.json`
- `docs/operations/ec2-rds-deployment-architecture.md`
- `docs/operations/production-deployment-foundation.md`
- `docs/operations/release-readiness-checklist.md`
- `scripts/verify-staging-deployment-runtime.sh`
- `docs/implementation-plan/2026-06-29/add-staging-runtime-deploy-skeleton.md`

## Tests Run And Results

- `C:\Program Files\Git\bin\bash.exe -lc "./scripts/verify-staging-deployment-runtime.sh"`
  - Passed.
- `C:\Program Files\Git\bin\bash.exe -lc "./scripts/verify-production-deployment.sh"`
  - Passed.
- `git diff --check`
  - Passed.

## Manual Verification Results

The staging fixture was reviewed to ensure it contains only placeholders under
`/time-archive/staging/...`. The deployment scripts were reused rather than
copied so staging and production continue to share one deployment behavior.

## Known Limitations

- Real staging SSM parameters have not been created.
- The staging EC2 host has not pulled the published images or started the
  application containers.
- The GitHub Actions SSM deployment workflow is still pending.
- The staging public hostname for optional public smoke checks is still an open
  owner decision.

## Follow-up Recommendations

- Provision the staging SSM parameters from the documented contract.
- Add the GitHub Actions staging deployment workflow using SSM Run Command.
- Configure the Cloudflare Tunnel and staging hostname before public smoke
  verification.
