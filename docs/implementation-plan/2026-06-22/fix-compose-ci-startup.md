# Fix Compose CI Startup

## Objective

Restore all Compose-based GitHub Actions checks after local secret
externalization by fixing MinIO bucket initialization and tolerating bounded,
transient container registry failures during stack startup.

## Scope

- Correct shell quoting in the `minio-init` Compose service.
- Add a reusable shell script that retries only Compose stack startup.
- Use the script in every Compose-based CI job.
- Verify the MinIO initialization command, script behavior, Compose rendering,
  and affected local flows.

Out of scope:

- Retrying verification flow failures.
- Changing object storage credentials or storage architecture.
- Changing container image versions.
- Refactoring the GitHub Actions job matrix.

## Relevant Files Or Modules

- `docker-compose.yml`
- `.github/workflows/ci.yml`
- `scripts/start-local-stack.sh`
- `docs/implementation-plan/2026-06-22/fix-compose-ci-startup.md`

## Key Design Decisions

- Use normal shell quotes in the YAML literal block while retaining `$$` so
  Docker Compose defers credential expansion to the container shell.
- Retry `docker compose up` at most three times with a short increasing delay.
- Forward all script arguments directly to Docker Compose so API-only and full
  stack jobs keep their existing behavior.
- Do not hide the final Compose failure or retry application-level flow
  assertions.

## Step-By-Step Execution Plan

- [x] Inspect the failed workflow run and job logs through the GitHub plugin.
- [x] Identify the MinIO quoting defect and transient registry failures.
- [x] Add this implementation plan.
- [x] Correct the MinIO initialization command.
- [x] Add and apply the bounded Compose startup retry script.
- [x] Run shell, Compose, startup, and affected flow verification.
- [x] Record completion details and residual risks.

## Risks And Rollback Strategy

- Risk: Retry behavior could mask deterministic configuration failures.
  - Mitigation: Use only three attempts, print each failure, and return the
    final non-zero status.
- Risk: Incorrect dollar escaping could expand credentials on the host.
  - Mitigation: Retain Compose `$$` escaping and inspect rendered Compose
    configuration without printing environment values.
- Risk: Local MinIO data can hide bucket initialization defects.
  - Mitigation: Inspect `minio-init` logs and run media flows in an isolated CI
    environment after push.

Rollback:

- Revert the workflow script integration and restore direct Compose commands.
- Restore the previous init command if necessary. No database or object data
  migration is involved.

## Verification Plan

- Run `bash -n` for the new script and all existing verification scripts.
- Validate base and CI Compose configurations with the example env file.
- Confirm rendered init commands contain shell quotes without backslashes.
- Recreate `minio-init` locally and inspect its exit code and logs.
- Run the affected media upload, public timeline, admin preview, and web checks
  when the local environment permits.
- Run `git diff --check`.

## Open Questions

- None.

## Progress Log

- 2026-06-22: Workflow run `27948455941` showed two startup failures caused by
  Docker Hub manifest responses and three media flows failing with HTTP 404.
- 2026-06-22: MinIO logs confirmed that escaped quote characters were passed as
  part of the access key, preventing reliable bucket initialization.
- 2026-06-22: Corrected runtime shell quoting and confirmed `minio-init`
  authenticated and created the bucket with exit code 0.
- 2026-06-22: Added a bounded Compose command retry and applied it to all seven
  Compose-based CI startup steps.
- 2026-06-22: Re-ran all five affected local flows successfully with MinIO,
  then restored the user's R2-overlay stack.

## Completion Summary

MinIO initialization now passes credentials without literal quote characters,
so fresh CI runners create the local media bucket correctly. Compose-based CI
jobs use one shared startup script that retries transient image registry or
build failures up to three times while preserving a final failure.

## Files Changed

- Updated `docker-compose.yml` MinIO initialization quoting.
- Added `scripts/start-local-stack.sh`.
- Updated all Compose startup steps in `.github/workflows/ci.yml`.
- Added this implementation plan.

## Tests Run And Results

- `bash -n scripts/*.sh`: passed.
- Base plus CI Compose configuration with `.env.local.example`: passed.
- Base plus R2 Compose configuration with both example env files: passed.
- Rendered `minio-init` command inspection: passed with no escaped quote
  characters.
- Retry simulation: passed for two failures followed by success.
- Retry exhaustion simulation: returned non-zero after the configured limit.
- Invalid retry configuration: returned exit code 2 before invoking Compose.
- `git diff --check`: passed.

## Manual Verification Results

- Recreated MinIO and `minio-init` without deleting the existing volume.
- `minio-init` logged successful alias creation and bucket creation, then exited
  with code 0.
- Local media upload flow: passed.
- Local public timeline flow: passed.
- Local admin preview flow: passed.
- Local web purchase flow: passed.
- Local web purchase-upload flow: passed.
- R2-overlay API health, Web, and timeline proxy returned HTTP 200 after the
  local stack was restored.

## Known Limitations

- The two Docker Hub manifest failures were external and cannot be eliminated
  by application code. Three startup attempts reduce but do not remove this
  risk.
- The user's current `.env.local` MinIO root and API S3 credential pairs do not
  match. Verification used process-only overrides without reading values into
  logs. The R2 overlay remains functional because it overrides the API storage
  credentials, but base MinIO uploads require matching pairs.
- A fresh GitHub-hosted runner remains the authoritative verification that no
  persisted local MinIO state influenced startup.

## Follow-Up Recommendations

- Align the two MinIO/API credential pairs in `.env.local` before using the
  base MinIO stack.
- Re-run the PR checks after committing and pushing this fix.
