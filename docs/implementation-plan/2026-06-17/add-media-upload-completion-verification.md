# Add Media Upload Completion Verification

## Objective

Complete the S3-compatible media upload flow by verifying uploaded objects before
creating `MediaAsset` records.

This task closes the current gap between presigned upload request creation and
admin moderation. After completion, moderation APIs can operate on media records
that are tied to a verified upload request.

## Scope

- Add object metadata lookup to the storage port.
- Implement S3-compatible object `HEAD` metadata lookup.
- Add upload request completion state support.
- Add a `complete` use case that:
  - verifies current user ownership
  - verifies upload request ownership and status
  - rejects expired upload requests
  - checks storage object existence
  - validates content type and content length
  - marks the upload request as completed
  - creates an `UPLOADED` `MediaAsset`
  - returns existing result on idempotent retry
- Add REST endpoint and OpenAPI documentation.
- Add unit, controller, and persistence tests.

Out of scope:

- Real Cloudflare R2 credentials.
- Malware scanning.
- File signature inspection.
- Media transcoding or thumbnail generation.
- Admin moderation APIs.

## Relevant Files Or Modules

- `src/main/kotlin/com/timearchive/application`
- `src/main/kotlin/com/timearchive/domain/model`
- `src/main/kotlin/com/timearchive/domain/port`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence`
- `src/main/kotlin/com/timearchive/adapter/outbound/storage`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest`
- `src/main/resources/db/migration`
- `docs/api/openapi.yaml`
- `docs/architecture/domain-model.md`
- `docs/architecture/security-and-operations.md`

## Key Design Decisions

- Completion creates the `MediaAsset`; upload request creation does not.
- Idempotency is tracked by storing `mediaAssetId` on completed upload requests.
- The use case uses object metadata through the storage port instead of depending
  on S3 SDK types.
- Content type and content length must match the upload request metadata.
- Upload request completion and media asset creation are executed in one
  transaction boundary.
- The REST adapter continues using `X-User-Id` as development-stage identity
  input.

## Step-By-Step Execution Plan

- [x] Update `main` and create a dedicated work branch.
- [x] Create this implementation plan.
- [x] Add upload request completion fields and migration.
- [x] Extend upload request domain model.
- [x] Extend upload request repository port and JDBC adapter.
- [x] Extend storage port and S3-compatible adapter with object metadata lookup.
- [x] Add completion use case.
- [x] Register the use case in application configuration.
- [x] Add REST endpoint and response mapping.
- [x] Update OpenAPI and architecture/security docs.
- [x] Add or update tests.
- [x] Run verification.
- [ ] Commit and push the branch.

## Risks And Rollback Strategy

- Risk: S3-compatible providers may return content type metadata differently.
  - Mitigation: Keep validation inside the application use case using normalized
    metadata returned by the storage port.
- Risk: Completion may partially succeed.
  - Mitigation: Wrap DB state changes in one transaction; storage HEAD is read-only.
- Risk: Repeated completion can create duplicate media.
  - Mitigation: Store `media_asset_id` on the upload request and return the
    existing asset when status is already `COMPLETED`.

Rollback:

- Remove the completion use case, endpoint, storage metadata lookup, and migration
  if reverted before release.
- Leave upload request creation behavior intact if reverting only the completion
  endpoint.

## Verification Plan

- Run `.\gradlew.bat test`.
- Run `.\gradlew.bat build`.
- Run `git diff --check`.
- Optionally run `docker compose config` if Compose remains touched.

## Open Questions

- None for this implementation slice.

## Progress

- 2026-06-17: Updated `main` from `origin/main`.
- 2026-06-17: Created `codex/add-media-upload-completion`.
- 2026-06-17: Plan created.
- 2026-06-17: Added upload completion migration, domain completion state,
  repository update support, S3-compatible object metadata lookup, completion use
  case, REST endpoint, OpenAPI updates, and tests.
- 2026-06-17: Verified with `.\gradlew.bat test --max-workers=2`,
  `.\gradlew.bat build`, and `git diff --check`.

## Completion Summary

Implemented upload completion verification and `MediaAsset` creation.

The backend can now complete a media upload request only after confirming that
the expected object exists in S3-compatible storage and matches the upload
request's content type and content length. Completion creates an `UPLOADED`
`MediaAsset`, stores its id on the upload request, and returns the existing asset
for repeated completion calls.

## Files Changed

- `docs/api/openapi.yaml`
- `docs/architecture/domain-model.md`
- `docs/architecture/security-and-operations.md`
- `docs/implementation-plan/2026-06-17/add-media-upload-completion-verification.md`
- `src/main/kotlin/com/timearchive/application/CompleteOwnedRangeMediaUpload.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/OwnedRangeMediaController.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/OwnedRangeMediaDtos.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcMediaUploadRequestRepository.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/storage/S3CompatibleMediaObjectStorageAdapter.kt`
- `src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `src/main/kotlin/com/timearchive/configuration/StorageConfiguration.kt`
- `src/main/kotlin/com/timearchive/domain/model/MediaUploadRequest.kt`
- `src/main/kotlin/com/timearchive/domain/port/MediaObjectStoragePort.kt`
- `src/main/kotlin/com/timearchive/domain/port/MediaUploadRequestRepository.kt`
- `src/main/resources/db/migration/V6__add_media_upload_request_completion.sql`
- `src/test/kotlin/com/timearchive/application/CompleteOwnedRangeMediaUploadTest.kt`
- `src/test/kotlin/com/timearchive/application/CreateOwnedRangeMediaUploadRequestTest.kt`
- `src/test/kotlin/com/timearchive/adapter/inbound/rest/OwnedRangeMediaControllerTest.kt`
- `src/test/kotlin/com/timearchive/adapter/outbound/persistence/JdbcMediaUploadRequestRepositoryIntegrationTest.kt`

## Tests Run And Results

- `.\gradlew.bat test --max-workers=2` passed.
- `.\gradlew.bat build` passed.
- `git diff --check` passed.

## Manual Verification Results

- Manually inspected the OpenAPI path and response schema for upload completion.
- Confirmed completion endpoint returns the created or existing `MediaAsset`.

## Known Limitations

- File signature inspection is not implemented.
- Malware scanning is not implemented.
- Video transcoding and thumbnail generation are not implemented.
- Cloudflare R2 is not configured yet.
- Local end-to-end script coverage for presigned upload and completion is not
  implemented yet.

## Follow-Up Recommendations

- Add a local MinIO end-to-end verification script for upload request, PUT upload,
  and completion.
- Wire Cloudflare R2 in a non-local environment once the local completion flow is
  manually verified.
- Add admin moderation API after this completion flow is merged.
