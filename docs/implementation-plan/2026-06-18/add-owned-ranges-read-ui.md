# Add Owned Ranges Read UI

## Objective

Expose the authenticated user's owned archive ranges through `GET /api/me/owned-ranges`
and show those ranges in the frontend account panel.

## Scope

- Add an application use case for listing active ownership records owned by the
  current authenticated user.
- Add a backend REST endpoint at `GET /api/me/owned-ranges`.
- Add a frontend same-origin proxy route for the endpoint.
- Add a minimal authenticated account-panel UI that lists owned ranges with
  loading, empty, and error states.
- Update OpenAPI documentation for the new endpoint.

Out of scope:

- Media upload UI.
- Purchase UI.
- Ownership transfer or ownership history management.
- Admin-only ownership inspection.

## Relevant Files Or Modules

- `apps/api/src/main/kotlin/com/timearchive/domain/port/OwnershipRepository.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcOwnershipRepository.kt`
- `apps/api/src/main/kotlin/com/timearchive/application`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest`
- `apps/api/src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `apps/api/src/test/kotlin/com/timearchive`
- `apps/web/src/app/api/me`
- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `apps/web/src/lib`
- `docs/api/openapi.yaml`

## Key Design Decisions

- The endpoint derives identity from the server-side session through
  `CurrentUserSession`; it does not accept `ownerId` or any identity header.
- The domain port gains an owner-based active listing method because application
  use cases should not depend on JDBC details.
- The response hides `ownerId`; the endpoint is scoped to the current user.
- The frontend uses the existing Next.js route-handler proxy pattern so browser
  requests remain same-origin.
- The UI remains minimal and account-scoped. It only displays owned ranges and
  does not introduce upload actions yet.

## Step-By-Step Execution Plan

- [x] Inspect existing backend and frontend conventions.
- [x] Add this implementation plan.
- [x] Add owner-scoped ownership listing use case and repository port method.
- [x] Implement the JDBC repository query.
- [x] Add `GET /api/me/owned-ranges` REST endpoint and DTO.
- [x] Add backend unit/integration tests.
- [x] Add frontend proxy route and client fetch helper.
- [x] Add owned ranges account-panel UI states.
- [x] Update OpenAPI documentation.
- [x] Run relevant backend and frontend verification.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: The repository method could accidentally return inactive historical
  ownership records.
  - Mitigation: Filter by `status = 'ACTIVE'` and `valid_until is null`; cover
    this in repository tests.
- Risk: The frontend panel could become too visually heavy.
  - Mitigation: Keep the list compact and reuse the current neutral account
    panel styling.
- Risk: Session-only access may be missed by tests.
  - Mitigation: Add controller tests for authenticated and unauthenticated
    requests.

Rollback:

- Remove the new endpoint, use case, repository method, frontend proxy/helper,
  UI additions, tests, and OpenAPI entries. No database migration is required.

## Verification Plan

- Run backend tests for the API module.
- Run frontend lint and production build.
- Run `git diff --check`.
- If practical, manually verify that an authenticated frontend session can call
  `/api/me/owned-ranges` through the same-origin proxy.

## Open Questions

- None. The current UI will list active owned ranges only.

## Progress Log

- 2026-06-18: Confirmed `main` is up to date and includes the frontend logout
  proxy fix.
- 2026-06-18: Created `feature/owned-ranges-read-ui` from `main`.
- 2026-06-18: Added the owner-scoped ownership listing port, use case, JDBC
  query, REST endpoint, frontend proxy/helper, account-panel UI, and OpenAPI
  contract updates.
- 2026-06-18: Verified backend tests, frontend lint/build, and a Docker Compose
  same-origin smoke flow returning an empty owned range list for a newly
  registered user.

## Completion Summary

Implemented authenticated owned range listing across the backend and frontend.
The backend now exposes `GET /api/me/owned-ranges`, derives identity from the
server-side session, returns active owned ranges only, and hides `ownerId` from
the user-scoped response. The frontend proxies the endpoint through Next.js and
shows owned seconds in the authenticated account panel with loading, empty, and
error states.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/domain/port/OwnershipRepository.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/ListCurrentUserOwnedRanges.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcOwnershipRepository.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/CurrentUserOwnedRangeController.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/CurrentUserOwnedRangeDtos.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/ListCurrentUserOwnedRangesTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest/CurrentUserOwnedRangeControllerTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/outbound/persistence/JdbcOwnershipRepositoryIntegrationTest.kt`
- Existing ownership repository fake implementations in application tests.
- `apps/web/src/app/api/me/owned-ranges/route.ts`
- `apps/web/src/lib/owned-ranges.ts`
- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `docs/api/openapi.yaml`
- `docs/implementation-plan/2026-06-18/add-owned-ranges-read-ui.md`

## Tests Run And Results

- `apps/api`: `.\gradlew.bat test --max-workers=2` passed.
- `apps/web`: `npm.cmd run lint` passed.
- `apps/web`: `npm.cmd run build` passed.

## Manual Verification Results

- `docker compose up -d --build` started the local stack, although the command
  exceeded the shell timeout while containers were already running.
- Verified `http://localhost:3000/api/csrf`, `POST /api/auth/register`, and
  `GET /api/me/owned-ranges` through the frontend proxy using one browser-like
  `WebRequestSession`.
- The owned range response for the newly registered user returned an empty list
  with `ownedRangesCount=0`.
- Ran `docker compose down` afterward without deleting volumes.

## Known Limitations

- The frontend only lists owned ranges. Upload actions remain a follow-up.
- The manual proxy smoke covered an empty owned-range list. Non-empty ownership
  display is covered by backend tests and should be exercised again after the
  purchase UI or a frontend purchase proxy exists.

## Follow-Up Recommendations

- Add the owned media upload flow next, using this owned range list as the
  starting point for selecting an upload target.
