# Add Owned Range Media API

## Objective

Add the first API layer for attaching media metadata to an owned time range.

This task should allow a caller with a server-side user identity to create an uploaded
`MediaAsset` for an active ownership record, and to read media attached to that
ownership record.

## Scope

- Add application use cases for owned range media creation and lookup.
- Add ownership lookup support needed for authorization checks.
- Add REST endpoints for media creation and media lookup.
- Update OpenAPI documentation for the new endpoints.
- Add unit and controller tests for the new behavior.

Out of scope:

- Direct binary file upload through the API server.
- Object storage integration.
- Presigned upload URL generation.
- Moderation approval or rejection APIs.
- Public timeline media manifest generation.
- Real authentication integration.

## Relevant Files Or Modules

- `src/main/kotlin/com/timearchive/application`
- `src/main/kotlin/com/timearchive/domain/model/MediaAsset.kt`
- `src/main/kotlin/com/timearchive/domain/port/MediaAssetRepository.kt`
- `src/main/kotlin/com/timearchive/domain/port/OwnershipRepository.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcOwnershipRepository.kt`
- `src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `docs/api/openapi.yaml`
- `docs/architecture/security-and-operations.md`

## Key Design Decisions

- The first API accepts media metadata URLs, not file bytes.
- The request body must not accept `ownerId` or `buyerId`.
- Until real authentication exists, the REST adapter uses an `X-User-Id` header as
  development-stage server-side identity input.
- Ownership authorization is enforced in the application layer by checking that the
  requested ownership record is active and owned by the current user.
- Media creation uses `ModerationStatus.UPLOADED`; public visibility still requires
  a future moderation step.
- `OwnershipRepository.findById` is added as a domain port method because the use
  case needs ownership state, owner, and active status.

## Step-By-Step Execution Plan

- [x] Create this implementation plan.
- [x] Add ownership lookup support to the port and JDBC adapter.
- [x] Add `CreateOwnedRangeMediaAsset` use case.
- [x] Add `ListOwnedRangeMediaAssets` use case.
- [x] Register use cases in application configuration.
- [x] Add media REST DTOs and controller.
- [x] Extend API exception mapping for ownership/media authorization errors.
- [x] Update OpenAPI contract.
- [x] Add or update tests.
- [x] Run verification.
- [ ] Commit and push the branch.

## Risks And Rollback Strategy

- Risk: The temporary `X-User-Id` header could be mistaken for production-ready
  authentication.
  - Mitigation: Document it clearly as development-stage behavior in OpenAPI and
    security documentation.
- Risk: Media URLs are accepted before storage validation exists.
  - Mitigation: Keep the endpoint metadata-only, require non-blank URLs, and leave
    file signature and storage checks for the dedicated upload step.
- Risk: Adding `OwnershipRepository.findById` requires updating test fakes.
  - Mitigation: Keep the method simple and covered by integration tests.

Rollback:

- Remove the new use cases, controller, DTOs, and OpenAPI entries.
- Remove `OwnershipRepository.findById` if no longer used.
- Keep the existing MediaAsset persistence migration unchanged unless this task
  introduces a migration, which is not currently planned.

## Verification Plan

- Run `.\gradlew.bat test`.
- Run `.\gradlew.bat build`.
- Run `git diff --check`.
- Manually inspect OpenAPI endpoint definitions for request and response consistency.

## Open Questions

- None for this implementation slice. Real identity and object storage remain
  separate follow-up tasks.

## Progress

- 2026-06-17: Plan created. The implementation will build on the existing
  MediaAsset persistence branch.
- 2026-06-17: Added ownership lookup, owned range media creation/listing use
  cases, REST endpoints, OpenAPI updates, security documentation, and tests.
- 2026-06-17: Verified with `.\gradlew.bat test`, `.\gradlew.bat build`, and
  `git diff --check`.

## Completion Summary

Implemented the first owned range media metadata API.

The API now supports creating `UPLOADED` media metadata for an active ownership
record and listing all media metadata attached to an active ownership record for
the current owner. The implementation derives the media owner from the ownership
record and does not accept owner identity in the request body.

## Files Changed

- `docs/api/openapi.yaml`
- `docs/architecture/security-and-operations.md`
- `docs/implementation-plan/2026-06-17/add-owned-range-media-api.md`
- `src/main/kotlin/com/timearchive/application/CreateOwnedRangeMediaAsset.kt`
- `src/main/kotlin/com/timearchive/application/ListOwnedRangeMediaAssets.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/OwnedRangeMediaController.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/OwnedRangeMediaDtos.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcOwnershipRepository.kt`
- `src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `src/main/kotlin/com/timearchive/domain/port/OwnershipRepository.kt`
- `src/test/kotlin/com/timearchive/adapter/inbound/rest/OwnedRangeMediaControllerTest.kt`
- `src/test/kotlin/com/timearchive/adapter/outbound/persistence/JdbcOwnershipRepositoryIntegrationTest.kt`
- `src/test/kotlin/com/timearchive/application/CreateOwnedRangeMediaAssetTest.kt`
- `src/test/kotlin/com/timearchive/application/ListOwnedRangeMediaAssetsTest.kt`
- Existing application tests updated for the new ownership repository port method.

## Tests Run And Results

- `.\gradlew.bat test` passed.
- `.\gradlew.bat build` passed.
- `git diff --check` passed.

## Manual Verification Results

- Manually inspected OpenAPI endpoint definitions for request, response, and
  error-code consistency.
- Confirmed the API contract documents `X-User-Id` as development-stage identity
  input only.

## Known Limitations

- Real authentication is not implemented.
- `X-User-Id` is a temporary development-stage stand-in for server-side identity.
- The API stores media metadata URLs only and does not verify object storage
  ownership, file existence, file signatures, file sizes, or content safety.
- Media approval and public timeline manifest generation remain separate tasks.

## Follow-Up Recommendations

- Add object storage upload preparation with presigned URLs.
- Add media moderation state transition APIs.
- Add local verification script coverage once the purchase flow can return or
  expose ownership record identifiers conveniently.
