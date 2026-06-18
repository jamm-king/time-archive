# Add Frontend Owned Media Upload Flow

## Objective

Let authenticated users upload media for their owned archive ranges from the
frontend account panel.

## Scope

- Add Next.js same-origin proxy routes for owned range media APIs:
  - list media assets for an owned range
  - create a media upload request
  - complete a media upload request
- Add frontend client helpers for owned media listing and upload completion.
- Add a minimal upload UI under each owned range in the authenticated account
  panel.
- Show loading, empty, upload progress, success, and error states.
- Keep the uploaded media pending moderation; do not add admin approval UI here.
- Update documentation and this implementation plan.

Out of scope:

- Backend domain or database changes.
- Purchase UI.
- Admin moderation UI.
- Public timeline refresh after moderation approval.
- Browser automation or a new CI verification script.

## Relevant Files Or Modules

- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `apps/web/src/lib/owned-ranges.ts`
- `apps/web/src/lib/owned-media.ts`
- `apps/web/src/app/api/owned-ranges`
- `apps/web/src/lib/backend-proxy.ts`
- Existing backend APIs under `/api/owned-ranges/{ownershipRecordId}/media`
- `docs/api/openapi.yaml`

## Key Design Decisions

- The frontend remains same-origin for authenticated backend API calls through
  Next route handlers.
- File bytes are uploaded directly to the backend-issued presigned upload URL.
  This preserves the existing S3-compatible upload architecture and avoids
  routing large media files through the web server.
- The upload UI is attached to owned ranges because upload authorization is
  range-scoped.
- Media type is inferred from the selected file MIME type and limited to image
  and video files.
- The first UI iteration is compact and account-panel scoped. It does not add a
  separate dashboard or new navigation.

## Step-By-Step Execution Plan

- [x] Inspect existing backend upload API and frontend account panel.
- [x] Add this implementation plan.
- [x] Add Next.js proxy routes for owned range media list, upload request, and
  completion.
- [x] Add frontend owned media helper functions and response validation.
- [x] Extend the owned range list UI with per-range media list and upload form.
- [x] Run frontend lint/build.
- [x] Manually verify the upload flow against Docker Compose if practical.
- [x] Update the implementation plan with completion details.

## Risks And Rollback Strategy

- Risk: Browser direct upload to MinIO may fail if local object storage CORS is
  not compatible with the presigned upload URL.
  - Mitigation: Verify manually in the local Docker Compose environment and
    document the result.
- Risk: The account panel can become too dense.
  - Mitigation: Keep copy short and controls compact; only render upload controls
    inside owned range items.
- Risk: Large files can make the UI feel stuck.
  - Mitigation: Add clear uploading/completing states, but keep progress
    percentages out of scope for this first iteration.

Rollback:

- Remove the new web proxy routes, frontend owned media helper, UI additions,
  and documentation updates. No database or backend rollback is required.

## Verification Plan

- `npm.cmd run lint` in `apps/web`.
- `npm.cmd run build` in `apps/web`.
- `git diff --check`.
- If practical, run Docker Compose, create or reuse an owned range, upload a
  small local image through the frontend, and verify the resulting media asset
  appears as `UPLOADED`.

## Open Questions

- None for implementation. Local MinIO browser CORS behavior will be verified.

## Progress Log

- 2026-06-18: Confirmed `main` is up to date and includes owned range listing
  and local auth verification script work.
- 2026-06-18: Created `feature/frontend-owned-media-upload-flow` from `main`.
- 2026-06-18: Added Next.js proxy routes for owned range media listing, upload
  request creation, and upload completion.
- 2026-06-18: Added frontend owned media helpers and account-panel upload UI.
- 2026-06-18: Verified frontend lint/build and a local Docker Compose HTTP
  upload flow through the web-origin proxy routes.

## Completion Summary

Implemented the first frontend owned media upload flow. Authenticated users can
open the account panel, view owned ranges, see existing media metadata for each
range, select an image or video file, create a server-tracked upload request,
upload the file to the presigned object storage URL, complete the upload, and
see the resulting `UPLOADED` media asset in the range summary.

## Files Changed

- `apps/web/src/app/api/owned-ranges/[ownershipRecordId]/media/route.ts`
- `apps/web/src/app/api/owned-ranges/[ownershipRecordId]/media/upload-requests/route.ts`
- `apps/web/src/app/api/owned-ranges/[ownershipRecordId]/media/upload-requests/[uploadRequestId]/complete/route.ts`
- `apps/web/src/lib/auth.ts`
- `apps/web/src/lib/owned-media.ts`
- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `docs/implementation-plan/2026-06-18/add-frontend-owned-media-upload-flow.md`

## Tests Run And Results

- `apps/web`: `npm.cmd run lint` passed.
- `apps/web`: `npm.cmd run build` passed.
- `git diff --check` passed.

## Manual Verification Results

- `docker compose up -d --build` passed.
- Created a local authenticated user through `http://localhost:3000`.
- Created a local owned range for that user through the existing fake purchase
  flow.
- Created an upload request through
  `http://localhost:3000/api/owned-ranges/{ownershipRecordId}/media/upload-requests`.
- Uploaded bytes to the returned presigned MinIO URL.
- Completed the upload through
  `http://localhost:3000/api/owned-ranges/{ownershipRecordId}/media/upload-requests/{uploadRequestId}/complete`.
- Confirmed the created media asset was listed through
  `http://localhost:3000/api/owned-ranges/{ownershipRecordId}/media` with
  `moderationStatus=UPLOADED`.
- `docker compose down` completed after verification.

## Known Limitations

- The upload UI uses direct browser upload to the presigned object storage URL.
  The HTTP flow was verified, but the in-app browser plugin failed to initialize
  in this environment, so browser-level MinIO CORS behavior was not verified.
- Uploaded media remains pending moderation and does not become publicly visible
  until admin approval.
- The UI shows compact status states but does not show byte-level upload
  progress.

## Follow-Up Recommendations

- Add a web-origin upload verification script after browser/object-storage CORS
  behavior is confirmed.
- Add admin moderation frontend next so uploaded media can move from `UPLOADED`
  to public visibility.
