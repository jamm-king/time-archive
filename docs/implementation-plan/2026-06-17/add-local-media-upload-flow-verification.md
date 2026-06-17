# Add Local Media Upload Flow Verification

## Objective

Add an end-to-end local verification script for the owned range media upload flow.

The script should verify the local flow from purchase completion through
presigned upload request creation, object upload to MinIO, upload completion
verification, `MediaAsset` creation, and idempotent completion retry.

## Scope

- Add a shell script that can run locally and in GitHub Actions.
- Reuse the development-stage fake purchase flow to create an ownership record.
- Create a media upload request for the owned range.
- Upload a small local file to the returned presigned URL.
- Complete the upload request and assert that a `MediaAsset` is created.
- Retry completion and assert idempotency.
- Wire the script into GitHub Actions.
- Add manual verification documentation.

Out of scope:

- Windows PowerShell variant.
- Real Cloudflare R2 verification.
- File signature inspection.
- Admin moderation verification.

## Relevant Files Or Modules

- `scripts/verify-local-media-upload-flow.sh`
- `.github/workflows/ci.yml`
- `docs/manual-verification/local-media-upload-flow.md`
- `docs/implementation-plan/2026-06-17/add-local-media-upload-flow-verification.md`
- `docker-compose.yml`
- `src/main/resources/application.yml`
- `src/main/kotlin/com/timearchive/configuration/StorageConfiguration.kt`

## Key Design Decisions

- The script uses `curl` and Python JSON parsing, matching the existing purchase
  flow script and avoiding a `jq` dependency.
- The script creates its own purchased range to avoid depending on pre-existing
  database state.
- The script uploads a small generated PNG-like byte payload to MinIO through the
  presigned URL.
- The script verifies idempotent completion by calling the completion endpoint
  twice.
- The GitHub Actions job starts the full Docker Compose stack because the flow
  requires PostgreSQL, the API, and MinIO.
- S3-compatible storage uses separate internal and presigned URL endpoints so
  the API container can reach MinIO through the Docker network while clients can
  upload through `localhost`.

## Step-By-Step Execution Plan

- [x] Create a dedicated work branch.
- [x] Create this implementation plan.
- [x] Add local media upload flow shell script.
- [x] Add manual verification documentation.
- [x] Add GitHub Actions job.
- [x] Run local script-level checks where feasible.
- [x] Run automated verification.
- [x] Update this plan with completion details.
- [ ] Commit and push the branch.

## Risks And Rollback Strategy

- Risk: Presigned URL headers may vary across SDK/provider versions.
  - Mitigation: Use the known content type header explicitly and let `curl`
    provide content length from the uploaded file.
- Risk: CI can become slower due to an additional Docker Compose job.
  - Mitigation: Keep the uploaded file small and reuse the same full local stack
    pattern already used for purchase flow verification.
- Risk: The script may fail if the selected range is already owned in a reused
  local database.
  - Mitigation: Expose `START_SECOND` and `END_SECOND` overrides.

Rollback:

- Remove the script, manual verification document, and CI job.

## Verification Plan

- Run `bash -n scripts/verify-local-media-upload-flow.sh` if bash is available.
- Run `.\gradlew.bat test --max-workers=2`.
- Run `.\gradlew.bat build`.
- Run `git diff --check`.
- Optionally run the script against `docker compose up -d --build` if local
  Docker execution is available and time permits.

## Open Questions

- None for this implementation slice.

## Progress

- 2026-06-17: Created `codex/add-local-media-upload-flow-verification` from
  the upload completion branch.
- 2026-06-17: Plan created.
- 2026-06-17: Added the local media upload flow verification script, manual
  verification documentation, and GitHub Actions job.
- 2026-06-17: Fixed local Docker S3-compatible configuration by separating the
  API container's internal S3 endpoint from the client-facing presigned URL
  endpoint.
- 2026-06-17: Verified the script against the local Docker Compose stack using
  Git Bash.

## Completion Summary

Implemented local end-to-end media upload flow verification.

The script now creates a local purchase and ownership record, requests a
presigned media upload URL, uploads a small file to MinIO, completes the upload,
verifies that a media asset is created, retries completion to prove idempotency,
and confirms the media asset appears in the owned range media list.

## Files Changed

- `.github/workflows/ci.yml`
- `README.md`
- `docker-compose.yml`
- `docs/implementation-plan/2026-06-17/add-local-media-upload-flow-verification.md`
- `docs/manual-verification/local-media-upload-flow.md`
- `scripts/verify-local-media-upload-flow.sh`
- `src/main/kotlin/com/timearchive/configuration/StorageConfiguration.kt`
- `src/main/resources/application.yml`

## Tests Run And Results

- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-local-media-upload-flow.sh` passed.
- `.\gradlew.bat test --max-workers=2` passed.
- `.\gradlew.bat build` passed.
- `docker compose config` passed.
- `git diff --check` passed.
- `docker compose up -d --build` passed.
- `scripts/verify-local-media-upload-flow.sh` passed against the local Docker
  Compose stack via Git Bash with range `[2002, 2003)`.

## Manual Verification Results

- Confirmed API health before running the flow.
- Confirmed presigned upload to local MinIO succeeds.
- Confirmed upload completion creates an `UPLOADED` `MediaAsset`.
- Confirmed duplicate completion returns the same media asset with
  `alreadyCompleted=true`.

## Known Limitations

- The script is shell-only; no PowerShell equivalent was added.
- The script uses development-stage `X-User-Id` identity.
- The uploaded test file is stable test bytes with `image/png` content type; this
  does not validate real image decoding.
- Existing local database state can make the default range unavailable; override
  `START_SECOND` and `END_SECOND` when needed.

## Follow-Up Recommendations

- Proceed with admin moderation API.
- Add Cloudflare R2 environment verification after the local MinIO flow remains
  stable through moderation.
