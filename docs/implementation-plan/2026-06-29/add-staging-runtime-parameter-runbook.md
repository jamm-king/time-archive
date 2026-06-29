# Add Staging Runtime Parameter Runbook

## Objective

Prepare the repository-side runbook and validation needed before creating the
real staging runtime SSM parameters.

## Scope

- Document the exact staging runtime parameter creation process.
- Add a validator for the staging SSM parameter contract.
- Allow optional read-only AWS metadata verification without decrypting values.
- Wire local validation into CI.

Out of scope:

- Creating, updating, or deleting real SSM parameters.
- Printing or storing real secret values.
- Creating database users on RDS.
- Running the staging EC2 deployment workflow.

## Relevant Files

- `deploy/staging/ssm-parameters.example.json`
- `deploy/staging/runtime.env.example`
- `deploy/production/render-runtime-env.sh`
- `scripts/verify-staging-runtime-parameters.sh`
- `.github/workflows/ci.yml`
- `docs/operations/staging-runtime-parameters.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- Real SSM values remain operator-managed and are not represented in Git.
- The validator checks names and parameter types, not secret values.
- Optional AWS verification uses SSM metadata APIs only and must not request
  decryption.
- The first staging deployment may use a single database identity for both
  Flyway and runtime because the current application has one datasource. This
  is acceptable only for staging and remains a production blocker until split.

## Step-by-step Execution Plan

1. Inspect the current staging deployment fixture and deployment renderer.
2. Add a runtime parameter runbook with value sources, commands, and safety
   boundaries.
3. Add a validation script for local fixture and optional AWS metadata checks.
4. Add the validator to CI.
5. Update release readiness and deployment documentation.
6. Run focused verification.

## Risks And Rollback Strategy

- Risk: accidentally exposing secrets in command output or committed files.
  Mitigation: the runbook uses placeholder variables and the validator does not
  decrypt SSM values.
- Risk: parameter names drift from the renderer. Mitigation: the validator
  derives the required contract from the committed staging fixture.
- Risk: staging deployment fails because the database identity has insufficient
  migration privileges. Mitigation: explicitly document the required first
  staging privilege model and the production follow-up to separate identities.
- Rollback: revert this repository-only change. No external state is modified.

## Verification Plan

- Run `scripts/verify-staging-runtime-parameters.sh`.
- Run `scripts/verify-staging-deployment-runtime.sh`.
- Run shell syntax validation for changed shell scripts.
- Run `git diff --check`.

## Open Questions

- The staging Cloudflare Tunnel token and hostname are still owner-provided.
- The staging R2 bucket/access key pair must be provided by the owner before
  real SSM parameters can be created.

## Progress

- Confirmed `main` contains the merged staging runtime deployment contract.
- Confirmed the current application still uses one datasource for Flyway and
  runtime.
- Added the staging runtime parameter runbook.
- Added local fixture validation and optional read-only AWS SSM metadata
  validation.
- Added CI coverage for the staging runtime parameter contract.
- Updated release readiness and deployment foundation documentation.

## Completion Summary

The repository now documents how to create and verify staging runtime SSM
parameters without committing or printing secret values. A new validator checks
the committed staging fixture locally and can verify live SSM parameter names
and types without decrypting values.

## Files Changed

- `.github/workflows/ci.yml`
- `docs/implementation-plan/2026-06-29/add-staging-runtime-parameter-runbook.md`
- `docs/operations/production-deployment-foundation.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/staging-runtime-parameters.md`
- `scripts/verify-staging-runtime-parameters.sh`

## Tests Run And Results

- `C:\Program Files\Git\bin\bash.exe -lc "./scripts/verify-staging-runtime-parameters.sh"`
  - Passed.
- `C:\Program Files\Git\bin\bash.exe -lc "./scripts/verify-staging-deployment-runtime.sh"`
  - Passed.
- `git diff --check`
  - Passed.

## Manual Verification Results

The runbook was reviewed to ensure all real values remain operator-supplied and
that secret parameters are marked `SecureString`. The optional AWS verifier uses
SSM `DescribeParameters` metadata and does not request decryption.

## Known Limitations

- Real staging SSM parameters have not been created.
- The staging database application/migration user has not been created.
- The Cloudflare Tunnel token, staging hostname, and staging R2 credentials are
  owner-provided and still pending.
- Production still requires separated migration and runtime database
  identities.

## Follow-up Recommendations

- Collect owner-provided staging R2 and Cloudflare values.
- Create the staging database application/migration user through a separately
  approved operational step.
- Create the real `/time-archive/staging/...` SSM parameters.
- Run `scripts/verify-staging-runtime-parameters.sh --check-aws` after
  parameter creation.
