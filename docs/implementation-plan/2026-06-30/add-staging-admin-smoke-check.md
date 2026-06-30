# Add Staging Admin Smoke Check

## Objective

Add a repeatable staging admin authorization smoke check that verifies admin
API access control through the deployed HTTPS hostname.

## Scope

- Add a shell script for staging admin authorization smoke verification.
- Add a manually triggered GitHub Actions workflow that reads staging
  environment secrets.
- Add static validation for the workflow contract.
- Update operations documentation and release readiness status.

Out of scope:

- Media upload, approval, or preview flows.
- Creating or rotating the staging admin password.
- Adding production admin provisioning.
- Cleaning up disposable non-admin smoke-test users.

## Relevant Files Or Modules

- `scripts/verify-staging-admin-smoke.sh`
- `scripts/verify-staging-admin-smoke-workflow.sh`
- `.github/workflows/smoke-staging-admin.yml`
- `.github/workflows/ci.yml`
- `docs/operations/staging-deployment.md`
- `docs/operations/staging-admin-provisioning.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- The workflow is manual only because it depends on live staging and reads admin
  credentials from the `staging` GitHub Environment.
- Admin credentials are supplied only through `STAGING_ADMIN_EMAIL` and
  `STAGING_ADMIN_PASSWORD` environment secrets.
- The script verifies unauthenticated `401`, normal-user `403`, and admin `200`
  behavior for the moderation list endpoint.
- A disposable non-admin user is created to verify the `403` path.
- The script does not mutate media state.

## Step-by-step Execution Plan

1. Inspect current admin API path and local admin verification scripts.
2. Add the implementation plan.
3. Add the staging admin smoke script.
4. Add the manual GitHub Actions workflow.
5. Add CI static validation for the workflow and script.
6. Update operations documentation.
7. Run local static validation.

## Risks And Rollback Strategy

- Risk: leaking admin credentials in logs. Mitigation: read credentials from
  environment secrets and never print request bodies or secret values.
- Risk: accumulating disposable users. Mitigation: use a clear
  `staging-admin-smoke-...@example.com` prefix and document cleanup as a data
  retention task.
- Risk: accidentally expanding admin API surface in smoke tests. Mitigation:
  check only the read-only moderation list endpoint.
- Rollback: remove the workflow and scripts without changing runtime behavior.

## Verification Plan

- Run shell syntax validation.
- Run `scripts/verify-staging-admin-smoke-workflow.sh`.
- Run `git diff --check`.
- After merge, run the manual `Smoke staging admin` workflow with staging
  environment secrets.

## Open Questions

- None.

## Progress

- Created the dedicated branch from latest `main`.
- Added the staging admin smoke shell script.
- Added the manual GitHub Actions staging admin smoke workflow.
- Added CI static validation for the workflow contract.
- Updated operations documentation with admin smoke verification guidance.

## Completion Summary

Added a manual staging admin authorization smoke check. The script verifies the
deployed HTTPS admin authorization boundary by checking unauthenticated
rejection, disposable non-admin user rejection, and configured admin user access
to the read-only moderation list endpoint.

The workflow reads `STAGING_ADMIN_EMAIL` and `STAGING_ADMIN_PASSWORD` only from
the `staging` GitHub Environment secrets. It does not request AWS credentials,
does not use OIDC, and does not mutate media state.

## Files Changed

- `.github/workflows/smoke-staging-admin.yml`
- `.github/workflows/ci.yml`
- `scripts/verify-staging-admin-smoke.sh`
- `scripts/verify-staging-admin-smoke-workflow.sh`
- `docs/operations/staging-admin-provisioning.md`
- `docs/operations/staging-deployment.md`
- `docs/operations/release-readiness-checklist.md`

## Tests Run And Results

- `bash -n scripts/verify-staging-admin-smoke.sh`: passed.
- `bash -n scripts/verify-staging-admin-smoke-workflow.sh`: passed.
- `scripts/verify-staging-admin-smoke-workflow.sh`: passed with local
  `PYTHONPATH=temp/cfn-lint`.
- `git diff --check`: passed.

## Manual Verification Results

The actual staging admin smoke was not run locally because the admin password is
stored only as a GitHub `staging` environment secret. Run the manual
`Smoke staging admin` workflow after merge.

## Known Limitations

- The script creates one disposable non-admin user per run.
- The smoke check covers only read-only moderation-list authorization. Media
  preview, approval, rejection, and hiding remain separate staging verification
  tasks.

## Follow-up Recommendations

- After merge, run `Smoke staging admin` from `main`.
- If it passes, update release readiness to record the staging admin
  authorization verification result.
