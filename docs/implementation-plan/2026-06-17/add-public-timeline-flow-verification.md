# Add Public Timeline Flow Verification

## Objective

Add an end-to-end local verification script for the MVP public timeline flow.

The script should prove that a purchased range can receive uploaded media,
that an admin can approve the media, and that the approved media appears through
the public timeline read API.

## Scope

- Add a shell-only verification script for the public timeline flow.
- Cover:
  - API health
  - range availability
  - purchase reservation
  - fake payment completion
  - ownership creation
  - media upload request creation
  - MinIO object upload
  - upload completion and idempotency
  - admin approval
  - public timeline read
- Add the script to GitHub Actions.
- Add manual verification documentation.
- Update README and operations documentation.

## Out of Scope

- Browser or frontend verification.
- Real media transcoding or thumbnail generation.
- Real admin authentication.
- Real payment provider integration.
- Public timeline cache or manifest invalidation.

## Relevant Files or Modules

- `scripts/verify-local-public-timeline-flow.sh`
- `.github/workflows/ci.yml`
- `docs/manual-verification/local-public-timeline-flow.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `README.md`

## Key Design Decisions

- Keep verification scripts shell-only for consistency with the repository's
  current scripting policy.
- Keep the public timeline script separate from the media upload script so CI
  can identify whether upload verification or public timeline exposure failed.
- Use the development-stage `X-Admin-Id` header for admin approval.
- Use the uploaded object URL as the approved media URL in local verification.
  This is acceptable for local E2E coverage because production media processing
  and derived asset generation are not part of the current MVP backend scope.
- Assert that public timeline output contains the approved media segment and
  does not expose owner identity, original upload URL, or moderation status.

## Step-by-Step Execution Plan

- [x] Update `main` and create a dedicated feature branch.
- [x] Create this implementation plan.
- [x] Add public timeline verification script.
- [x] Add manual verification documentation.
- [x] Add GitHub Actions job.
- [x] Update README and CI/CD documentation.
- [x] Run shell syntax checks.
- [x] Run relevant tests and build checks.
- [x] Run local Docker E2E verification if the local Docker stack is available.
- [x] Commit and push the branch.

## Risks and Rollback Strategy

- Risk: The CI job may be slower because it performs purchase, upload,
  approval, and public read verification in one flow.
  - Mitigation: Use a one-second range and a tiny deterministic upload payload.
- Risk: Local approval uses the original object URL as the approved URL.
  - Mitigation: Document this as development-only behavior.
- Rollback: Revert the script, CI job, and documentation changes. No schema or
  production behavior changes are expected.

## Verification Plan

- `bash -n scripts/verify-local-public-timeline-flow.sh`
- `.\gradlew.bat test --max-workers=2`
- `.\gradlew.bat build`
- `git diff --check`
- If Docker is available:
  - `docker compose up -d --build`
  - `START_SECOND=3000 END_SECOND=3001 ./scripts/verify-local-public-timeline-flow.sh`
  - `docker compose down`

## Open Questions

- Should the public timeline script eventually verify hidden media removal after
  the first player workflow is stable?

## Progress Log

- 2026-06-17: Updated `main` from `origin/main`.
- 2026-06-17: Created `codex/add-public-timeline-flow-verification`.
- 2026-06-17: Created implementation plan.
- 2026-06-17: Added `scripts/verify-local-public-timeline-flow.sh`.
- 2026-06-17: Added manual verification documentation and CI job.
- 2026-06-17: Updated README and CI/CD testing strategy.
- 2026-06-17: Ran shell syntax checks and `git diff --check`.
- 2026-06-17: Ran `.\gradlew.bat test --max-workers=2` and
  `.\gradlew.bat build` successfully.
- 2026-06-17: Ran local Docker E2E verification successfully with
  `START_SECOND=3200 END_SECOND=3201`.

## Completion Summary

Added a shell-only public timeline E2E verification script.

The script verifies the MVP path from purchase to public playback:

- Purchase reservation
- Fake payment completion
- Ownership creation
- Media upload request creation
- MinIO presigned upload
- Upload completion
- Public timeline exclusion before approval
- Admin approval
- Public timeline inclusion after approval
- Public response privacy checks

The script is wired into GitHub Actions as a separate `Local public timeline
flow` job.

## Files Changed

- `.github/workflows/ci.yml`
- `README.md`
- `docs/implementation-plan/2026-06-17/add-public-timeline-flow-verification.md`
- `docs/manual-verification/local-public-timeline-flow.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `scripts/verify-local-public-timeline-flow.sh`

## Tests Run and Results

- `bash -n scripts/verify-local-public-timeline-flow.sh` - passed
- `bash -n scripts/verify-local-media-upload-flow.sh` - passed
- `bash -n scripts/verify-local-purchase-flow.sh` - passed
- `git diff --check` - passed
- `.\gradlew.bat test --max-workers=2` - passed
- `.\gradlew.bat build` - passed
- `docker compose up -d --build` - passed after rerun with a longer timeout
- `START_SECOND=3200 END_SECOND=3201 ./scripts/verify-local-public-timeline-flow.sh` - passed
- `docker compose down` - passed

## Manual Verification Results

The local public timeline flow returned:

```text
[verify-timeline] Local public timeline flow verification passed
```

The script verified that unapproved media is hidden from `GET /api/timeline`
and that approved media appears after admin approval.

## Known Limitations

- The script uses development-stage `X-Admin-Id` identity.
- The script approves the uploaded local object URL directly as the public media
  URL because media processing and derived asset publishing are not implemented
  yet.
- The script does not verify hide behavior. That can be added after the first
  frontend player flow is stable.

## Follow-up Recommendations

- Add hide-to-public-timeline-removal verification after frontend playback work.
- Consider extracting shared shell helpers if more E2E scripts are added.
