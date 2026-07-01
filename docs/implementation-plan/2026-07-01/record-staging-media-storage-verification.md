# Record Staging Media Storage Verification

## Objective

Record the verified staging media upload and admin preview path in the release
readiness documentation without overstating unverified browser CORS behavior.

## Scope

- Review the existing staging media preview smoke workflow and script coverage.
- Update the release readiness checklist for staging media upload/admin preview.
- Clarify the remaining presigned upload URL verification gap.
- Update staging deployment documentation with the successful smoke result.

## Out Of Scope

- New API, frontend, or infrastructure changes.
- Browser-based CORS automation.
- Production Cloudflare R2 provisioning.
- Media signature validation, malware scanning, transcoding, or thumbnails.

## Relevant Files Or Modules

- `scripts/verify-staging-media-preview-smoke.sh`
- `.github/workflows/smoke-staging-media-preview.yml`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/staging-deployment.md`
- `docs/implementation-plan/2026-07-01/record-staging-media-storage-verification.md`

## Key Design Decisions

- Mark the staging media upload and admin preview path as `Ready` because the
  manual smoke workflow passed in staging and verifies the deployed path through
  the public HTTPS hostname.
- Keep presigned upload URL browser CORS as `Needs verification` because the
  current smoke workflow uses `curl` for the object PUT and does not execute a
  browser-origin CORS preflight or browser PUT.
- Keep production R2 as blocked for production because production buckets,
  credentials, and storage policy are intentionally separate from staging.

## Step-By-Step Execution Plan

- [x] Inspect current release readiness storage rows.
- [x] Inspect staging media preview smoke script coverage.
- [x] Create this implementation plan.
- [x] Update release readiness storage rows.
- [x] Update staging deployment documentation.
- [x] Run documentation verification.
- [x] Record final completion details.

## Risks And Rollback Strategy

- Risk: Documentation could claim browser CORS is verified when only curl-based
  upload is verified.
  - Mitigation: keep the CORS-specific gate under `Needs verification`.
  - Rollback: revert this documentation-only change.
- Risk: A future reader could confuse staging R2 verification with production
  R2 readiness.
  - Mitigation: keep production R2 explicitly blocked for production.

## Verification Plan

- Run `git diff --check`.
- Review the changed documentation for consistency with existing smoke script
  behavior.

## Open Questions

- None for this documentation update.

## Progress

- 2026-07-01: Created branch `docs/staging-media-storage-verification`.
- 2026-07-01: Confirmed the staging media preview smoke script verifies admin
  login, active owned range lookup, upload request creation, presigned PUT,
  upload completion, admin moderation list visibility, admin preview URL
  creation, preview download, and byte equality with the uploaded object.
- 2026-07-01: Confirmed the workflow uses the public HTTPS hostname, staging
  environment, and only the reviewed admin credentials.
- 2026-07-01: Updated the release readiness checklist to mark staging media
  upload/admin preview as ready while keeping browser-origin CORS verification
  as a remaining presigned upload URL gate.
- 2026-07-01: `git diff --check` passed.

## Completion Summary

The staging media/storage verification result is now recorded in the release
readiness documentation. The documentation marks the staging media upload and
admin preview path as ready based on the successful manual staging smoke
workflow, while preserving browser-origin CORS as an explicit remaining
verification gate for presigned upload URLs.

## Files Changed

- `docs/implementation-plan/2026-07-01/record-staging-media-storage-verification.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/staging-deployment.md`

## Tests Run And Results

- `git diff --check`: passed.

## Manual Verification Results

The user confirmed the manual `Smoke staging media preview` workflow had
passed. The existing workflow verifies the deployed public HTTPS path for owned
media upload, object upload through a presigned PUT URL, upload completion,
admin moderation-list visibility, admin preview URL creation, and preview
download byte equality.

## Known Limitations

- Browser-origin CORS behavior for direct object PUT uploads is still not
  verified by the current smoke workflow.
- Production Cloudflare R2 remains blocked for production until a separate
  production bucket, credentials, policy, and verification plan are completed.

## Follow-Up Recommendations

- Add a focused staging CORS/preflight verification path for presigned uploads.
- Keep production R2 verification separate from staging media smoke results.
