# Add Public Timeline Read API

## Objective

Add the first public timeline read API for the MVP player.

The endpoint should return public, approved media segments for a requested
window of the single canonical 24-hour timeline.

## Scope

- Add a read model for public timeline segments.
- Add a repository port for querying public timeline segments.
- Add a JDBC adapter that joins active ownership records with approved media.
- Add an application use case for timeline window reads.
- Add a REST endpoint:
  - `GET /api/timeline?from=0&to=300`
- Update OpenAPI and architecture documentation.
- Add application, REST, and JDBC integration tests.

## Out of Scope

- CDN manifest generation.
- Timeline chunk caching.
- Frontend fullscreen player.
- Authentication or personalization.
- Returning unowned placeholder segments.
- Returning owner identity, original upload URLs, or moderation metadata.
- Selecting among multiple approved assets for the same ownership range.

## Relevant Files or Modules

- `src/main/kotlin/com/timearchive/domain/model`
- `src/main/kotlin/com/timearchive/domain/port`
- `src/main/kotlin/com/timearchive/application`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest`
- `src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `src/test/kotlin/com/timearchive/application`
- `src/test/kotlin/com/timearchive/adapter/inbound/rest`
- `src/test/kotlin/com/timearchive/adapter/outbound/persistence`
- `docs/api/openapi.yaml`
- `docs/architecture/time-archive-architecture.md`
- `docs/architecture/domain-model.md`

## Key Design Decisions

- The public API exposes only approved media, never uploaded, pending, rejected,
  hidden, or original file URLs.
- The public API uses `from` and `to` query parameters to match the architecture
  document's current endpoint sketch.
- The API returns only occupied approved segments. The frontend can render
  unowned or empty seconds as placeholders.
- The JDBC query is a read model query behind a domain port. It does not leak SQL
  or persistence details into application logic.
- Segments are clipped to the requested window to keep the response directly
  usable by the player.
- Multiple approved assets for the same ownership range may produce multiple
  segments in the initial read API. A later media selection policy can choose a
  primary asset when product behavior requires it.

## Step-by-Step Execution Plan

- [x] Create a dedicated feature branch.
- [x] Create this implementation plan.
- [x] Add public timeline segment model and port.
- [x] Add list public timeline use case.
- [x] Implement JDBC read adapter.
- [x] Add REST controller and response DTO.
- [x] Wire the use case in Spring configuration.
- [x] Add application, REST, and persistence tests.
- [x] Update OpenAPI and architecture docs.
- [x] Run relevant tests and build checks.
- [x] Commit and push the branch.

## Risks and Rollback Strategy

- Risk: The first query may become inefficient when media volume grows.
  - Mitigation: Keep the query scoped by time window and approved status.
  - Follow-up: Add targeted indexes or materialized manifest chunks after the
    endpoint shape is validated.
- Risk: Multiple approved media assets can exist for one ownership record.
  - Mitigation: Document current behavior clearly and avoid hiding records
    implicitly.
  - Follow-up: Add an explicit primary-media policy if needed.
- Rollback: Revert the feature commit. No schema migration is expected for this
  task.

## Verification Plan

- Run application tests for the new timeline use case.
- Run REST controller tests for request/response and validation behavior.
- Run JDBC integration tests for approved-only, hidden exclusion, active
  ownership filtering, and window clipping.
- Run the full test suite.
- Run `git diff --check`.

## Open Questions

- Should public timeline reads eventually return placeholder ranges for unowned
  seconds?
- Should each ownership range allow only one public media asset at a time?

## Progress Log

- 2026-06-17: Created `codex/add-public-timeline-read-api`.
- 2026-06-17: Created implementation plan.
- 2026-06-17: Added public timeline model, port, use case, JDBC adapter,
  REST endpoint, wiring, and tests.
- 2026-06-17: Updated OpenAPI and architecture documentation.
- 2026-06-17: Ran `.\gradlew.bat test --max-workers=2`,
  `.\gradlew.bat build`, and `git diff --check` successfully.

## Completion Summary

Implemented the first public timeline read API.

Clients can call `GET /api/timeline?from=0&to=300` to receive approved media
segments for a requested canonical timeline window. The response exposes only
player-safe fields: segment range, media asset ID, media type, approved media
URL, thumbnail URL, and external link.

The endpoint excludes unapproved, rejected, hidden, and inactive-ownership media.
Returned segment ranges are clipped to the requested window.

## Files Changed

- `docs/api/openapi.yaml`
- `docs/architecture/domain-model.md`
- `docs/architecture/time-archive-architecture.md`
- `docs/implementation-plan/2026-06-17/add-public-timeline-read-api.md`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/PublicTimelineController.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/PublicTimelineDtos.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcPublicTimelineSegmentRepository.kt`
- `src/main/kotlin/com/timearchive/application/ListPublicTimelineSegments.kt`
- `src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `src/main/kotlin/com/timearchive/domain/model/PublicTimelineSegment.kt`
- `src/main/kotlin/com/timearchive/domain/port/PublicTimelineSegmentRepository.kt`
- `src/test/kotlin/com/timearchive/adapter/inbound/rest/PublicTimelineControllerTest.kt`
- `src/test/kotlin/com/timearchive/adapter/outbound/persistence/JdbcPublicTimelineSegmentRepositoryIntegrationTest.kt`
- `src/test/kotlin/com/timearchive/application/ListPublicTimelineSegmentsTest.kt`

## Tests Run and Results

- `.\gradlew.bat test --max-workers=2` - passed
- `.\gradlew.bat build` - passed
- `git diff --check` - passed

## Manual Verification Results

- Manually inspected OpenAPI path and schemas for public timeline reads.
- Verified the public response schema does not include owner identifiers,
  original upload URLs, or moderation status.

## Known Limitations

- The API returns approved occupied segments only. It does not return placeholder
  ranges for unowned or empty seconds.
- Multiple approved media assets for the same ownership range may return
  multiple segments until an explicit primary-media policy is introduced.
- The first implementation reads directly from PostgreSQL. CDN-cacheable
  manifest chunks remain a later optimization.

## Follow-up Recommendations

- Add an end-to-end local verification script after the admin moderation flow is
  stable on the target branch stack.
- Add a primary public media selection policy if owners can have multiple
  approved media assets for one ownership range.
- Continue with the frontend fullscreen player after this API is merged.
