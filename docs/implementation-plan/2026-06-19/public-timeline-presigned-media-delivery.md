# Public Timeline Presigned Media Delivery

## Objective

Make public timeline media delivery use short-lived read-only presigned URLs
for approved media while keeping stored media objects private.

## Scope

- Treat `approvedFileUrl` and `thumbnailUrl` as internal stored object
  references, not permanent public playback URLs.
- Convert approved public timeline media URLs to presigned GET URLs at read
  time.
- Ensure public timeline reads only include `APPROVED` media.
- Update tests, OpenAPI, and verification scripts to match the delivery policy.

Out of scope:

- Cloudflare R2 production wiring.
- Public CDN domain setup.
- Separate playback URL endpoint.
- Media transcoding or thumbnail generation.

## Relevant Files Or Modules

- `apps/api/src/main/kotlin/com/timearchive/application/ListPublicTimelineSegments.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcPublicTimelineSegmentRepository.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/ListPublicTimelineSegmentsTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/outbound/persistence/JdbcPublicTimelineSegmentRepositoryIntegrationTest.kt`
- `scripts/verify-local-public-timeline-flow.sh`
- `docs/api/openapi.yaml`
- `docs/architecture/security-and-operations.md`

## Key Design Decisions

- Keep the object storage bucket private for both original and approved media.
- Public timeline responses will contain short-lived presigned GET URLs in
  `mediaUrl` and `thumbnailUrl` when present.
- Use a 10-minute playback URL expiry for the MVP.
- Keep the public timeline API response shape backward-compatible.
- Do not introduce Cloudflare R2 in this task. R2 should be connected after the
  local MinIO-backed S3-compatible path is stable and before production
  deployment.
- Public timeline HTTP responses should not be cached by shared caches while
  they embed presigned URLs.

## Step-By-Step Execution Plan

- [x] Create a dedicated feature branch from latest `main`.
- [x] Add this implementation plan.
- [x] Update the public timeline use case to presign approved media URLs.
- [x] Confirm hidden and rejected media stay excluded by the existing
  `APPROVED` filter.
- [x] Update application and persistence tests.
- [x] Update local public timeline verification script expectations.
- [x] Update OpenAPI and architecture documentation.
- [x] Run relevant backend tests and script checks.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: Public timeline reads become dependent on storage presigning.
  - Mitigation: Keep the logic isolated in the use case and use the existing
    storage port.
- Risk: Presigned URLs reduce API cacheability.
  - Mitigation: Document no-store/short-cache policy and defer cache
    optimization until a separate playback URL endpoint or CDN strategy exists.
- Risk: Existing local verification expected raw `approvedFileUrl`.
  - Mitigation: Update the script to validate a playable presigned URL instead.

Rollback:

- Revert this branch to return stored approved URLs directly from the public
  timeline repository.

## Verification Plan

- `.\gradlew.bat test --tests com.timearchive.application.ListPublicTimelineSegmentsTest`
- `.\gradlew.bat test --tests com.timearchive.adapter.outbound.persistence.JdbcPublicTimelineSegmentRepositoryIntegrationTest`
- `.\gradlew.bat test --tests com.timearchive.adapter.inbound.rest.PublicTimelineControllerTest`
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-local-public-timeline-flow.sh`
- `git diff --check`

## Open Questions

- None.

## Progress Log

- 2026-06-19: Confirmed `main` was up to date.
- 2026-06-19: Created `feature/public-timeline-presigned-media-delivery`.
- 2026-06-19: Added implementation plan.
- 2026-06-19: Confirmed the current schema has no separate
  `publicly_visible` column; public visibility is represented by
  `moderation_status = 'APPROVED'`.
- 2026-06-19: Updated public timeline reads to convert stored approved media
  references into 10-minute presigned playback URLs.
- 2026-06-19: Added `Cache-Control: no-store` to backend and web public
  timeline responses.
- 2026-06-19: Updated tests, the local public timeline verification script,
  OpenAPI, and architecture documentation.
- 2026-06-19: Rebuilt the local Docker Compose stack and verified the public
  timeline flow with `START_SECOND=4300 END_SECOND=4301`.

## Completion Summary

Public timeline media delivery now keeps approved media objects private and
returns short-lived presigned playback URLs in `mediaUrl` and `thumbnailUrl`
when present. The response shape remains unchanged. The backend and web proxy
now mark timeline responses as `Cache-Control: no-store` because they embed
expiring URLs. Hidden, rejected, uploaded, and pending media remain excluded by
the existing `APPROVED` query filter.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/application/ListPublicTimelineSegments.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/PublicTimelineController.kt`
- `apps/api/src/main/resources/application.yml`
- `apps/api/src/test/kotlin/com/timearchive/application/ListPublicTimelineSegmentsTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest/PublicTimelineControllerTest.kt`
- `apps/web/src/app/api/timeline/route.ts`
- `scripts/verify-local-public-timeline-flow.sh`
- `docs/api/openapi.yaml`
- `docs/architecture/domain-model.md`
- `docs/architecture/security-and-operations.md`
- `docs/manual-verification/local-public-timeline-flow.md`
- `docs/implementation-plan/2026-06-19/public-timeline-presigned-media-delivery.md`

## Tests Run And Results

- `apps/api`: `.\gradlew.bat test --tests com.timearchive.application.ListPublicTimelineSegmentsTest`: passed.
- `apps/api`: `.\gradlew.bat test --tests com.timearchive.adapter.inbound.rest.PublicTimelineControllerTest`: passed.
- `apps/api`: `.\gradlew.bat test --tests com.timearchive.adapter.outbound.persistence.JdbcPublicTimelineSegmentRepositoryIntegrationTest`: passed.
- `apps/web`: `npm.cmd run lint`: passed.
- `apps/web`: `npm.cmd run build`: passed.
- `C:\Program Files\Git\bin\bash.exe -n ./scripts/verify-local-public-timeline-flow.sh`: passed.
- `git diff --check`: passed.
- `docker compose up -d --build`: passed.
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-local-public-timeline-flow.sh`: default range `[3000,3001)` was already unavailable in the local database.
- `START_SECOND=4300 END_SECOND=4301 C:\Program Files\Git\bin\bash.exe ./scripts/verify-local-public-timeline-flow.sh`: passed.

## Manual Verification Results

- The local public timeline flow verified purchase, upload request creation,
  presigned object upload, upload completion, pre-approval timeline exclusion,
  admin approval, public timeline inclusion through a presigned playback URL,
  and playback URL byte equality with the uploaded object.

## Known Limitations

- `approvedFileUrl` and `thumbnailUrl` must point to objects belonging to the
  configured storage base URL. Arbitrary external URLs are not compatible with
  the private-bucket presigned delivery policy.
- Cloudflare R2 is not connected in this task.
- CDN cache optimization is deferred until after MVP correctness and access
  control are stable.

## Follow-Up Recommendations

- Add approval-time validation that approved media references belong to the
  configured storage backend.
- Revisit media processing and derived approved media generation before
  production launch.
