# Add Staging Presigned Upload CORS Smoke

## Objective

Add a focused staging smoke check that verifies presigned upload URLs accept
browser-origin CORS preflight and PUT requests from the deployed staging Web
origin.

## Scope

- Add a shell smoke script for staging presigned upload CORS.
- Add a manual GitHub Actions workflow for the smoke script.
- Add CI policy validation for the new manual workflow.
- Update staging deployment and release readiness documentation.

## Out Of Scope

- Browser automation with Playwright.
- R2 bucket policy changes.
- Media upload completion or admin preview verification, which are already
  covered by the staging media preview smoke workflow.
- Production R2 provisioning.

## Relevant Files Or Modules

- `scripts/verify-staging-presigned-upload-cors-smoke.sh`
- `scripts/verify-staging-presigned-upload-cors-smoke-workflow.sh`
- `.github/workflows/smoke-staging-presigned-upload-cors.yml`
- `.github/workflows/ci.yml`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/staging-deployment.md`

## Key Design Decisions

- Use a shell-based smoke before adding browser automation. It verifies the
  exact CORS contract a browser depends on: preflight response headers and the
  actual PUT response headers when an `Origin` header is present.
- Reuse the staging admin account and owned range grant because staging has no
  real payment provider yet.
- Do not complete the upload request in this smoke. The media preview smoke
  already verifies completion and preview; this smoke focuses only on presigned
  upload CORS behavior.
- Do not print presigned URLs, admin credentials, or uploaded object references.

## Step-By-Step Execution Plan

- [x] Inspect existing staging media preview smoke coverage.
- [x] Create a dedicated branch from the latest `main`.
- [x] Create this implementation plan.
- [x] Add presigned upload CORS smoke script.
- [x] Add manual staging workflow.
- [x] Add CI workflow policy validation.
- [x] Update staging deployment and readiness documentation.
- [x] Run focused local verification.
- [x] Record final completion details.

## Risks And Rollback Strategy

- Risk: The smoke creates a staging upload request and object without completing
  the upload request.
  - Mitigation: document the mutation and keep the object small and stable.
  - Rollback: revert this documentation and workflow/script change.
- Risk: Different S3-compatible providers may return allowed headers in
  different case or as `*`.
  - Mitigation: validate CORS headers case-insensitively and accept explicit
    `content-type` or wildcard allowed headers.
- Risk: CORS could pass in curl but still fail in a browser due to an untested
  browser-specific behavior.
  - Mitigation: treat this as a preflight/response-header smoke; add Playwright
    later only if needed.

## Verification Plan

- Run shell syntax checks for the new scripts.
- Run `git diff --check`.
- Run the workflow policy verifier where PyYAML is available.
- After merge, run the manual `Smoke staging presigned upload CORS` workflow
  from `main`.

## Open Questions

- None for this implementation.

## Progress

- 2026-07-01: Created branch
  `feature/staging-presigned-upload-cors-smoke`.
- 2026-07-01: Confirmed existing staging media preview smoke verifies upload
  request creation, presigned PUT upload, completion, admin moderation
  visibility, preview URL creation, and preview download byte equality.
- 2026-07-01: Added a dedicated staging presigned upload CORS smoke script.
- 2026-07-01: Added a manual staging GitHub Actions workflow and CI policy
  validation for the workflow.
- 2026-07-01: Updated staging deployment and release readiness documentation.
- 2026-07-01: Git Bash shell syntax checks passed for the new smoke script and
  workflow policy script.
- 2026-07-01: `git diff --check` passed.
- 2026-07-01: Local execution of the workflow policy verifier was skipped
  because the local Python installation does not include PyYAML. CI installs
  PyYAML through the existing CloudFormation validator requirements before
  running workflow policy checks.

## Completion Summary

The staging presigned upload CORS smoke is implemented. It logs in through the
public staging HTTPS hostname, finds the configured owned range, creates a
presigned upload request, verifies `OPTIONS` preflight CORS headers for a
browser-style `PUT` request, and verifies the actual `PUT` response includes an
allowed origin CORS header when the deployed Web origin is sent.

## Files Changed

- `.github/workflows/ci.yml`
- `.github/workflows/smoke-staging-presigned-upload-cors.yml`
- `docs/implementation-plan/2026-07-01/add-staging-presigned-upload-cors-smoke.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/staging-deployment.md`
- `scripts/verify-staging-presigned-upload-cors-smoke.sh`
- `scripts/verify-staging-presigned-upload-cors-smoke-workflow.sh`

## Tests Run And Results

- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-staging-presigned-upload-cors-smoke.sh`:
  passed.
- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-staging-presigned-upload-cors-smoke-workflow.sh`:
  passed.
- `git diff --check`: passed.

## Manual Verification Results

The deployed staging CORS smoke was not run in this branch. After merge, run the
manual `Smoke staging presigned upload CORS` workflow from `main`.

## Known Limitations

- This is a shell-based preflight and response-header smoke, not full browser
  automation.
- The smoke mutates staging by creating an upload request and uploading a small
  object, but it intentionally does not complete the upload request or create a
  media asset.
- Production R2 remains a separate production readiness item.

## Follow-Up Recommendations

- After the workflow passes in staging, update the release readiness checklist
  to mark `Presigned upload URLs` as `Ready`.
- Add browser automation only if the shell CORS smoke passes but real browser
  uploads still fail.
