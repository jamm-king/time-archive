# Add Staging Media Duration Smoke

## Objective

Add a repeatable staging smoke workflow that verifies the deployed video
duration validation behavior through the public HTTPS hostname and real
S3-compatible object upload path.

## Scope

- Add a shell smoke script for staging media duration validation.
- Add a manually triggered GitHub Actions workflow.
- Add a workflow policy validator and wire it into CI.
- Update release readiness documentation.

## Out Of Scope

- Changing video duration validation production code.
- Real payment provider work.
- Media approval, public playback, thumbnail generation, or transcoding.
- Malware scanning or file signature validation.

## Relevant Files Or Modules

- `scripts/verify-staging-media-duration-smoke.sh`
- `scripts/verify-staging-media-duration-smoke-workflow.sh`
- `.github/workflows/smoke-staging-media-duration.yml`
- `.github/workflows/ci.yml`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- Keep the workflow manual only because it mutates staging state and uploads
  objects to staging R2.
- Reuse the staging admin account as the authenticated owner, matching the
  current media preview smoke model.
- Require the account to already own a configurable active staging range.
- Generate minimal MP4 files in the script instead of committing binary assets.
- Verify one short video upload completes and records `durationMs`.
- Verify one over-duration video upload fails during completion with
  `MEDIA_DURATION_EXCEEDS_OWNED_RANGE`.
- Verify the failed over-duration upload does not create a new owned media
  asset in the owner media list.
- Do not print presigned upload URLs or credentials.

## Step-By-Step Execution Plan

1. Add the implementation plan.
2. Add the staging duration smoke shell script.
3. Add the manual GitHub Actions workflow.
4. Add the static workflow policy validator.
5. Wire the validator into CI.
6. Update release readiness documentation.
7. Run shell syntax and policy validations.

## Risks And Rollback Strategy

- Risk: The smoke uploads staging objects and creates one successful media
  asset per run.
  - Mitigation: Use a clearly named smoke filename prefix and document the
    staging data side effect.
- Risk: The script depends on an owned staging range.
  - Mitigation: Keep range inputs configurable and default to the existing
    `[7000, 7001)` smoke range.
- Risk: The test could leak presigned URLs in logs.
  - Mitigation: The script logs only IDs and status, not upload URLs.
- Rollback: Remove the workflow, script, validator, CI wiring, and release
  readiness documentation update.

## Verification Plan

- Run `bash -n` against both new scripts.
- Run the workflow policy validator locally.
- Confirm CI includes the new static validator.
- After merge and staging deployment, run the manual workflow from `main`.

## Open Questions

- None.

## Progress

- Created implementation plan.
- Added the staging media duration smoke shell script.
- Added the manual `Smoke staging media duration` GitHub Actions workflow.
- Added the staging media duration workflow policy validator.
- Wired the policy validator into CI.
- Updated staging deployment, Cloudflare edge hardening, and release readiness
  documentation.

## Completion Summary

Added a manual staging smoke path for deployed video duration validation. The
new workflow uploads a generated short `video/mp4`, verifies completion and
returned `durationMs`, then uploads a generated over-duration `video/mp4` and
verifies completion is rejected with `MEDIA_DURATION_EXCEEDS_OWNED_RANGE`
without creating an additional media asset.

## Files Changed

- `.github/workflows/ci.yml`
- `.github/workflows/smoke-staging-media-duration.yml`
- `scripts/verify-staging-media-duration-smoke.sh`
- `scripts/verify-staging-media-duration-smoke-workflow.sh`
- `docs/operations/staging-deployment.md`
- `docs/operations/cloudflare-edge-hardening.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-02/add-staging-media-duration-smoke.md`

## Tests Run And Results

- `git diff --check`: passed.
- `bash -n scripts/verify-staging-media-duration-smoke.sh`: passed.
- `bash -n scripts/verify-staging-media-duration-smoke-workflow.sh`: passed.
- `PYTHONPATH=D:\develop\time-archive\temp\cfn-lint ./scripts/verify-staging-media-duration-smoke-workflow.sh`:
  passed.

## Manual Verification Results

- The live staging smoke workflow was not run in this implementation branch.
  Run `Smoke staging media duration` from `main` after merge and staging
  deployment.

## Known Limitations

- The workflow mutates staging by uploading two smoke objects and creating one
  successful `UPLOADED` media asset per run.
- The smoke assumes the configured staging account already owns the target
  active range.

## Follow-Up Recommendations

- After the manual workflow passes against staging, update release readiness
  from `Needs verification` to `Ready` for video duration validation.
