# Purchase Duration Selection

## Objective

Change the frontend purchase flow so `BUY THIS SECOND` opens a local duration
draft first. A backend reservation must be created only after the user selects a
valid duration and confirms the range.

## Scope

- Keep the CTA label as `BUY THIS SECOND`.
- Use the current unclaimed archive second as the fixed purchase start second.
- Add duration presets based on the confirmed product rule.
- Clamp direct duration input to the available maximum.
- Check backend availability for the selected range before creating a
  reservation.
- Keep the existing fake local payment completion flow after reservation.

Out of scope:

- Reservation cancellation API.
- Purchase offers to existing owners.
- Owned seconds upload modal redesign.
- Browser automation.

## Relevant Files Or Modules

- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `apps/web/src/lib/purchase-duration.ts`
- `apps/web/src/lib/purchase.ts`
- `docs/implementation-plan/2026-06-19/purchase-duration-selection.md`

## Key Design Decisions

- `BUY THIS SECOND` remains the user-facing CTA because it matches the product
  message of buying one or more seconds from the current unclaimed second.
- The frontend computes the maximum duration through backend availability
  checks before showing duration controls. Backend availability remains the
  final authority before reservation.
- Presets follow the product rule:
  - Base presets are `1s`, `5s`, `10s`, `15s`, `30s`.
  - Show base presets less than or equal to `maxDuration`.
  - If `maxDuration < 30` and is not one of `1`, `5`, `10`, or `15`, append
    `maxDuration`.
  - Do not append `maxDuration` when it is `30` or greater.
- Direct input is clamped to `[1, maxDuration]`, with a short explanation when
  the input exceeds the maximum.

## Step-By-Step Execution Plan

- [x] Create a dedicated feature branch from the latest `main`.
- [x] Add this implementation plan.
- [x] Add purchase duration helper functions.
- [x] Update `PurchaseCurrentSecondPanel` to use draft-first reservation flow.
- [x] Wire max duration calculation through backend availability checks.
- [x] Add direct input clamping and preset UI.
- [x] Run frontend lint/build checks.
- [x] Run relevant local verification scripts.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: Timeline data may not include all future claimed ranges.
  - Mitigation: Use backend availability as the final gate before reservation.
- Risk: The purchase flow becomes more complex and creates stale UI state.
  - Mitigation: Keep reservation/payment state separate from draft duration
    state and reset draft on current second changes.
- Risk: Existing scripts validate API flow, not this UI behavior.
  - Mitigation: Run existing scripts for regression coverage and manually verify
    the web UI behavior locally if possible.

Rollback:

- Revert the helper module, component changes, and this implementation plan.
  Backend data and database migrations are not affected.

## Verification Plan

- `npm.cmd run lint` in `apps/web`.
- `npm.cmd run build` in `apps/web`.
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-local-web-purchase-flow.sh`
  against the local Docker Compose stack if practical.
- Manual browser or HTTP-level verification of the draft-first purchase flow if
  practical.

## Open Questions

- None.

## Progress Log

- 2026-06-19: Confirmed `main` is up to date.
- 2026-06-19: Created `feature/purchase-duration-selection`.
- 2026-06-19: Added duration preset/clamp helpers and backend availability
  based maximum duration lookup.
- 2026-06-19: Updated the purchase panel to open a duration draft before
  reservation and to create reservations only from an available draft range.
- 2026-06-19: `npm.cmd run lint` and `npm.cmd run build` passed for `apps/web`.
- 2026-06-19: Docker Compose full-stack build passed and
  `verify-local-web-purchase-flow.sh` passed against `http://localhost:3000`.
- 2026-06-19: In-app Browser verification was attempted but could not start due
  to the local Browser plugin error `failed to write kernel assets`.

## Completion Summary

Implemented draft-first frontend purchase duration selection. `BUY THIS SECOND`
now opens a purchase draft for the current unclaimed archive second instead of
creating a reservation immediately. Users can choose a duration preset or enter
a custom duration, and reservations are created only after the selected range is
available.

The preset rule is implemented as:

- Base presets: `1s`, `5s`, `10s`, `15s`, `30s`.
- Show base presets less than or equal to `maxDuration`.
- If `maxDuration < 30` and is not `1`, `5`, `10`, or `15`, append
  `maxDuration`.
- Do not append `maxDuration` when it is `30` or greater.

## Files Changed

- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `apps/web/src/lib/purchase.ts`
- `apps/web/src/lib/purchase-duration.ts`
- `docs/implementation-plan/2026-06-19/purchase-duration-selection.md`

## Tests Run And Results

- `npm.cmd run lint`
  - Passed.
- `npm.cmd run build`
  - Passed.
- `git diff --check`
  - Passed.
- `docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d --build`
  - Passed.
- `START_SECOND=4040 END_SECOND=4041 ./scripts/verify-local-web-purchase-flow.sh`
  - Passed.
- `docker compose down`
  - Passed.

## Manual Verification Results

- The full-stack Docker build confirmed the changed web application builds in
  the same container path used by CI.
- The web-origin purchase verification script confirmed session, CSRF, web
  proxy, reservation, checkout, fake payment completion, owned range listing,
  and final availability behavior.
- In-app Browser verification could not be completed because the Browser plugin
  failed during initialization with a local kernel asset path error.

## Known Limitations

- There is no browser-level automated assertion for the new duration draft UI in
  this change because Browser automation could not initialize locally.
- `findMaxAvailableDuration` uses backend availability checks to find the
  maximum duration. This is accurate but can make several requests when opening
  the purchase draft.

## Follow-Up Recommendations

- Add browser-level verification once the local Browser plugin issue is fixed.
- Continue with Owned Seconds layout cleanup and upload modal separation after
  this purchase UX change is merged.
