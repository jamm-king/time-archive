# Add CSRF Protection

## Objective

Enable CSRF protection for browser session authenticated mutation APIs while
keeping server-to-server fake payment webhook verification outside the CSRF
boundary.

## Scope

- Enable Spring Security CSRF protection for session-based browser APIs.
- Use a cookie-readable CSRF token so the web client and verification scripts
  can echo it through `X-XSRF-TOKEN`.
- Add a minimal CSRF bootstrap endpoint.
- Exempt the development-stage fake payment webhook endpoint because it models a
  server-to-server payment provider callback.
- Update local verification scripts to fetch and submit CSRF tokens.
- Update OpenAPI, README, and security documentation.

## Out of Scope

- Payment provider webhook signature verification.
- Frontend CSRF client integration beyond documenting the API contract.
- CSRF protection for direct object storage PUT uploads.
- Changing session cookie SameSite/Secure deployment settings.

## Relevant Files and Modules

- `apps/api/src/main/kotlin/com/timearchive/configuration/SecurityConfiguration.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest`
- `scripts/verify-local-purchase-flow.sh`
- `scripts/verify-local-media-upload-flow.sh`
- `scripts/verify-local-public-timeline-flow.sh`
- `docs/api/openapi.yaml`
- `docs/architecture/security-and-operations.md`
- `README.md`

## Key Design Decisions

- Use Spring Security `CookieCsrfTokenRepository` with a cookie-readable token.
  This matches a browser-first same-origin app: JavaScript reads the token cookie
  and sends it in `X-XSRF-TOKEN` for mutating API calls.
- Add `GET /api/csrf` as a bootstrap endpoint so clients and scripts can obtain
  a token before sending mutations.
- Keep the fake payment webhook endpoint CSRF-exempt. It is not a browser form
  endpoint and must later be protected by provider webhook signature
  verification.
- Do not add dependencies.

## Execution Plan

- [x] Create a dedicated feature branch from latest `main`.
- [x] Document this implementation plan.
- [x] Add CSRF bootstrap REST endpoint and tests.
- [x] Enable Spring Security CSRF with cookie token repository.
- [x] Exempt fake payment webhook endpoint from CSRF.
- [x] Update shell verification scripts to fetch and submit CSRF tokens.
- [x] Update API and security documentation.
- [x] Run focused and full verification.
- [x] Record completion details in this plan.

## Risks and Rollback Strategy

- Risk: Verification scripts and CI can fail if token handling is incomplete.
  - Mitigation: Add a shared shell helper pattern in each script and run all
    Docker Compose flows.
- Risk: Internal fake webhook calls could be blocked by CSRF.
  - Mitigation: Explicitly ignore the fake webhook route in CSRF config and
    document that signature verification is the correct protection.
- Risk: The readable CSRF token cookie may be confused with an authentication
  secret.
  - Mitigation: Document that it is not an auth secret and must be paired with
    the HttpOnly session cookie.
- Rollback: Disable CSRF in `SecurityConfiguration` and remove script token
  handling in a single revert if it blocks local development unexpectedly.

## Verification Plan

- Run API tests with `./gradlew test --max-workers=2`.
- Run API build with `./gradlew build`.
- Syntax-check shell scripts.
- Run Docker Compose API stack and verify local purchase, media upload, and
  public timeline flows.
- Run `git diff --check`.

## Open Questions

- None.

## Progress Log

- 2026-06-18: Confirmed `main` includes session identity for purchase, owned
  media, and admin moderation APIs.
- 2026-06-18: Created `feature/csrf-protection`.
- 2026-06-18: Added `GET /api/csrf`, enabled cookie-backed CSRF protection,
  and exempted the fake payment webhook endpoint.
- 2026-06-18: Updated local verification scripts to fetch CSRF tokens before
  mutation calls and refresh tokens after authentication changes the session.
- 2026-06-18: Verified API tests, API build, shell syntax, Docker Compose local
  flows, and diff whitespace.

## Completion Summary

CSRF protection is now enabled for browser-facing mutating APIs. Clients fetch a
CSRF token through `GET /api/csrf`, retain the cookie jar, and send the token in
`X-XSRF-TOKEN` for subsequent mutation requests.

The fake payment webhook endpoint remains CSRF-exempt because it represents a
server-to-server callback. It still needs provider signature verification before
production.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/CsrfController.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest/CsrfControllerTest.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/SecurityConfiguration.kt`
- `scripts/verify-local-purchase-flow.sh`
- `scripts/verify-local-media-upload-flow.sh`
- `scripts/verify-local-public-timeline-flow.sh`
- `docs/api/openapi.yaml`
- `docs/architecture/security-and-operations.md`
- `docs/manual-verification/local-purchase-flow.md`
- `docs/manual-verification/local-media-upload-flow.md`
- `docs/manual-verification/local-public-timeline-flow.md`
- `README.md`
- `docs/implementation-plan/2026-06-18/add-csrf-protection.md`

## Tests Run and Results

- `./gradlew test --max-workers=2` in `apps/api`: passed.
- `./gradlew build` in `apps/api`: passed.
- `bash -n scripts/verify-local-purchase-flow.sh`: passed.
- `bash -n scripts/verify-local-media-upload-flow.sh`: passed.
- `bash -n scripts/verify-local-public-timeline-flow.sh`: passed.
- `START_SECOND=42310 END_SECOND=42320 ./scripts/verify-local-purchase-flow.sh`: passed.
- `START_SECOND=42320 END_SECOND=42321 ./scripts/verify-local-media-upload-flow.sh`: passed.
- `START_SECOND=42330 END_SECOND=42331 ./scripts/verify-local-public-timeline-flow.sh`: passed.
- `git diff --check`: passed.

## Manual Verification Results

Docker Compose local API stack was rebuilt and started with:

```text
docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d --build api
```

The local purchase, media upload, and public timeline scripts completed
successfully. Each script fetched a CSRF token, refreshed it after session
authentication, and sent it with mutating browser API requests. The fake payment
webhook calls continued to work without CSRF protection.

The stack was stopped with:

```text
docker compose down
```

## Known Limitations

- CSRF is configured for the current same-origin browser model. Deployment
  cookie attributes such as `Secure`, `SameSite`, and domain scoping still need
  production review.
- Fake payment webhook protection still depends on future provider signature
  verification.
- Frontend client code still needs to call `GET /api/csrf` before mutations.

## Follow-up Recommendations

- Wire the frontend fetch client to bootstrap and send `X-XSRF-TOKEN`.
- Add production cookie policy review before deployment.
- Implement real payment provider webhook signature verification.
