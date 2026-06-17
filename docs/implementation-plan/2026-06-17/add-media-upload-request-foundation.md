# Add Media Upload Request Foundation

## Objective

Add an S3-compatible upload request foundation for owned range media.

The goal is to issue short-lived presigned upload URLs for active ownership records
owned by the current user, store upload request metadata for later completion
verification, and provide a local MinIO-based development environment.

## Scope

- Add a `media_upload_requests` persistence model and migration.
- Add a domain model and repository port for media upload requests.
- Add a storage presigning port and S3-compatible outbound adapter.
- Add application use case for creating owned range media upload requests.
- Add REST endpoint and DTOs for upload request creation.
- Add MinIO to Docker Compose for local S3-compatible development.
- Update OpenAPI and security/operations documentation.
- Add focused tests.

Out of scope:

- Actual AWS S3 or Cloudflare R2 production credential wiring.
- Upload completion verification.
- Creating `MediaAsset` records from completed uploads.
- File signature inspection and malware scanning.
- Admin moderation APIs.

## Relevant Files Or Modules

- `build.gradle.kts`
- `docker-compose.yml`
- `src/main/resources/application.yml`
- `src/main/resources/db/migration`
- `src/main/kotlin/com/timearchive/domain/model`
- `src/main/kotlin/com/timearchive/domain/port`
- `src/main/kotlin/com/timearchive/application`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence`
- `src/main/kotlin/com/timearchive/adapter/outbound/storage`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest`
- `src/main/kotlin/com/timearchive/configuration`
- `docs/api/openapi.yaml`
- `docs/architecture/security-and-operations.md`

## Dependency Decision

Add AWS SDK for Java v2 S3 support:

- Dependency: `software.amazon.awssdk:s3`
- License: Apache-2.0
- Reason: Provides maintained SigV4 signing and S3 presigner support, including
  endpoint override for S3-compatible providers such as MinIO and Cloudflare R2.
- Alternative considered: implement SigV4 signing directly. Rejected because it
  increases security risk and maintenance cost for a non-core product concern.
- Compatibility: Apache-2.0 is compatible with the repository's MIT license.

## Key Design Decisions

- Upload requests are stored before upload completion.
- The upload request stores the server-generated object key, expected content
  type, expected byte size limit, expiration time, and current status.
- The client must not provide object keys or owner identifiers.
- The REST adapter continues using `X-User-Id` as development-stage identity
  input until real authentication exists.
- Local development uses MinIO through S3-compatible configuration.
- Cloudflare R2 should be wired after this foundation is merged and the local
  MinIO flow is verified end-to-end.

## Step-By-Step Execution Plan

- [x] Update `main` and create a dedicated work branch.
- [x] Create this implementation plan.
- [x] Add AWS SDK dependency and storage configuration properties.
- [x] Add MinIO service and API environment variables to Docker Compose.
- [x] Add `media_upload_requests` migration.
- [x] Add domain model and repository port.
- [x] Add JDBC repository adapter.
- [x] Add S3-compatible presigned upload URL port and adapter.
- [x] Add create upload request use case.
- [x] Register new beans in application configuration.
- [x] Add REST endpoint and DTOs.
- [x] Update OpenAPI and security/operations docs.
- [x] Add tests.
- [x] Run verification.
- [ ] Commit and push the branch.

## Risks And Rollback Strategy

- Risk: Presigned URL configuration may vary between AWS S3, MinIO, and
  Cloudflare R2.
  - Mitigation: Keep provider-specific values in configuration and hide them
    behind a storage port.
- Risk: Upload request records may accumulate if clients never complete uploads.
  - Mitigation: Store `expiresAt`; cleanup can be added as a later scheduled task.
- Risk: Client-provided metadata can be spoofed.
  - Mitigation: Treat this as preparation only; completion must verify object
    metadata and future processing must validate actual file content.

Rollback:

- Remove the upload request use case, REST endpoint, storage adapter, and
  migration if this task is reverted before release.
- Remove MinIO service from Docker Compose.
- Remove AWS SDK dependency if no other storage code uses it.

