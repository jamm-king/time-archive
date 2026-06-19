# Validate Approved Storage References

## Objective

Reject invalid approved media storage references during admin approval so public
timeline reads do not fail later while generating presigned playback URLs.

## Scope

- Validate `approvedFileUrl` during admin media approval.
- Validate `thumbnailUrl` during admin media approval when it is present.
- Keep validation behind the existing media object storage port.
- Return a client error for invalid storage references.
- Add focused tests for valid and invalid approval inputs.

Out of scope:

- Cloudflare R2 production setup.
- Media processing or derived approved media generation.
- Object existence checks during approval.
- Public API shape changes.

## Relevant Files Or Modules

- `apps/api/src/main/kotlin/com/timearchive/application/ApproveMediaAsset.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/port/MediaObjectStoragePort.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/storage/S3CompatibleMediaObjectStorageAdapter.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/AdminMediaModerationTest.kt`
- `docs/api/openapi.yaml`

## Key Design Decisions

- Treat approved media URLs as private storage references, not arbitrary public
  URLs.
- Validate storage ownership through `MediaObjectStoragePort` so the application
  layer does not depend on S3-compatible URL details.
- Check only URL ownership in this task. Object existence and media processing
  remain separate concerns.
- Preserve the admin approval request and response shape.

## Step-By-Step Execution Plan

- [x] Create a dedicated feature branch from latest `main`.
- [x] Add this implementation plan.
- [x] Add storage reference validation to `MediaObjectStoragePort`.
- [x] Implement validation in the S3-compatible adapter.
- [x] Apply validation in `ApproveMediaAsset`.
- [x] Map invalid approved storage references to a stable 400 response.
- [x] Add focused tests.
- [x] Update OpenAPI/docs if behavior needs documenting.
- [x] Run relevant tests and checks.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: Existing admin flows may approve external URLs in local databases.
  - Mitigation: This behavior is intentionally rejected because public timeline
    playback now depends on private storage presigning.
- Risk: The validation may be too strict if storage base URL changes.
  - Mitigation: Use the configured storage base URL through the existing
    adapter; R2 can be introduced by configuration.

Rollback:

- Remove the storage reference validation calls from `ApproveMediaAsset` and
  remove the port method.

## Verification Plan

- `apps/api`: `.\gradlew.bat test --tests com.timearchive.application.AdminMediaModerationTest`
- `apps/api`: `.\gradlew.bat test --tests com.timearchive.adapter.inbound.rest.AdminMediaModerationControllerTest`
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-openapi.sh`
- `git diff --check`

## Open Questions

- None.

## Progress Log

- 2026-06-19: Confirmed local `main` is up to date.
- 2026-06-19: Created `feature/validate-approved-storage-references`.
- 2026-06-19: Added implementation plan.
- 2026-06-19: Added managed storage URL validation to the media object storage
  port and S3-compatible adapter.
- 2026-06-19: Updated admin approval to reject unmanaged approved media and
  thumbnail references before state transition and audit logging.
- 2026-06-19: Added API error mapping, tests, OpenAPI notes, and architecture
  documentation.
- 2026-06-19: Ran targeted tests, full backend tests, OpenAPI validation, and
  whitespace checks successfully.

## Completion Summary

Admin approval now validates that `approvedFileUrl` and optional `thumbnailUrl`
belong to the configured object storage base URL before a media asset can move
to `APPROVED`. Invalid references return `400 INVALID_MEDIA_STORAGE_REFERENCE`
instead of being persisted and failing later during public timeline presigning.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/application/ApproveMediaAsset.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/port/MediaObjectStoragePort.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/storage/S3CompatibleMediaObjectStorageAdapter.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/AdminMediaModerationTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest/AdminMediaModerationControllerTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/CompleteOwnedRangeMediaUploadTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/CreateAdminMediaPreviewUrlTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/CreateOwnedRangeMediaUploadRequestTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/ListPublicTimelineSegmentsTest.kt`
- `docs/api/openapi.yaml`
- `docs/architecture/domain-model.md`
- `docs/architecture/security-and-operations.md`
- `docs/implementation-plan/2026-06-19/validate-approved-storage-references.md`

## Tests Run And Results

- `apps/api`: `.\gradlew.bat test --tests com.timearchive.application.AdminMediaModerationTest`: passed.
- `apps/api`: `.\gradlew.bat test --tests com.timearchive.adapter.inbound.rest.AdminMediaModerationControllerTest`: passed.
- `apps/api`: `.\gradlew.bat test --max-workers=2`: passed.
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-openapi.sh`: passed.
- `git diff --check`: passed.

## Manual Verification Results

- No browser or Docker Compose flow was run for this task. The changed behavior
  is covered by application and controller tests.

## Known Limitations

- Approval validates storage URL ownership, not object existence or media
  safety. Object verification, scanning, and processing remain separate
  concerns.

## Follow-Up Recommendations

- Consider validating approved object existence once derived media generation
  and publication are implemented.
