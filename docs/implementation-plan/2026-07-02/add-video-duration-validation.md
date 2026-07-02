# Add Video Duration Validation

## Objective

Prevent users from attaching videos that are longer than the owned time range.
The validation must avoid FFmpeg/ffprobe licensing concerns by parsing MP4
container duration metadata directly with project-owned code and no new
runtime dependency.

## Scope

- Add a domain/application port for media duration inspection.
- Add a small MP4 duration parser for `video/mp4` uploads.
- Validate video duration during owned-range media upload completion.
- Store inspected media duration on `media_assets`.
- Expose the stored duration through existing media asset API responses.
- Update OpenAPI and release readiness documentation.
- Add focused tests for duration policy and parser behavior.

## Out Of Scope

- Audio-only uploads.
- Transcoding, clipping, or thumbnail generation.
- Malware scanning.
- Full codec validation.
- Production media worker infrastructure.
- Any PayPal or payment-provider work.

## Relevant Files Or Modules

- `apps/api/src/main/kotlin/com/timearchive/application/CompleteOwnedRangeMediaUpload.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/model/MediaAsset.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/port/MediaObjectStoragePort.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/port/MediaInspectionPort.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/storage/*`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcMediaAssetRepository.kt`
- `apps/api/src/main/resources/db/migration/*`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/*`
- `docs/api/openapi.yaml`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- Validate duration in the upload completion use case because the object exists
  only after the browser uploads it to S3-compatible storage.
- Keep audio-only files unsupported.
- Keep images exempt from duration validation.
- Reject videos whose parsed duration exceeds the owned range duration plus a
  small tolerance.
- Use a default tolerance of 250 milliseconds to avoid rejecting valid files due
  to container metadata rounding.
- Fail closed when MP4 duration cannot be parsed.
- Avoid adding third-party parsing dependencies for this MVP change.
- Store `duration_ms` as nullable because images have no media duration and
  existing rows predate the validation.

## Step-By-Step Execution Plan

1. Add `MediaInspectionPort` and a `MediaInspectionResult` model.
2. Extend `MediaObjectStoragePort` with read access to uploaded object bytes.
3. Implement MP4 duration parsing from ISO BMFF boxes, prioritizing `mvhd`
   duration metadata.
4. Add a storage-backed inspection adapter for `video/mp4`.
5. Wire the inspection adapter into Spring configuration.
6. Extend `MediaAsset` with nullable `durationMs`.
7. Add a Flyway migration for `media_assets.duration_ms`.
8. Update JDBC persistence and REST DTOs.
9. Enforce video duration in `CompleteOwnedRangeMediaUpload`.
10. Map duration validation failures to stable API errors.
11. Update OpenAPI and release readiness docs.
12. Add and run targeted tests.

## Risks And Rollback Strategy

- Risk: The minimal MP4 parser may reject valid but unusual MP4 files.
  Mitigation: fail closed for MVP and document the supported subset.
- Risk: Reading the uploaded object during completion adds latency.
  Mitigation: the existing upload cap is 100 MB and the parser skips large box
  payloads where possible.
- Risk: Existing media rows have no duration.
  Mitigation: `duration_ms` remains nullable.
- Rollback: revert the application changes and migration only before deployment.
  After deployment, keep the nullable column if rollback is needed.

## Verification Plan

- Unit tests for image completion bypassing inspection.
- Unit tests for video completion within range.
- Unit tests for video completion exceeding range.
- Unit tests for duration parser success and invalid MP4 rejection.
- Run targeted Gradle tests for upload completion and storage inspection.
- Run API test suite if time permits.

## Open Questions

- None. The user approved the no-ffprobe direct MP4 parsing direction.

## Progress

- Created implementation plan.
- Added `MediaInspectionPort`.
- Extended object storage port with object read access for inspection.
- Added direct MP4 `mvhd` duration parsing without FFmpeg or third-party
  dependencies.
- Added storage-backed `video/mp4` inspection adapter.
- Added upload completion duration validation for video uploads.
- Added nullable `media_assets.duration_ms` migration and API response field.
- Updated frontend response parsers for `durationMs`.
- Updated OpenAPI and release readiness documentation.
- Added focused upload completion and MP4 parser tests.

## Completion Summary

Implemented direct MP4 duration validation during owned-range media upload
completion. `VIDEO` uploads are inspected after object upload and before
`MediaAsset` creation. Videos longer than the owned range duration plus the
250 ms tolerance are rejected. Images bypass duration inspection. Parsed
duration is stored as nullable `media_assets.duration_ms` and exposed as
`durationMs` in media asset responses.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/application/CompleteOwnedRangeMediaUpload.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/model/MediaAsset.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/port/MediaInspectionPort.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/port/MediaObjectStoragePort.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/storage/Mp4DurationParser.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/storage/Mp4MediaInspectionAdapter.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/storage/S3CompatibleMediaObjectStorageAdapter.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcMediaAssetRepository.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/OwnedRangeMediaDtos.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `apps/api/src/main/resources/db/migration/V9__add_media_asset_duration.sql`
- `apps/web/src/lib/owned-media.ts`
- `apps/web/src/lib/admin-moderation.ts`
- `docs/api/openapi.yaml`
- `docs/operations/release-readiness-checklist.md`

## Tests Run And Results

- `.\gradlew.bat test --tests "com.timearchive.application.CompleteOwnedRangeMediaUploadTest" --tests "com.timearchive.adapter.outbound.storage.Mp4DurationParserTest" --max-workers=2`:
  passed.
- `.\gradlew.bat test --max-workers=2`: passed after Docker Desktop Linux
  engine was started.
- `.\gradlew.bat build -x test --max-workers=2`: passed.
- `cmd /c npm run lint` in `apps/web`: passed.
- `cmd /c npm run build` in `apps/web`: passed.
- `./scripts/verify-openapi.sh`: passed after Docker Desktop Linux engine was
  started.

## Manual Verification Results

- Reviewed upload completion flow to confirm duration validation runs after
  object metadata verification and before media asset persistence.
- Reviewed API response parsing in the web app to confirm the new nullable
  `durationMs` field is accepted.

## Known Limitations

- The parser supports the MVP MP4 subset by reading `moov/mvhd` movie duration.
  Unusual or damaged MP4 files fail closed.
- The implementation does not perform codec validation, file signature
  validation, malware scanning, transcoding, clipping, or thumbnail generation.
- Staging smoke coverage for over-duration video rejection is not implemented
  yet.

## Follow-Up Recommendations

- Add staging smoke coverage with a short accepted MP4 and an over-duration MP4
  rejection case.
- Add file signature validation before trusting browser-supplied content type.
- Revisit a dedicated media worker only when thumbnail generation,
  transcoding, or deeper media safety checks become necessary.
