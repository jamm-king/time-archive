# Admin Moderation UI

## Objective

Add a minimal frontend admin moderation experience for reviewing uploaded media
and approving or rejecting it through the existing admin moderation APIs.

## Scope

- Add role to the current-user response so the web app can show admin controls.
- Add Next.js proxy routes for existing admin moderation backend endpoints.
- Add frontend admin moderation client helpers.
- Add a compact admin moderation entry point for authenticated admin users.
- Open the moderation queue in a dedicated modal.
- Support listing media by moderation status.
- Support approving uploaded media with an approved URL.
- Support rejecting uploaded media.

Out of scope:

- Admin account management.
- Media transcoding, thumbnail generation, malware scanning, or derived asset
  processing.
- Full moderation queue pagination.
- Browser automation.

## Relevant Files Or Modules

- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/AuthDtos.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest/AuthControllerTest.kt`
- `apps/web/src/lib/auth.ts`
- `apps/web/src/lib/admin-moderation.ts`
- `apps/web/src/app/api/admin/media/assets/**`
- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `docs/implementation-plan/2026-06-19/admin-moderation-ui.md`

## Key Design Decisions

- The backend admin moderation use cases already exist and remain the source of
  authorization truth.
- The current-user response gains an additive `role` field. This is not a
  breaking API change and lets the web app decide whether to display admin UI.
- The signed-in account panel only exposes a compact admin entry point. The
  moderation queue opens in a dedicated modal so long asset data does not make
  the account panel difficult to use.
- Approval defaults the approved file URL to the original file URL so local
  MinIO/R2-compatible uploads can be approved without a separate processing
  pipeline.

## Step-By-Step Execution Plan

- [x] Create a dedicated feature branch from latest `main`.
- [x] Add this implementation plan.
- [x] Add role to current user response and tests.
- [x] Add web proxy routes for admin moderation APIs.
- [x] Add frontend admin moderation helper.
- [x] Add admin moderation modal UI for admin users.
- [x] Run backend and frontend checks.
- [x] Run relevant local verification script.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: The role field could be confused as authorization.
  - Mitigation: Treat it only as UI gating; backend admin API continues to
    enforce permissions from the server-side session.
- Risk: Approval URL defaults to original file URL.
  - Mitigation: Document this as local/MVP behavior until processing generates
    separate approved derivatives.
- Risk: Account panel can become dense.
  - Mitigation: Keep only the admin entry point in the account panel and move
    the queue into a modal.

Rollback:

- Revert the additive current-user role field, web proxy routes, UI helper,
  component changes, and this implementation plan.

## Verification Plan

- `.\gradlew.bat test --max-workers=2` in `apps/api`.
- `npm.cmd run lint` in `apps/web`.
- `npm.cmd run build` in `apps/web`.
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-local-public-timeline-flow.sh`
  against the local Docker Compose stack if practical.

## Open Questions

- None.

## Progress Log

- 2026-06-19: Confirmed `main` is up to date.
- 2026-06-19: Created `feature/admin-moderation-ui`.
- 2026-06-19: Added an additive `role` field to current-user responses and web
  auth parsing.
- 2026-06-19: Added web proxy routes for admin moderation list, approve,
  reject, and hide operations.
- 2026-06-19: Added frontend admin moderation helper for list, approve, reject,
  and hide operations.
- 2026-06-19: Added compact admin moderation entry point in the signed-in
  account panel for admin users.
- 2026-06-19: Moved the moderation queue into a dedicated modal with status
  filters, refresh, close, Escape close, and original-file links.
- 2026-06-19: Backend tests, frontend lint/build, Docker Compose full-stack
  build, API-origin public timeline moderation flow, and web-origin admin proxy
  list/approve verification passed.
- 2026-06-19: Reran frontend lint/build and whitespace checks after moving
  the moderation queue to a modal.
- 2026-06-19: Constrained moderation modal and media asset cards so long
  original-file URLs cannot expand the queue horizontally.

## Completion Summary

Added a minimal admin moderation experience to the web app. Admin users now see
an `Open moderation` entry point inside the signed-in account panel. The
moderation queue opens in a dedicated modal, where admins can list media by
moderation status and perform approve, reject, and hide actions through
web-origin proxy routes backed by the existing server-side admin moderation
APIs.

The current-user response now includes an additive `role` field. The frontend
uses that field only to decide whether to display admin UI; the backend remains
the authorization authority and still validates admin permissions from the
server-side session.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/AuthDtos.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest/AuthControllerTest.kt`
- `apps/web/src/app/api/admin/media/assets/route.ts`
- `apps/web/src/app/api/admin/media/assets/[mediaAssetId]/approve/route.ts`
- `apps/web/src/app/api/admin/media/assets/[mediaAssetId]/reject/route.ts`
- `apps/web/src/app/api/admin/media/assets/[mediaAssetId]/hide/route.ts`
- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `apps/web/src/lib/admin-moderation.ts`
- `apps/web/src/lib/auth.ts`
- `docs/implementation-plan/2026-06-19/admin-moderation-ui.md`

## Tests Run And Results

- `npm.cmd run lint`
  - Passed.
- `npm.cmd run build`
  - Passed.
- `.\gradlew.bat test --max-workers=2`
  - Passed.
- `git diff --check`
  - Passed.
- `npm.cmd run lint` after modal conversion
  - Passed.
- `npm.cmd run build` after modal conversion
  - Passed.
- `git diff --check` after modal conversion
  - Passed.
- `npm.cmd run lint` after media card width constraint
  - Passed.
- `npm.cmd run build` after media card width constraint
  - Passed.
- `git diff --check` after media card width constraint
  - Passed.
- `.\gradlew.bat bootJar --no-daemon`
  - Passed.
- `docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d --build`
  - Passed.
- `START_SECOND=3050 END_SECOND=3051 ./scripts/verify-local-public-timeline-flow.sh`
  - Passed.
- `START_SECOND=5060 END_SECOND=5061 ./scripts/verify-local-web-purchase-upload-flow.sh`
  - Passed.
- Web-origin admin login, uploaded media list, and approve request using
  `http://localhost:3000/api/admin/media/assets`
  - Passed.
- `docker compose down`
  - Passed.

## Manual Verification Results

- Admin login through the web origin returned `role: ADMIN`.
- Admin media list through the web origin returned at least one `UPLOADED`
  media asset.
- Admin approve through the web origin returned `moderationStatus: APPROVED`.

## Known Limitations

- The admin moderation experience is modal-based. It is not yet a dedicated
  admin dashboard.
- The modal can be closed by button or Escape, but it does not yet implement a
  full focus trap.
- Approval defaults to the original uploaded file URL in the UI because the MVP
  does not yet have a processing pipeline that creates separate approved
  derivatives.
- There is no pagination for the moderation queue.
- Browser-level click automation was not added.

## Follow-Up Recommendations

- Add a dedicated admin page if moderation queue volume grows.
- Add moderation reason persistence before production moderation workflows.
- Add derived media processing before using separate public approved assets.
