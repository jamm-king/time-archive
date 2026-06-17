# Add Media Asset Persistence

## Objective

Add the MediaAsset domain model, repository port, PostgreSQL migration, JDBC adapter, and tests.

This is the persistence foundation for the MVP media workflow. Upload APIs, object storage integration, admin moderation APIs, and public timeline reads will be implemented in later tasks.

## Scope

- Add `MediaAsset` domain model.
- Add `MediaType` enum.
- Add `ModerationStatus` enum.
- Add `MediaAssetRepository` port.
- Add Flyway migration for `media_assets`.
- Add JDBC repository adapter.
- Add domain tests.
- Add JDBC integration tests.
- Update architecture documentation if needed.

## Out of Scope

- Media upload API
- Object storage adapter
- File validation
- Admin approval/rejection/hide use cases
- Public timeline media query API
- Thumbnail generation
- Malware scanning
- Video processing

## Relevant Files

- `src/main/kotlin/com/timearchive/domain/model/MediaAsset.kt`
- `src/main/kotlin/com/timearchive/domain/model/MediaType.kt`
- `src/main/kotlin/com/timearchive/domain/model/ModerationStatus.kt`
- `src/main/kotlin/com/timearchive/domain/port/MediaAssetRepository.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcMediaAssetRepository.kt`
- `src/main/resources/db/migration/V4__create_media_assets.sql`
- `src/test/kotlin/com/timearchive/domain/model/MediaAssetTest.kt`
- `src/test/kotlin/com/timearchive/adapter/outbound/persistence/JdbcMediaAssetRepositoryIntegrationTest.kt`
- `docs/implementation-plan/2026-06-17/add-media-asset-persistence.md`

Potentially changed files:

- `docs/architecture/domain-model.md`
- `docs/architecture/security-and-operations.md`

## Key Design Decisions

- Store media assets against `ownershipRecordId`.
- Store `ownerId` redundantly to support authorization checks and owner queries.
- Keep original and approved file URLs separate.
- Only `APPROVED` media is eligible for public timeline reads.
- `HIDDEN` means previously approved media is no longer public.
- Use nullable approved file URL and thumbnail URL because upload happens before moderation.
- Do not add object storage semantics to the domain model yet.
- Do not add foreign keys to `ownership_records` yet because ownership records may become historical, and this step should stay focused on media persistence.

## Domain Rules

- `ownerId` and `ownershipRecordId` are required.
- `originalFileUrl` must not be blank.
- `approvedFileUrl`, when present, must not be blank.
- `thumbnailUrl`, when present, must not be blank.
- `externalLink`, when present, must not be blank.
- `APPROVED` media must have an `approvedFileUrl`.
- `updatedAt` must not be before `createdAt`.

## Repository Behavior

Initial repository operations:

- `save(asset)`
- `findById(id)`
- `findByOwnershipRecordId(ownershipRecordId)`
- `findApprovedByOwnershipRecordId(ownershipRecordId)`
- `findByOwnerId(ownerId)`

## Database Constraints

Expected `media_assets` fields:

- `id uuid primary key`
- `ownership_record_id uuid not null`
- `owner_id uuid not null`
- `media_type varchar(32) not null`
- `original_file_url text not null`
- `approved_file_url text null`
- `thumbnail_url text null`
- `external_link text null`
- `moderation_status varchar(32) not null`
- `created_at timestamptz not null`
- `updated_at timestamptz not null`

Indexes:

- `media_assets_ownership_record_id_idx`
- `media_assets_owner_id_idx`
- `media_assets_approved_ownership_record_id_idx`

## Verification Plan

- Run `.\gradlew.bat test`.
- Run `.\gradlew.bat build`.
- Run `docker build -t time-archive-api:local .`.

## Risks and Rollback Strategy

- Risk: The initial table shape may need changes once upload and moderation use cases are implemented.
  - Mitigation: Keep fields aligned with existing architecture docs and avoid speculative processing fields.
- Risk: Missing foreign key can allow orphan media rows.
  - Mitigation: Application use cases must verify active ownership before media creation. A foreign key can be added later if it does not conflict with history rules.
- Rollback: Revert the implementation commit before data depends on the table.

## Progress

- [x] Latest `main` pulled.
- [x] Implementation branch created.
- [x] Implementation plan created.
- [x] Domain model added.
- [x] Repository port added.
- [x] Flyway migration added.
- [x] JDBC adapter added.
- [x] Domain tests added.
- [x] Integration tests added.
- [x] Documentation updates completed if needed.
- [x] `.\gradlew.bat test` passed.
- [x] `.\gradlew.bat build` passed.
- [x] Docker image build passed.
- [x] Completion details recorded.

## Implementation Notes

- Added `MediaAsset`, `MediaType`, and `ModerationStatus`.
- Added `MediaAssetRepository` port.
- Added `media_assets` Flyway migration.
- Added `JdbcMediaAssetRepository`.
- Added domain validation for blank URLs, approved media file URL requirements, and timestamp ordering.
- Added repository reads for asset ID, ownership record ID, approved assets by ownership record ID, and owner ID.
- Did not add upload or moderation use cases in this step.
- Did not add a foreign key to `ownership_records` yet; ownership validation belongs in later application use cases.

## Completion Summary

MediaAsset persistence foundation was implemented. The project now has media asset domain models, a repository port, PostgreSQL schema migration, JDBC persistence adapter, and focused tests.

## Files Changed

- `src/main/kotlin/com/timearchive/domain/model/MediaAsset.kt`
- `src/main/kotlin/com/timearchive/domain/model/MediaType.kt`
- `src/main/kotlin/com/timearchive/domain/model/ModerationStatus.kt`
- `src/main/kotlin/com/timearchive/domain/port/MediaAssetRepository.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcMediaAssetRepository.kt`
- `src/main/resources/db/migration/V4__create_media_assets.sql`
- `src/test/kotlin/com/timearchive/domain/model/MediaAssetTest.kt`
- `src/test/kotlin/com/timearchive/adapter/outbound/persistence/JdbcMediaAssetRepositoryIntegrationTest.kt`
- `docs/architecture/domain-model.md`
- `docs/architecture/security-and-operations.md`
- `docs/implementation-plan/2026-06-17/add-media-asset-persistence.md`

## Tests Run and Results

- `.\gradlew.bat test`: passed.
- `.\gradlew.bat build`: passed.
- `docker build -t time-archive-api:local .`: passed.

## Manual Verification Results

- Verified domain validation for media URLs, approved file URL requirements, public visibility, and timestamp ordering.
- Verified JDBC save and lookup by ID.
- Verified lookup by ownership record ID.
- Verified approved-only lookup by ownership record ID.
- Verified lookup by owner ID.
- Verified database constraints for blank original URL, invalid moderation status, and approved media without approved file URL.

## Known Limitations

- No media upload API exists yet.
- No object storage adapter exists yet.
- No admin moderation use cases exist yet.
- No public timeline media read API exists yet.
- No ownership validation is performed before media creation because application use cases are future work.

## Follow-Up Recommendations

- Add owned range media upload use case and REST API.
- Add object storage port and a local/fake storage adapter.
- Add admin moderation use cases for approve, reject, and hide.
- Add public timeline read API that returns approved media only.
