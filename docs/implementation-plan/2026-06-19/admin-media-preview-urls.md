# Admin Media Preview URLs

## Objective

Allow admins to preview uploaded original media from the moderation UI without
making the object storage bucket public.

## Scope

- Add a storage port operation for short-lived presigned download URLs.
- Implement the S3-compatible presigned GET adapter behavior.
- Add an admin-only backend endpoint for original media preview URLs.
- Add a Next.js proxy route for the new admin endpoint.
- Update the admin moderation UI so `Open original` requests a preview URL
  before opening the object.
- Add focused backend tests for the use case and REST endpoint.

Out of scope:

- Public media delivery changes.
- Bucket policy changes.
- Media transcoding or thumbnail generation.
- Dedicated admin dashboard routing.

## Relevant Files Or Modules

- `apps/api/src/main/kotlin/com/timearchive/domain/port/MediaObjectStoragePort.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/storage/S3CompatibleMediaObjectStorageAdapter.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/CreateAdminMediaPreviewUrl.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/AdminMediaModerationController.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/AdminMediaModerationDtos.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `apps/web/src/app/api/admin/media/assets/**`
- `apps/web/src/lib/admin-moderation.ts`
- `apps/web/src/components/PublicTimelinePlayer.tsx`

## Key Design Decisions

- Keep uploaded objects private. Admin previews use short-lived presigned GET
  URLs instead of public bucket access.
- Reuse the existing server-side admin session and role check. The frontend role
  remains UI-only.
- Keep object URL parsing inside the storage adapter by passing the stored
  original file URL through the media object storage port.
- Use a short configurable TTL for preview URLs, defaulting to 300 seconds.

## Step-By-Step Execution Plan

- [x] Create a dedicated feature branch from latest `main`.
- [x] Add this implementation plan.
- [x] Add presigned download support to the storage port and S3-compatible adapter.
- [x] Add `CreateAdminMediaPreviewUrl` application use case.
- [x] Wire the use case in application configuration.
- [x] Add admin REST endpoint and DTO.
- [x] Add backend tests for use case and controller behavior.
- [x] Add web proxy route and frontend helper.
- [x] Update moderation UI to open the presigned preview URL.
- [x] Run backend and frontend checks.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: Preview URLs could be treated as long-lived access.
  - Mitigation: Use a short TTL and generate URLs only after backend admin
    authorization.
- Risk: Stored object URL cannot be mapped back to an object key.
  - Mitigation: The adapter validates that URLs match the configured storage
    public base URL before presigning.
- Risk: Popup blockers could block `window.open` after an async request.
  - Mitigation: Open a blank tab synchronously and navigate it after the preview
    URL is returned.

Rollback:

- Revert the new storage port method, use case, endpoint, web proxy/helper, UI
  changes, tests, and this implementation plan.

## Verification Plan

- `.\gradlew.bat test --max-workers=2`.
- `npm.cmd run lint` in `apps/web`.
- `npm.cmd run build` in `apps/web`.
- `git diff --check`.

## Open Questions

- None.

## Progress Log

- 2026-06-19: Confirmed local `main` matched `origin/main`.
- 2026-06-19: Created `feature/admin-media-preview-urls`.
- 2026-06-19: Added implementation plan.
- 2026-06-19: Added presigned download support to the media object storage port
  and S3-compatible adapter.
- 2026-06-19: Added `CreateAdminMediaPreviewUrl` use case with focused tests.
- 2026-06-19: Added admin-only backend `preview-url` endpoint and controller
  test coverage.
- 2026-06-19: Added web proxy route and frontend helper for preview URLs.
- 2026-06-19: Updated moderation UI so `Open original` opens a short-lived
  preview URL instead of the private object URL.

## Completion Summary

Admins can now open original uploaded media from the moderation modal without
making the storage bucket public. The web UI requests a backend-admin-verified
preview URL and opens the returned short-lived presigned URL in a new tab.

The backend exposes `GET /api/admin/media/assets/{mediaAssetId}/preview-url`
and the web app proxies it through
`/api/admin/media/assets/{mediaAssetId}/preview-url`.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/domain/port/MediaObjectStoragePort.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/storage/S3CompatibleMediaObjectStorageAdapter.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/CreateAdminMediaPreviewUrl.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/AdminMediaModerationController.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/AdminMediaModerationDtos.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `apps/api/src/main/resources/application.yml`
- `apps/api/src/test/kotlin/com/timearchive/application/CreateAdminMediaPreviewUrlTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/CreateOwnedRangeMediaUploadRequestTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/CompleteOwnedRangeMediaUploadTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest/AdminMediaModerationControllerTest.kt`
- `apps/web/src/app/api/admin/media/assets/[mediaAssetId]/preview-url/route.ts`
- `apps/web/src/lib/admin-moderation.ts`
- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `docs/implementation-plan/2026-06-19/admin-media-preview-urls.md`

## Tests Run And Results

- `.\gradlew.bat test --max-workers=2`
  - Passed.
- `npm.cmd run lint`
  - Passed.
- `npm.cmd run build`
  - Passed.
- `git diff --check`
  - Passed.

## Manual Verification Results

- Not run in browser. The local browser tool is unreliable in this environment.
- Build output confirms the new Next.js proxy route is registered.

## Known Limitations

- Preview URL TTL is configurable and defaults to 300 seconds.
- The UI opens a blank tab synchronously before requesting the preview URL to
  reduce popup-blocking risk. If the request fails, that blank tab is closed.
- The backend presigns the stored original media URL only when it matches the
  configured storage public base URL.

## Follow-Up Recommendations

- Add an end-to-end script that verifies admin preview URL generation against
  the Docker Compose MinIO stack.
