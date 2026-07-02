# Add File Signature Validation

## Objective

Reject uploaded media whose actual file signature does not match the requested
content type. This reduces reliance on browser-supplied `contentType` and
closes the release-readiness blocker for basic file signature validation.

## Scope

- Validate file signatures for the currently supported upload content types:
  `image/jpeg`, `image/png`, `image/webp`, and `video/mp4`.
- Run validation during upload completion after object metadata checks and
  before `MediaAsset` creation.
- Reuse the existing media inspection port and storage object read path.
- Keep video duration validation behavior unchanged.
- Add focused tests and update OpenAPI/release-readiness documentation.

## Out Of Scope

- Malware scanning.
- Codec validation.
- Transcoding, thumbnail generation, or media rewriting.
- Support for additional file types.
- Staging live smoke automation for signature mismatch.

## Relevant Files Or Modules

- `apps/api/src/main/kotlin/com/timearchive/application/CompleteOwnedRangeMediaUpload.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/port/MediaInspectionPort.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/storage/StorageMediaInspectionAdapter.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/CompleteOwnedRangeMediaUploadTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/outbound/storage/*`
- `docs/api/openapi.yaml`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- Fail closed when the object cannot be read or the signature cannot be
  recognized.
- Keep signature validation in the inspection adapter so application logic
  depends only on a port result.
- For MP4, require an ISO BMFF-compatible `ftyp` box near the start of the
  object. Duration parsing remains responsible for finding `moov/mvhd`.
- For WebP, require RIFF/WEBP headers.
- For JPEG and PNG, use standard magic bytes.
- Do not introduce new dependencies.

## Step-By-Step Execution Plan

1. Add signature match information to `MediaInspectionPort.Result`.
2. Add file signature detection for the supported content types.
3. Update the storage inspection adapter to inspect signature for every
   supported upload type and duration for MP4.
4. Update upload completion use case to reject signature mismatches.
5. Add API error mapping.
6. Add focused application and signature detector tests.
7. Update OpenAPI and release readiness docs.
8. Run targeted tests, backend tests, build, web checks if needed, and OpenAPI
   validation.

## Risks And Rollback Strategy

- Risk: Some valid MP4 files with unusual layout could be rejected.
  - Mitigation: Accept standard ISO BMFF `ftyp` box signatures and keep the
    parser conservative for MVP.
- Risk: Reading object bytes during completion adds overhead.
  - Mitigation: Only a small header is needed for images, and MP4 duration
    already reads the object for video validation.
- Rollback: Revert the code/docs commit. No schema migration is required.

## Verification Plan

- Unit tests for matching signatures.
- Unit tests for mismatch rejection.
- Existing video duration tests remain green.
- Full backend test suite.
- Backend build.
- OpenAPI validation.

## Open Questions

- None.

## Progress

- Created implementation plan.
- Added file signature matching to the media inspection port result.
- Added signature detection for JPEG, PNG, WebP, and MP4.
- Updated storage-backed media inspection to validate signatures and preserve
  MP4 duration inspection.
- Updated upload completion to reject signature mismatches before media asset
  creation.
- Added API error mapping and OpenAPI error code documentation.
- Updated release readiness from blocked to needs verification.
- Added focused application, detector, and adapter tests.

## Completion Summary

Upload completion now validates that the uploaded object's file signature
matches the requested content type before creating a media asset. Supported
checks cover `image/jpeg`, `image/png`, `image/webp`, and `video/mp4`. Signature
mismatches fail closed with `MEDIA_FILE_SIGNATURE_MISMATCH`. Existing MP4
duration validation remains unchanged.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/application/CompleteOwnedRangeMediaUpload.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/port/MediaInspectionPort.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/storage/MediaFileSignatureDetector.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/storage/StorageMediaInspectionAdapter.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/CompleteOwnedRangeMediaUploadTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/outbound/storage/MediaFileSignatureDetectorTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/outbound/storage/StorageMediaInspectionAdapterTest.kt`
- `docs/api/openapi.yaml`
- `docs/operations/release-readiness-checklist.md`

## Tests Run And Results

- `.\gradlew.bat test --tests "com.timearchive.application.CompleteOwnedRangeMediaUploadTest" --tests "com.timearchive.adapter.outbound.storage.MediaFileSignatureDetectorTest" --tests "com.timearchive.adapter.outbound.storage.StorageMediaInspectionAdapterTest" --tests "com.timearchive.adapter.outbound.storage.Mp4DurationParserTest" --max-workers=2`:
  passed.
- `.\gradlew.bat test --max-workers=2`: passed.
- `./scripts/verify-openapi.sh`: passed.

## Manual Verification Results

- Reviewed upload completion flow to confirm signature validation runs after
  object metadata checks and before media asset persistence.

## Known Limitations

- This is signature sniffing, not malware scanning.
- MP4 validation checks the ISO BMFF `ftyp` signature and existing duration
  parser, but it does not validate codecs or playback compatibility.
- Staging signature-mismatch smoke coverage is not implemented yet.

## Follow-Up Recommendations

- Add staging smoke coverage for signature mismatch rejection after this change
  is deployed.
- Decide the MVP malware scanning policy separately.
