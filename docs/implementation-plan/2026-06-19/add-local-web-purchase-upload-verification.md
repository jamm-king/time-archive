# Add Local Web Purchase Upload Verification

## Objective

Add web-origin shell verification scripts for the frontend purchase flow and the
combined purchase-to-upload MVP loop.

## Scope

- Add `scripts/verify-local-web-purchase-flow.sh`.
- Add `scripts/verify-local-web-purchase-upload-flow.sh`.
- Run both scripts against `BASE_URL=http://localhost:3000` by default.
- Wire both scripts into GitHub Actions in a combined local web flow job.
- Update README usage instructions.
- Stabilize local Docker Compose startup order for web-origin verification.

Out of scope:

- Browser automation.
- Real payment provider integration.
- Admin moderation UI or approval flow.
- Public timeline approval verification.

## Relevant Files Or Modules

- `scripts/verify-local-web-purchase-flow.sh`
- `scripts/verify-local-web-purchase-upload-flow.sh`
- `.github/workflows/ci.yml`
- `README.md`
- `docker-compose.yml`
- `apps/api/src/main/kotlin/com/timearchive/configuration/SecurityConfiguration.kt`
- Existing local verification scripts under `scripts/`

## Key Design Decisions

- The scripts target the Next.js web origin so they verify frontend proxy routes,
  session cookies, CSRF forwarding, purchase APIs, fake payment completion, and
  owned range reads together.
- The purchase-upload script directly uploads bytes to the returned presigned
  object storage URL, matching the browser-side architecture.
- The two scripts run in a single CI job to avoid paying full Docker Compose
  startup cost twice.
- The web container waits for the API health endpoint before startup so Next.js
  does not cache a stale or not-yet-ready upstream API connection during local
  stack recreation.

## Step-By-Step Execution Plan

- [x] Inspect existing local shell verification script patterns.
- [x] Add this implementation plan.
- [x] Add web purchase flow script.
- [x] Add web purchase-upload flow script.
- [x] Update README script instructions.
- [x] Add combined GitHub Actions job.
- [x] Add API healthcheck and web health dependency to Docker Compose.
- [x] Protect `/api/me/**` with the same authenticated security rule as
  `/api/me`.
- [x] Run shell syntax checks.
- [x] Run scripts against local Docker Compose if practical.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: CI runtime increases because the web-origin scripts require the full
  Docker Compose stack.
  - Mitigation: Run both scripts in one job.
- Risk: Range collisions can happen if a reused local database already owns the
  default ranges.
  - Mitigation: Support `START_SECOND` and `END_SECOND` overrides and choose
    separate default ranges from existing jobs.
- Risk: The upload script validates HTTP object upload, not browser CORS.
  - Mitigation: Keep browser CORS verification as a separate manual follow-up.

Rollback:

- Remove the two scripts, README entries, CI job, and this implementation plan.
  No app code or database migration rollback is required.

## Verification Plan

- `C:\Program Files\Git\bin\bash.exe -n` for both scripts.
- `git diff --check`.
- `docker compose up -d --build`.
- Run both scripts against `http://localhost:3000`.
- `docker compose down`.

## Open Questions

- None.

## Progress Log

- 2026-06-19: Confirmed `main` includes frontend purchase flow, purchase panel
  state fix, and owned media upload flow.
- 2026-06-19: Created `feature/local-web-purchase-upload-verification` from
  `main`.
- 2026-06-19: Added web-origin purchase and purchase-upload verification scripts.
- 2026-06-19: Added README entries and a combined GitHub Actions job for the
  web-origin flow scripts.
- 2026-06-19: Web-origin verification exposed a Docker Compose readiness issue:
  the web container could start before the recreated API was healthy and keep a
  stale upstream connection target. Added an API healthcheck and made web depend
  on API health.
- 2026-06-19: Web-origin verification also exposed that `/api/me/owned-ranges`
  was not covered by the `/api/me` authenticated matcher. Added `/api/me/**` to
  the authenticated security rules so current-user subresources use the same
  security boundary.

## Completion Summary

Added web-origin shell verification for the purchase flow and the combined
purchase-to-upload MVP loop. Both scripts exercise the Next.js API routes,
session cookies, CSRF forwarding, fake payment completion, current-user owned
range reads, and upload completion behavior. The purchase-upload script also
checks upload completion idempotency.

During verification, the new script exposed two local-stack issues:

- The web container could start before the recreated API container was healthy.
- `/api/me/owned-ranges` was not covered by the same authenticated Spring
  Security matcher as `/api/me`.

Both were fixed as part of this task because the scripts cannot be reliable CI
gates without those boundaries.

## Files Changed

- `.github/workflows/ci.yml`
- `README.md`
- `docker-compose.yml`
- `apps/api/src/main/kotlin/com/timearchive/configuration/SecurityConfiguration.kt`
- `scripts/verify-local-web-purchase-flow.sh`
- `scripts/verify-local-web-purchase-upload-flow.sh`
- `docs/implementation-plan/2026-06-19/add-local-web-purchase-upload-verification.md`

## Tests Run And Results

- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-local-web-purchase-flow.sh`
  - Passed.
- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-local-web-purchase-upload-flow.sh`
  - Passed.
- `git diff --check`
  - Passed.
- `.\gradlew.bat test --max-workers=2`
  - Passed.
- `.\gradlew.bat bootJar --no-daemon`
  - Passed.

## Manual Verification Results

- `docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d --build --force-recreate api web`
  - Passed. API reached healthy state before web startup.
- `START_SECOND=4030 END_SECOND=4031 ./scripts/verify-local-web-purchase-flow.sh`
  - Passed.
- `START_SECOND=5030 END_SECOND=5031 ./scripts/verify-local-web-purchase-upload-flow.sh`
  - Passed.
- `curl http://localhost:8080/api/me/owned-ranges` without authentication
  - Returned `401 AUTHENTICATION_REQUIRED` after the security matcher fix.

## Known Limitations

- These scripts validate web-origin API and direct presigned URL object upload.
  They do not validate browser CORS behavior or UI click paths.
- Default local ranges can collide when a reused local database already contains
  purchases from previous verification runs. Override `START_SECOND` and
  `END_SECOND` in that case.

## Follow-Up Recommendations

- Keep these scripts as required CI gates for PRs that touch purchase, auth,
  upload, owned range, web proxy, or Docker Compose behavior.
- Add browser-level upload verification later if CORS or real object storage
  origin behavior becomes risky.
