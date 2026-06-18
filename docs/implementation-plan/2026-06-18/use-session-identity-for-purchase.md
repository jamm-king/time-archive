# Use Session Identity for Purchase

## Objective

Replace request-body `buyerId` in purchase reservation creation with
server-side session identity.

## Scope

- Require authenticated server-side session identity for purchase reservation
  creation.
- Require authenticated server-side session identity for checkout creation.
- Ensure checkout creation is only allowed by the user who owns the reservation.
- Remove `buyerId` from `CreateReservationRequest`.
- Update local verification scripts to register a user and preserve the session
  cookie for purchase calls.
- Update OpenAPI and documentation that described `buyerId` as temporary
  request input.

## Out of Scope

- Migrating owned media APIs from `X-User-Id` to session identity.
- Frontend auth UI.
- Payment provider integration.
- Admin authentication.
- CSRF protection.

## Relevant Files or Modules

- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/PurchaseController.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/PurchaseDtos.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/CreateCheckout.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/SecurityConfiguration.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest/PurchaseControllerTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/CreateCheckoutTest.kt`
- `scripts/verify-local-purchase-flow.sh`
- `scripts/verify-local-media-upload-flow.sh`
- `scripts/verify-local-public-timeline-flow.sh`
- `docs/api/openapi.yaml`

## Key Design Decisions

- The client no longer sends `buyerId` for purchase reservation creation.
- The reservation owner is derived from the authenticated server-side session.
- Checkout creation validates that the current session user owns the requested
  reservation.
- Existing media APIs still use `X-User-Id` in this step. The scripts continue
  to use the registered user's ID as that header until the next migration.

## Step-by-Step Execution Plan

- [x] Update `main` and create a dedicated feature branch.
- [x] Create this implementation plan.
- [x] Update purchase DTO and controller to use session identity.
- [x] Update checkout use case to authorize by reservation buyer.
- [x] Keep purchase authentication enforced in the controller.
- [x] Update purchase controller and checkout use case tests.
- [x] Update local verification scripts to register a user and use session
  cookies for purchase calls.
- [x] Update OpenAPI and docs.
- [x] Run backend tests and local verification scripts.
- [x] Update this plan with completion details.
- [ ] Commit and push the branch.

## Risks and Rollback Strategy

- Risk: This is a public API request shape change for reservation creation.
  - Mitigation: Update OpenAPI and all local verification scripts in the same
    change.
- Risk: Checkout ownership enforcement may break existing callers that created
  checkout without a session.
  - Mitigation: This is intentional because checkout is part of the purchase
    identity boundary.
- Rollback: Revert controller, use case, script, and documentation changes. No
  database schema changes are required.

## Verification Plan

- `.\gradlew.bat test --max-workers=2` under `apps/api`
- `.\gradlew.bat build` under `apps/api`
- `docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d --build api`
- `bash ./scripts/verify-local-purchase-flow.sh`
- `bash ./scripts/verify-local-media-upload-flow.sh`
- `bash ./scripts/verify-local-public-timeline-flow.sh`
- `docker compose down`
- `git diff --check`

## Open Questions

- None.

## Progress Log

- 2026-06-18: Confirmed `main` contained PR #29.
- 2026-06-18: Created `feature/session-purchase-identity`.
- 2026-06-18: Created implementation plan.
- 2026-06-18: Removed `buyerId` from reservation request DTO.
- 2026-06-18: Updated purchase reservation creation to derive buyer identity
  from the server-side session.
- 2026-06-18: Updated checkout creation to require the current session user to
  own the reservation.
- 2026-06-18: Kept purchase authentication checks in the controller. A temporary
  Spring Security matcher-based check caused Git Bash curl verification to lose
  the expected session path, while controller-level session enforcement provides
  the required 401 behavior for this scope.
- 2026-06-18: Disabled session fixation ID rotation for the current
  server-side session adapter so repeated curl requests can keep the same
  session cookie.
- 2026-06-18: Updated local purchase, media upload, and public timeline
  verification scripts to register local users and use session cookies for
  purchase calls.
- 2026-06-18: Updated OpenAPI, README, security docs, and manual purchase
  verification docs.
- 2026-06-18: Verified backend tests, backend build, Docker Compose CI config,
  and all three local shell verification flows.

## Completion Summary

Purchase APIs now use authenticated server-side session identity for buyer
selection.

Reservation creation no longer accepts `buyerId` in the request body. Checkout
creation requires the current session user to match the reservation buyer before
creating a checkout session.

The owned media APIs still use `X-User-Id` as a development-stage identity
header. Verification scripts now create a local authenticated user first, use
that session for purchase calls, and continue to pass the registered `userId`
as `X-User-Id` for media calls until the next migration.

## Files Changed

- `README.md`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/AuthController.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/CurrentUserSession.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/PurchaseController.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/PurchaseDtos.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/CreateCheckout.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/SecurityConfiguration.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest/PurchaseControllerTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/CreateCheckoutTest.kt`
- `docs/api/openapi.yaml`
- `docs/architecture/security-and-operations.md`
- `docs/manual-verification/local-purchase-flow.md`
- `scripts/verify-local-purchase-flow.sh`
- `scripts/verify-local-media-upload-flow.sh`
- `scripts/verify-local-public-timeline-flow.sh`
- `docs/implementation-plan/2026-06-18/use-session-identity-for-purchase.md`

## Tests Run and Results

- `.\gradlew.bat test --max-workers=2` under `apps/api` - passed
- `.\gradlew.bat build` under `apps/api` - passed
- `docker compose -f docker-compose.yml -f docker-compose.ci.yml config` -
  passed
- `docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d --build api` -
  passed
- `START_SECOND=41140 END_SECOND=41150 bash ./scripts/verify-local-purchase-flow.sh` -
  passed through Git Bash
- `START_SECOND=41200 END_SECOND=41201 bash ./scripts/verify-local-media-upload-flow.sh` -
  passed through Git Bash
- `START_SECOND=41300 END_SECOND=41301 bash ./scripts/verify-local-public-timeline-flow.sh` -
  passed through Git Bash
- `docker compose down` - passed
- `git diff --check` - passed

## Manual Verification Results

Verified that local scripts register a session-authenticated user, create a
reservation without request-body `buyerId`, create checkout with the same
session, and continue through payment completion, media upload, and public
timeline publication.

## Known Limitations

- Owned media APIs still require `X-User-Id`.
- Admin APIs still require `X-Admin-Id`.
- Purchase API authentication is enforced at the controller boundary in this
  step. A broader Spring Security authorization model should be revisited when
  the remaining API groups migrate to session identity and roles.
- CSRF protection is still disabled for local MVP verification.

## Follow-up Recommendations

- Migrate owned media APIs from `X-User-Id` to session-derived current user
  identity.
- Introduce an admin role model before replacing `X-Admin-Id`.
- Revisit Spring Security authorization matchers once all API identity inputs
  are session-derived.
- Add CSRF protection before exposing cookie-authenticated mutation APIs outside
  local/staging verification.
