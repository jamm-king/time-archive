# Add Staging Media Preview Smoke Check

## Objective

Add a repeatable staging media smoke check that verifies owned range media
upload, R2 presigned PUT, upload completion, admin moderation-list visibility,
admin preview URL creation, and preview download through the deployed HTTPS
hostname.

## Scope

- Add a shell script for staging media upload and admin preview smoke
  verification.
- Add a manually triggered GitHub Actions workflow that reads staging
  environment secrets.
- Add static validation for the workflow contract.
- Update operations documentation and release readiness status.

Out of scope:

- Admin approval or public timeline playback.
- Creating owned ranges inside the smoke workflow.
- Real payment provider integration.
- Cleaning up uploaded smoke media.

## Relevant Files Or Modules

- `scripts/verify-staging-media-preview-smoke.sh`
- `scripts/verify-staging-media-preview-smoke-workflow.sh`
- `.github/workflows/smoke-staging-media-preview.yml`
- `.github/workflows/ci.yml`
- `docs/operations/staging-deployment.md`
- `docs/operations/staging-owned-range-grants.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- The workflow is manual only because it mutates staging by uploading media.
- The target user must already own a staging range, usually through
  `ADMIN_GRANT`.
- The workflow reads user and admin credentials only from the `staging` GitHub
  Environment secrets.
- The smoke check stops at admin preview download and does not approve, reject,
  hide, or publish media.
- The script picks the configured ownership range by exact start/end seconds.

## Step-by-step Execution Plan

1. Grant `[7000, 7001)` to the configured staging admin account.
2. Add this implementation plan.
3. Add the staging media preview smoke script.
4. Add the manual GitHub Actions workflow.
5. Add CI static validation for the workflow and script.
6. Update operations documentation.
7. Run local static validation.

## Risks And Rollback Strategy

- Risk: accumulating uploaded smoke media in staging. Mitigation: use a clear
  filename prefix and document cleanup as a later data operation.
- Risk: leaking credentials. Mitigation: read credentials only from environment
  secrets and never print request bodies or secret values.
- Risk: modifying public timeline state. Mitigation: do not approve or publish
  media in this smoke check.
- Rollback: remove the workflow and scripts; uploaded smoke assets can be
  rejected, hidden, or deleted through a later reviewed cleanup operation.

## Verification Plan

- Run shell syntax validation.
- Run `scripts/verify-staging-media-preview-smoke-workflow.sh`.
- Run `git diff --check`.
- After merge, run the manual `Smoke staging media preview` workflow with
  staging environment secrets.

## Open Questions

- None.

## Progress

- Granted staging owned range `[7000, 7001)` to the configured staging admin
  account with `ADMIN_GRANT`.
- Created the dedicated branch from latest `main`.
- Added the staging media preview smoke script.
- Added the manual GitHub Actions workflow.
- Added CI static workflow validation.
- Updated staging owned-range and release-readiness documentation.
- Updated the staging deployment runbook with media preview smoke instructions.

## Completion Summary

Added a manual staging media preview smoke path that verifies the deployed
owned-range upload flow through the public HTTPS hostname, uploads an object
through a presigned PUT URL, completes the upload, confirms admin moderation
list visibility, and downloads the original through an admin preview presigned
GET URL.

## Files Changed

- `.github/workflows/ci.yml`
- `.github/workflows/smoke-staging-media-preview.yml`
- `scripts/verify-staging-media-preview-smoke.sh`
- `scripts/verify-staging-media-preview-smoke-workflow.sh`
- `docs/operations/staging-deployment.md`
- `docs/operations/staging-owned-range-grants.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-06-30/add-staging-media-preview-smoke-check.md`

## Tests Run And Results

- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-staging-media-preview-smoke.sh`: passed.
- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-staging-media-preview-smoke-workflow.sh`: passed.
- `PYTHONPATH=D:\develop\time-archive\temp\cfn-lint C:\Program Files\Git\bin\bash.exe -lc './scripts/verify-staging-media-preview-smoke-workflow.sh'`: passed.
- `git diff --check`: passed.

## Manual Verification Results

- The actual staging owned range `[7000, 7001)` was granted to the configured
  staging admin account before this implementation.
- The new mutating staging smoke workflow was not executed locally because it
  requires staging GitHub Environment secrets and uploads a real staging object.

## Known Limitations

- The smoke workflow leaves the uploaded `UPLOADED` media asset in staging.
- The workflow uses the staging admin account as both the range owner and admin
  reviewer until a separate staging media-owner account is introduced.

## Follow-up Recommendations

- Run `Smoke staging media preview` from GitHub Actions after merge.
- Add an approved staging media cleanup process after data-retention policy is
  finalized.
