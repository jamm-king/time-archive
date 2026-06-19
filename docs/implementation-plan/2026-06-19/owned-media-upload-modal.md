# Owned Media Upload Modal

## Objective

Make the signed-in `Owned Seconds` area compact and move owned range media
upload/manage actions into a focused modal.

## Scope

- Keep `Owned Seconds` as a concise list of owned time ranges.
- Replace inline file input and upload controls with a modal opened from each
  owned range item.
- Hide the native file input behind an English custom `Choose file` control.
- Show selected filename, upload status, retry/error states, and existing media
  status inside the modal.
- Preserve the existing upload API flow and moderation status behavior.

Out of scope:

- Admin moderation UI.
- Public timeline approval behavior.
- Multiple-file batch uploads.
- New backend APIs or database migrations.

## Relevant Files Or Modules

- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `apps/web/src/lib/owned-media.ts`
- `apps/web/src/lib/owned-ranges.ts`
- `docs/implementation-plan/2026-06-19/owned-media-upload-modal.md`

## Key Design Decisions

- The owned range list should not contain upload form controls. Each list item
  shows the time range, current ownership status, a compact media summary, and a
  `Manage media` action.
- The modal owns upload state so failed/selected files do not expand list rows.
- The file input remains in the DOM for accessibility, but it is visually hidden
  and triggered through a custom English label.
- Existing media assets are fetched per owned range item to show concise status
  in the list and detailed status in the modal.

## Step-By-Step Execution Plan

- [x] Create a dedicated feature branch from latest `main`.
- [x] Add this implementation plan.
- [x] Refactor owned range item into compact row plus modal action.
- [x] Add upload/manage modal with custom file chooser.
- [x] Preserve media list refresh after successful upload.
- [x] Run frontend lint/build checks.
- [x] Run relevant local verification script.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: Moving upload state into a modal could break media list refresh.
  - Mitigation: Keep media asset state in the owned range item and pass an
    upload completion callback from the modal.
- Risk: The modal may overflow on small screens.
  - Mitigation: Use fixed inset overlay, max viewport height, and internal
    scrolling only in modal content.
- Risk: Native file input accessibility could regress.
  - Mitigation: Keep an actual file input associated with a visible label.

Rollback:

- Revert component changes and this implementation plan. No backend data or API
  changes are involved.

## Verification Plan

- `npm.cmd run lint` in `apps/web`.
- `npm.cmd run build` in `apps/web`.
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-local-web-purchase-upload-flow.sh`
  against the local Docker Compose stack if practical.
- Manual visual verification if the Browser plugin is available.

## Open Questions

- None.

## Progress Log

- 2026-06-19: Confirmed `main` is up to date.
- 2026-06-19: Created `feature/owned-media-upload-modal`.
- 2026-06-19: Refactored owned range list items into compact rows and moved
  upload/media management into a modal with an English custom file chooser.
- 2026-06-19: Added Escape-key close handling and stable modal labelling.
- 2026-06-19: `npm.cmd run lint`, `npm.cmd run build`, `git diff --check`,
  Docker Compose full-stack build, and `verify-local-web-purchase-upload-flow.sh`
  passed.

## Completion Summary

Updated the signed-in `Owned Seconds` UI so owned ranges render as compact rows
with a concise media summary and a `Manage media` action. Upload controls now
live in an owned range media modal. The modal uses a visually hidden native file
input with custom English `Choose file` and `No file selected` text, preserving
the existing upload flow while avoiding browser-localized native file input
text in the visible UI.

## Files Changed

- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `docs/implementation-plan/2026-06-19/owned-media-upload-modal.md`

## Tests Run And Results

- `npm.cmd run lint`
  - Passed.
- `npm.cmd run build`
  - Passed.
- `git diff --check`
  - Passed.
- `docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d --build`
  - Passed.
- `START_SECOND=5040 END_SECOND=5041 ./scripts/verify-local-web-purchase-upload-flow.sh`
  - Passed.
- `docker compose down`
  - Passed.

## Manual Verification Results

- The full-stack Docker build confirmed the changed web application builds in
  the same container path used by CI.
- The web-origin purchase-upload verification script confirmed session, CSRF,
  purchase, owned range, upload request, presigned object upload, upload
  completion idempotency, and media listing behavior.

## Known Limitations

- This change does not add click-based browser automation. The visual modal
  behavior should still be manually checked in the browser before merge if
  possible.
- The modal does not implement focus trapping yet.

## Follow-Up Recommendations

- Add password confirmation to the registration form.
- Add minimal modal keyboard handling if the modal pattern grows beyond this
  first upload use case.
