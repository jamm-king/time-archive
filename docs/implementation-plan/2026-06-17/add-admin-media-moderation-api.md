# Add Admin Media Moderation API

## Objective

Add the first admin media moderation API for the MVP media workflow.

The upload flow can now create `UPLOADED` `MediaAsset` records. This task adds
admin operations to list media requiring review, approve media, reject media, and
hide previously approved media.

## Scope

- Add moderation transition behavior to `MediaAsset`.
- Extend `MediaAssetRepository` with moderation reads and updates.
- Add admin moderation use cases:
  - list review queue
  - approve media
  - reject media
  - hide media
- Add admin REST endpoints.
- Update OpenAPI and security/domain documentation.
- Add focused unit, controller, and persistence tests.

Out of scope:

- Real admin authentication and RBAC.
- Audit log entries for moderation actions.
- Moderation reason persistence.
- Thumbnail generation, video transcoding, malware scanning, or file signature
  inspection.
- Public timeline API.

## Relevant Files Or Modules

- `src/main/kotlin/com/timearchive/domain/model/MediaAsset.kt`
- `src/main/kotlin/com/timearchive/domain/port/MediaAssetRepository.kt`
- `src/main/kotlin/com/timearchive/application`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcMediaAssetRepository.kt`
- `src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `docs/api/openapi.yaml`
- `docs/architecture/domain-model.md`
- `docs/architecture/security-and-operations.md`

## Key Design Decisions

- `X-Admin-Id` is used only as development-stage admin identity input.
- Approval requires an `approvedFileUrl`; this keeps original upload URLs
  separate from approved public media URLs.
- Rejection is allowed from `UPLOADED` or `PENDING_REVIEW`.
- Hiding is allowed only from `APPROVED`.
- Moderation updates use existing `MediaAsset` rows and update the moderation
  fields atomically.

## Step-By-Step Execution Plan

- [x] Create a dedicated work branch.
- [x] Create this implementation plan.
- [x] Add domain transition methods.
- [x] Extend repository port and JDBC adapter.
- [x] Add moderation use cases.
- [x] Register use cases.
- [x] Add REST DTOs and controller.
- [x] Update exception mapping.
- [x] Update OpenAPI and architecture/security docs.
- [x] Add tests.
- [x] Run verification.
- [ ] Commit and push the branch.

## Risks And Rollback Strategy

- Risk: The temporary `X-Admin-Id` header may be mistaken for production admin
  authorization.
  - Mitigation: Document it clearly as development-stage behavior.
- Risk: Approval without processing could publish unsafe original files.
  - Mitigation: Require `approvedFileUrl` explicitly and document that processing
    and scanning remain future tasks.

Rollback:

- Remove the admin controller, use cases, repository update methods, and docs.
- No schema migration is planned for this task.

## Verification Plan

- Run `.\gradlew.bat test --max-workers=2`.
- Run `.\gradlew.bat build`.
- Run `git diff --check`.

## Open Questions

- None for this implementation slice.

## Progress

- 2026-06-17: Created `codex/add-admin-media-moderation-api`.
- 2026-06-17: Plan created.
- 2026-06-17: Added admin moderation use cases, REST endpoints, repository
  updates, OpenAPI documentation, and tests.
- 2026-06-17: Verified with `.\gradlew.bat test --max-workers=2`,
  `.\gradlew.bat build`, and `git diff --check`.

## Completion Summary

Implemented the first admin media moderation API.

Admins can now list media assets by moderation status, approve uploaded media
with an explicit approved URL, reject uploaded media, and hide approved media.
The API uses `X-Admin-Id` only as a development-stage identity input.

## Files Changed

- `docs/api/openapi.yaml`
- `docs/architecture/domain-model.md`
- `docs/architecture/security-and-operations.md`
- `docs/implementation-plan/2026-06-17/add-admin-media-moderation-api.md`
- `src/main/kotlin/com/timearchive/application/ApproveMediaAsset.kt`
- `src/main/kotlin/com/timearchive/application/HideMediaAsset.kt`
- `src/main/kotlin/com/timearchive/application/ListMediaModerationQueue.kt`
- `src/main/kotlin/com/timearchive/application/RejectMediaAsset.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/AdminMediaModerationController.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/AdminMediaModerationDtos.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcMediaAssetRepository.kt`
- `src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `src/main/kotlin/com/timearchive/domain/model/MediaAsset.kt`
- `src/main/kotlin/com/timearchive/domain/port/MediaAssetRepository.kt`
- `src/test/kotlin/com/timearchive/application/AdminMediaModerationTest.kt`
- `src/test/kotlin/com/timearchive/adapter/inbound/rest/AdminMediaModerationControllerTest.kt`
- `src/test/kotlin/com/timearchive/adapter/outbound/persistence/JdbcMediaAssetRepositoryIntegrationTest.kt`
- Existing application tests updated for the expanded media asset repository
  contract.

## Tests Run And Results

- `.\gradlew.bat test --max-workers=2` passed.
- `.\gradlew.bat build` passed.
- `git diff --check` passed.

## Manual Verification Results

- Manually inspected OpenAPI paths and response schemas for admin moderation.
- Confirmed approval requires `approvedFileUrl` and hide transitions only apply
  to approved media through domain tests.

## Known Limitations

- Real admin authentication and RBAC are not implemented.
- Moderation actions are not audit logged yet.
- Rejection reasons are not persisted.
- Media scanning, transcoding, and thumbnail generation are not implemented.

## Follow-Up Recommendations

- Add public timeline read API that returns `APPROVED` media only.
- Add audit logging when a durable admin identity model exists.
