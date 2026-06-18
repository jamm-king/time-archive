# Fix Frontend Purchase Panel State

## Objective

Keep the development-stage purchase panel state stable after a user reserves the
current second, so the `Complete local payment` action remains available until
the user completes or retries the flow.

## Scope

- Fix the frontend purchase panel remount behavior.
- Prevent repeated reservation attempts while a reservation is already pending.
- Re-run frontend lint/build checks.

Out of scope:

- Real payment provider integration.
- New backend APIs.
- Purchase history or arbitrary second selection.

## Relevant Files Or Modules

- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `docs/implementation-plan/2026-06-18/add-frontend-purchase-flow.md`

## Key Design Decisions

- The purchase panel should not be keyed by `currentSecond` because the clock
  changes every second and remounts the component.
- The panel may still reset when the authenticated user changes.
- Once a reservation is created, the primary buy button should no longer create
  another reservation for a later ticking second.

## Step-By-Step Execution Plan

- [x] Inspect current purchase panel state behavior.
- [x] Add this implementation plan.
- [x] Remove the per-second remount key.
- [x] Disable repeated reservation while reserved, completing, or complete.
- [x] Run frontend lint/build and diff checks.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: Without remounting every second, the displayed purchase target could feel
  stale during long idle sessions.
  - Mitigation: Idle state still receives the latest `currentSecond`; only
    existing state is preserved after reservation.

Rollback:

- Restore the previous key and button state logic in
  `PublicTimelinePlayer.tsx`.

## Verification Plan

- `npm.cmd run lint` in `apps/web`.
- `npm.cmd run build` in `apps/web`.
- `git diff --check`.

## Open Questions

- None.

## Progress Log

- 2026-06-19: Confirmed the purchase panel was keyed by
  `${currentUser?.userId ?? "guest"}-${currentSecond}`, causing a remount every
  second.
- 2026-06-19: Changed the key to depend only on the authenticated user and
  disabled repeated reservation attempts after reservation creation.

## Completion Summary

Fixed the purchase panel state reset. The panel no longer remounts every second,
so `Complete local payment` remains visible after reservation creation. The
primary buy button now becomes `Reserved` or `Owned` and is disabled after the
reservation progresses.

## Files Changed

- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `docs/implementation-plan/2026-06-19/fix-frontend-purchase-panel-state.md`

## Tests Run And Results

- `apps/web`: `npm.cmd run lint` passed.
- `apps/web`: `npm.cmd run build` passed.
- `git diff --check` passed.

## Manual Verification Results

- Browser click-through verification was not run in this environment.
- The root cause was verified in code: a per-second React key forced component
  remounts and reset `reserved` state.

## Known Limitations

- This fix keeps an in-progress reservation visible even as the live clock
  advances. That is intentional for the current development-stage flow because
  the reservation itself is the authoritative selected range.

## Follow-Up Recommendations

- Add a web-origin purchase verification script or browser test if this UI flow
  becomes a required PR gate.
