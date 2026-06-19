# Add Admin Preview Flow Verification

## Objective

Add a local verification script and CI coverage for the admin original media
preview flow.

## Scope

- Add a shell-only verification script for admin media preview URLs.
- Verify purchase, ownership, upload request, MinIO upload, upload completion,
  admin authentication, preview URL creation, and preview URL download.
- Add the script to GitHub Actions.
- Document the manual verification flow.

Out of scope:

- Refactoring existing shell scripts into shared helpers.
- Browser click automation.
- Changing admin moderation behavior.
- Changing public media delivery policy.

## Relevant Files Or Modules

- `scripts/verify-local-admin-preview-flow.sh`
- `.github/workflows/ci.yml`
- `docs/manual-verification/local-admin-preview-flow.md`
- `docs/implementation-plan/2026-06-19/add-admin-preview-flow-verification.md`

## Key Design Decisions

- Keep the script self-contained, matching the existing verification script
  style.
- Use API-origin verification against the Docker Compose API service because
  the preview URL is a backend admin endpoint and does not require browser UI.
- Verify the returned presigned URL by downloading the uploaded object bytes.
- Keep shell scripts as the only verification script format.

## Step-By-Step Execution Plan

- [x] Create a dedicated branch from latest `main`.
- [x] Add this implementation plan.
- [x] Add admin preview verification script.
- [x] Add manual verification documentation.
- [x] Add CI job or CI step for the script.
- [x] Run local static checks.
- [x] Run the new script locally if Docker Compose is available.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: Script runtime increases CI duration.
  - Mitigation: Run only the API stack and use a one-second owned range.
- Risk: Preview URL download may be flaky if MinIO is not ready.
  - Mitigation: Reuse API health readiness and Compose service health checks.
- Risk: Script duplicates setup logic from other flows.
  - Mitigation: Accept duplication for now to match the current script style;
    defer helper extraction to a separate refactor.

Rollback:

- Revert the script, CI workflow change, manual verification doc, and this plan.

## Verification Plan

- `git diff --check`.
- `bash -n scripts/verify-local-admin-preview-flow.sh` if Bash is available.
- `docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d --build api`
  followed by `./scripts/verify-local-admin-preview-flow.sh` if practical.

## Open Questions

- None.

## Progress Log

- 2026-06-19: Confirmed local `main` included the documentation and admin
  preview URL PRs.
- 2026-06-19: Created `test/admin-preview-flow-verification`.
- 2026-06-19: Added implementation plan.
- 2026-06-19: Added `verify-local-admin-preview-flow.sh`.
- 2026-06-19: Added manual verification documentation for the admin preview
  flow.
- 2026-06-19: Added a GitHub Actions job for the admin preview flow.
- 2026-06-19: Verified shell syntax with Git Bash.
- 2026-06-19: Ran the Docker Compose admin preview flow successfully after
  rebuilding the API bootJar.
- 2026-06-19: Matched the new shell script executable mode to existing
  verification scripts.

## Completion Summary

Added local and CI verification for the admin original media preview flow. The
new script creates a user-owned range, uploads media through MinIO, completes
the upload, authenticates as an admin, requests an admin preview URL, downloads
the private original media through the returned presigned URL, and verifies the
downloaded bytes match the uploaded bytes.

## Files Changed

- `.github/workflows/ci.yml`
- `scripts/verify-local-admin-preview-flow.sh`
- `docs/manual-verification/local-admin-preview-flow.md`
- `docs/implementation-plan/2026-06-19/add-admin-preview-flow-verification.md`

## Tests Run And Results

- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-local-admin-preview-flow.sh`
  - Passed.
- `git diff --check`
  - Passed.
- `.\gradlew.bat bootJar --no-daemon`
  - Passed.
- `docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d --build api`
  - Passed.
- `START_SECOND=6100 END_SECOND=6101 ./scripts/verify-local-admin-preview-flow.sh`
  - Passed.
- `docker compose down`
  - Passed.

## Manual Verification Results

- Confirmed the script obtains an admin-only preview URL.
- Confirmed the preview URL downloads the uploaded original object from MinIO.
- Confirmed the downloaded bytes match the uploaded bytes.

## Known Limitations

- The script duplicates setup helpers from existing verification scripts.
- The default range may be unavailable in a reused local database; override
  `START_SECOND` and `END_SECOND` when needed.

## Follow-Up Recommendations

- Consider extracting shared shell helpers once the verification suite grows
  further.