## Verification Plan

- Run `.\gradlew.bat test`.
- Run `.\gradlew.bat build`.
- Run `git diff --check`.
- If local Docker is available, optionally run `docker compose config` to validate
  Compose syntax.

## Cloudflare R2 Timing Recommendation

Connect real Cloudflare R2 after:

- MinIO local upload request generation is verified.
- Upload completion verification is implemented.
- Object key naming, bucket privacy, expiration, and public delivery strategy are
  stable.
- Secrets management for non-local environments is defined.

R2 should be an environment configuration change, not a separate domain concept.
The same S3-compatible port should support MinIO locally and R2 in deployed
environments.

## Open Questions

- None for this implementation slice.

## Progress

- 2026-06-17: Updated `main` from `origin/main`.
- 2026-06-17: Created `codex/add-media-upload-request-foundation`.
- 2026-06-17: Plan created.
- 2026-06-17: Added upload request domain, persistence, S3-compatible presigning
  port/adapter, REST endpoint, MinIO local development setup, OpenAPI updates,
  and tests.
- 2026-06-17: Verified with `.\gradlew.bat test`, `.\gradlew.bat build`,
  `docker compose config`, and `git diff --check`.

## Completion Summary

Implemented the first media upload request foundation.

The system can now create a server-tracked upload request for an active owned
range, generate a server-controlled object key, issue a short-lived S3-compatible
presigned PUT URL, and store expected upload metadata for later completion
verification.

## Files Changed

- `README.md`
- `.gitignore`
- `build.gradle.kts`
- `docker-compose.yml`
- `docs/api/openapi.yaml`
- `docs/architecture/domain-model.md`
- `docs/architecture/security-and-operations.md`
- `docs/implementation-plan/2026-06-17/add-media-upload-request-foundation.md`
- `src/main/kotlin/com/timearchive/application/CreateOwnedRangeMediaUploadRequest.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/OwnedRangeMediaController.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/OwnedRangeMediaDtos.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcMediaUploadRequestRepository.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/storage/S3CompatibleMediaObjectStorageAdapter.kt`
- `src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `src/main/kotlin/com/timearchive/configuration/StorageConfiguration.kt`
- `src/main/kotlin/com/timearchive/domain/model/MediaUploadRequest.kt`
- `src/main/kotlin/com/timearchive/domain/model/MediaUploadRequestStatus.kt`
- `src/main/kotlin/com/timearchive/domain/port/MediaObjectStoragePort.kt`
- `src/main/kotlin/com/timearchive/domain/port/MediaUploadRequestRepository.kt`
- `src/main/resources/application.yml`
- `src/main/resources/db/migration/V5__create_media_upload_requests.sql`
- `src/test/kotlin/com/timearchive/application/CreateOwnedRangeMediaUploadRequestTest.kt`
- `src/test/kotlin/com/timearchive/adapter/inbound/rest/OwnedRangeMediaControllerTest.kt`
- `src/test/kotlin/com/timearchive/adapter/outbound/persistence/JdbcMediaUploadRequestRepositoryIntegrationTest.kt`

## Tests Run And Results

- `.\gradlew.bat test` passed.
- `.\gradlew.bat build` passed.
- `docker compose config` passed.
- `git diff --check` passed with a Windows line-ending warning for
  `application.yml`, but no whitespace errors.

## Manual Verification Results

- Confirmed the Compose model includes PostgreSQL, Redis, MinIO, MinIO bucket
  initialization, and API storage environment variables.
- Confirmed OpenAPI documents upload request creation as preparation only, not
  upload completion or media publication.

## Known Limitations

- Upload completion verification is not implemented.
- `MediaAsset` is not created from upload requests yet.
- Object metadata, file signature, and malware scanning are not verified yet.
- MinIO is configured for local development only.
- Cloudflare R2 is not wired yet.

## Follow-Up Recommendations

- Add upload completion verification using the stored upload request.
- Add object metadata HEAD checks before creating `MediaAsset`.
- Add Cloudflare R2 environment wiring after completion verification is stable.
- Add a local end-to-end upload verification script after completion exists.
