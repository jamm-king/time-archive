# Add Staging Media Signature Smoke

## Objective

Add a repeatable staging smoke workflow that verifies deployed file signature
validation rejects uploaded objects whose bytes do not match the requested
content type.

## Scope

- Add a shell smoke script for staging media signature mismatch verification.
- Add a manually triggered GitHub Actions workflow.
- Add a workflow policy validator and wire it into CI.
- Update staging and release readiness documentation.

## Out Of Scope

- Changing file signature validation production code.
- Malware scanning.
- Media approval, public playback, thumbnail generation, or transcoding.
- Any payment-provider work.

## Relevant Files Or Modules

- `scripts/verify-staging-media-signature-smoke.sh`
- `scripts/verify-staging-media-signature-smoke-workflow.sh`
- `.github/workflows/smoke-staging-media-signature.yml`
- `.github/workflows/ci.yml`
- `docs/operations/staging-deployment.md`
- `docs/operations/cloudflare-edge-hardening.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- Keep the workflow manual only because it mutates staging state and uploads an
  object to staging R2.
- Reuse the staging admin account as the authenticated owner, matching existing
  staging media smoke workflows.
- Require the account to already own a configurable active staging range.
- Upload a deliberately invalid `image/png` payload and verify completion fails
  with `MEDIA_FILE_SIGNATURE_MISMATCH`.
- Verify the failed upload does not create a media asset.
- Do not print presigned upload URLs or credentials.

## Step-By-Step Execution Plan

1. Add this implementation plan.
2. Add the staging signature smoke shell script.
3. Add the manual GitHub Actions workflow.
4. Add the static workflow policy validator.
5. Wire the validator into CI.
6. Update release readiness and staging runbook documentation.
7. Run shell syntax and policy validations.

## Risks And Rollback Strategy

- Risk: The smoke uploads staging objects and creates upload requests.
  - Mitigation: Use a clear smoke filename and verify no media asset is created.
- Risk: The script depends on an owned staging range.
  - Mitigation: Keep range inputs configurable and default to the existing
    `[7000, 7001)` smoke range.
- Risk: The test could leak presigned URLs in logs.
  - Mitigation: The script logs only IDs and status, not upload URLs.
- Rollback: Remove the workflow, script, validator, CI wiring, and documentation
  updates.

## Verification Plan

- Run `bash -n` against both new scripts.
- Run the workflow policy validator locally.
- Confirm CI includes the new static validator.
- After merge and staging deployment, run the manual workflow from `main`.

## Open Questions

- None.

## Progress

- Created implementation plan.
- Added staging media signature smoke script.
- Added manual staging media signature GitHub Actions workflow.
- Added static workflow policy validator.
- Wired the workflow policy validator into CI.
- Updated staging deployment, Cloudflare edge hardening, and release readiness
  documentation.

## Completion Summary

The staging media signature smoke path is ready for review. The new manual
workflow verifies that staging rejects an uploaded object whose bytes do not
match the declared `image/png` content type and confirms the rejected completion
does not create a media asset.

## Files Changed

- `.github/workflows/ci.yml`
- `.github/workflows/smoke-staging-media-signature.yml`
- `scripts/verify-staging-media-signature-smoke.sh`
- `scripts/verify-staging-media-signature-smoke-workflow.sh`
- `docs/operations/staging-deployment.md`
- `docs/operations/cloudflare-edge-hardening.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-02/add-staging-media-signature-smoke.md`

## Tests Run And Results

- `bash -n scripts/verify-staging-media-signature-smoke.sh`: passed.
- `bash -n scripts/verify-staging-media-signature-smoke-workflow.sh`: passed.
- `PYTHONPATH=temp/cfn-lint ./scripts/verify-staging-media-signature-smoke-workflow.sh`: passed.
- `git diff --check`: passed.

## Manual Verification Results

The live staging workflow was not run from this branch. After merge and staging
deployment, run `Smoke staging media signature` from `main` against the staging
public HTTPS hostname.

## Known Limitations

- The workflow requires the configured staging admin account to own the selected
  active range, defaulting to `[7000, 7001)`.
- The workflow mutates staging by creating an upload request and uploading one
  rejected smoke-test object.

## Follow-Up Recommendations

- Mark file signature validation as `Ready` in the release readiness checklist
  only after the staging workflow passes against the deployed release.
