# Add Frontend Purchase Flow

## Objective

Connect the frontend MVP loop so an authenticated user can reserve and complete
a development-stage purchase from the web UI, then see the purchased second in
their owned ranges.

## Scope

- Add Next.js same-origin proxy routes for:
  - archive availability checks
  - purchase reservation creation
  - checkout creation
  - development-stage fake payment completion
- Add frontend purchase helpers with response validation.
- Add a compact purchase panel for the current second in the public timeline
  player.
- Require authentication before purchase actions.
- Refresh current user's owned ranges after the development-stage purchase is
  completed.
- Keep this explicitly development-stage until a real payment provider is
  integrated.

Out of scope:

- Real payment provider integration.
- Redirect-based checkout callback handling.
- Purchase history UI.
- Payment failure/refund handling.
- Selecting arbitrary seconds outside the current timeline second.

## Relevant Files Or Modules

- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `apps/web/src/lib/purchase.ts`
- `apps/web/src/lib/auth.ts`
- `apps/web/src/lib/owned-ranges.ts`
- `apps/web/src/app/api/archive/availability/route.ts`
- `apps/web/src/app/api/purchase`
- `apps/web/src/app/api/internal/payments/fake/webhooks`
- Existing backend purchase APIs under `/api/purchase`
- Existing backend fake payment API under
  `/api/internal/payments/fake/webhooks/primary-purchase-completed`

## Key Design Decisions

- The UI buys the current archive second as a one-second range
  `[currentSecond, currentSecond + 1)`. This matches the product rule that
  the day is sold one second at a time.
- The frontend does not expose user-controlled `buyerId`; identity remains
  server-side session based.
- The development-stage fake payment completion is intentionally labeled in the
  UI as a local completion action, not a real payment.
- Owned ranges are refreshed after completion so the upload UI becomes reachable
  without a page reload.
- No backend domain changes are required for this first frontend connection.

## Step-By-Step Execution Plan

- [x] Inspect purchase API contracts and current frontend state ownership.
- [x] Add this implementation plan.
- [x] Add Next.js proxy routes for availability, reservation, checkout, and fake
  payment completion.
- [x] Add frontend purchase helper functions.
- [x] Update owned range list UI to accept a refresh signal.
- [x] Add current-second purchase UI states and actions.
- [x] Run frontend lint/build.
- [x] Manually verify the local web purchase flow against Docker Compose.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: Users may confuse fake payment completion with real payment.
  - Mitigation: Keep the UI copy explicit that it is local development payment
    completion.
- Risk: The current second may change while a user is interacting with the
  purchase button.
  - Mitigation: Capture the selected second at reservation time and show the
    reserved range in the pending state.
- Risk: The purchased second may already be reserved or owned.
  - Mitigation: Use backend availability and reservation errors as the source of
    truth and show a retryable error state.

Rollback:

- Remove the new web proxy routes, frontend purchase helper, player UI changes,
  and this implementation plan. No database migration or backend rollback is
  required.

## Verification Plan

- `npm.cmd run lint` in `apps/web`.
- `npm.cmd run build` in `apps/web`.
- `git diff --check`.
- Run Docker Compose and verify a web-origin flow:
  - register or login
  - reserve the current second
  - create checkout
  - complete fake payment
  - confirm `/api/me/owned-ranges` contains the purchased range

## Open Questions

- None for this development-stage implementation.

## Progress Log

- 2026-06-18: Confirmed `main` is up to date and includes the frontend owned
  media upload flow.
- 2026-06-18: Created `feature/frontend-purchase-flow` from `main`.
- 2026-06-18: Added web proxy routes for availability, reservations, checkout,
  and development-stage fake payment completion.
- 2026-06-18: Added frontend purchase helpers and connected current-second
  purchase UI to the public timeline player.
- 2026-06-18: Verified the web-origin purchase flow against Docker Compose.

## Completion Summary

Implemented the development-stage frontend purchase flow for the current archive
second. Authenticated users can reserve the current unclaimed second, create a
fake checkout session, complete local fake payment, and then see the purchased
range appear in their owned ranges without reloading the page.

## Files Changed

- `apps/web/src/app/api/archive/availability/route.ts`
- `apps/web/src/app/api/purchase/reservations/route.ts`
- `apps/web/src/app/api/purchase/reservations/[reservationId]/checkout/route.ts`
- `apps/web/src/app/api/internal/payments/fake/webhooks/primary-purchase-completed/route.ts`
- `apps/web/src/lib/purchase.ts`
- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `docs/implementation-plan/2026-06-18/add-frontend-purchase-flow.md`

## Tests Run And Results

- `apps/web`: `npm.cmd run lint` passed.
- `apps/web`: `npm.cmd run build` passed.
- `git diff --check` passed.

## Manual Verification Results

- `docker compose up -d --build` passed.
- Verified a web-origin flow through `http://localhost:3000`:
  - fetched CSRF
  - registered a user
  - checked availability
  - created a reservation
  - created checkout
  - completed development-stage fake payment
  - confirmed `/api/me/owned-ranges` contained the purchased ownership record
- `docker compose down` completed after verification.

## Known Limitations

- This is not real payment integration. The UI exposes a clearly labeled local
  fake payment completion action for development-stage MVP testing.
- The UI purchases only the current archive second. Arbitrary second selection
  remains out of scope.
- The in-app browser plugin still could not be used for visual click-through
  verification in this environment, so verification was HTTP-based through the
  web origin.

## Follow-Up Recommendations

- Add a web-origin purchase verification script and CI job if this flow becomes
  a required PR gate.
- Replace fake payment completion with real provider redirect/webhook handling
  before production use.
