# Add PayPal Return Confirmation UX

## Objective

Add a user-facing PayPal return confirmation flow so a buyer can see whether a
captured PayPal order has been finalized into an active ownership record.

## Scope

- Add a backend read use case for PayPal checkout status by provider order id.
- Expose a session-protected API endpoint for the current buyer.
- Add a Web proxy/helper and polling UI on the PayPal return page.
- Update API and release-readiness documentation where behavior changes.
- Add focused tests for backend status mapping and Web response parsing.

Out of scope:

- Granting ownership from browser redirects.
- Changing webhook finalization rules.
- Production PayPal live configuration.
- Alerting, restore drills, or broader payment retry testing.

## Relevant Files or Modules

- `apps/api/src/main/kotlin/com/timearchive/application`
- `apps/api/src/main/kotlin/com/timearchive/domain/port`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/persistence`
- `apps/api/src/test/kotlin/com/timearchive/application`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest`
- `apps/web/src/components/PayPalReturnPanel.tsx`
- `apps/web/src/lib/purchase.ts`
- `apps/web/src/app/api/payments/paypal`
- `docs/api/openapi.yaml`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- The browser return remains informational only. Ownership is still granted only
  after a verified PayPal webhook.
- The status endpoint is scoped to the authenticated buyer and PayPal provider
  order id, preventing users from polling other users' checkout attempts.
- The returned status is coarse-grained and safe for UI use:
  `CAPTURE_NOT_STARTED`, `CAPTURE_PENDING_WEBHOOK`, `OWNERSHIP_GRANTED`,
  `CAPTURE_FAILED`, `EXPIRED`, or `FAILED`.
- The Web return page polls briefly after capture and shows a delayed
  confirmation state instead of leaving users on a static waiting message.

## Step-by-Step Execution Plan

- [x] Inspect existing PayPal capture, checkout attempt, purchase, and ownership
  persistence.
- [x] Create this implementation plan.
- [x] Add repository read support for PayPal checkout attempt lookup without
  locking.
- [x] Add an application use case that maps checkout, reservation, purchase, and
  ownership state into a safe confirmation status.
- [x] Add REST DTOs/controller endpoint and focused tests.
- [x] Add Web proxy/client parsing and PayPal return polling UI.
- [x] Update OpenAPI and release-readiness docs.
- [x] Run relevant backend, Web, OpenAPI, and diff checks.

## Risks and Rollback Strategy

- Risk: The UI may imply payment success before ownership is granted.
  Mitigation: keep capture and ownership wording separate and show a delayed
  confirmation state when webhook finalization has not completed.
- Risk: A buyer could infer another user's payment status.
  Mitigation: require a session and verify the checkout attempt buyer id.
- Risk: Polling increases backend requests.
  Mitigation: use a short polling window with fixed intervals.
- Rollback: revert this branch. Existing PayPal capture behavior remains
  available, but the return page goes back to the static pending message.

## Verification Plan

- Run focused API tests for the new use case and controller.
- Run Web type/lint checks available in the repository.
- Run `./scripts/verify-openapi.sh` if Docker is available.
- Run `git diff --check`.

## Open Questions

- None for this scoped implementation. Production live PayPal verification
  remains a separate release-readiness blocker.

## Progress

- 2026-07-05: Confirmed current PayPal return page captures the order and then
  shows a static provider-confirmation waiting state.
- 2026-07-05: Added authenticated PayPal order confirmation status API, Web
  proxy route, and return-page polling UI.

## Completion Summary

The PayPal return page now captures the approved order, polls a server-side
confirmation status endpoint, and shows explicit success, delayed, and failure
states. The server-side status endpoint remains read-only and buyer-scoped; it
does not grant ownership and only reports final success after the verified
webhook-created ownership record exists.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/application/GetPayPalOrderConfirmationStatus.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/PayPalPaymentController.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/PayPalPaymentDtos.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcCheckoutAttemptRepository.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcOwnershipRepository.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcPurchaseReservationRepository.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/port/CheckoutAttemptRepository.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/port/OwnershipRepository.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/port/PurchaseReservationRepository.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/GetPayPalOrderConfirmationStatusTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest/PayPalPaymentControllerTest.kt`
- `apps/web/src/app/api/payments/paypal/orders/[orderId]/confirmation-status/route.ts`
- `apps/web/src/components/PayPalReturnPanel.tsx`
- `apps/web/src/lib/purchase.ts`
- `docs/api/openapi.yaml`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-05/add-paypal-return-confirmation-ux.md`

## Tests Run and Results

- `./gradlew.bat test --tests "com.timearchive.application.GetPayPalOrderConfirmationStatusTest" --tests "com.timearchive.adapter.inbound.rest.PayPalPaymentControllerTest"`:
  passed.
- `./gradlew.bat test --max-workers=2`: passed.
- `npm.cmd run lint`: passed.
- `npm.cmd run build`: passed.
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-openapi.sh`: passed.
- `git diff --check`: passed.

## Manual Verification Results

- Not deployed yet. After merging and deploying, run a fresh PayPal Sandbox
  purchase and confirm the return page transitions from capture confirmation to
  ownership success after the verified webhook is processed.

## Known Limitations

- The return page uses short polling and may show a delayed state if the webhook
  arrives after the polling window.
- Production PayPal live setup and live payment drill remain separate blockers.

## Follow-up Recommendations

- Add a staging smoke workflow for the PayPal return confirmation endpoint after
  the deployed UX is manually verified.
- Continue with PayPal retry/idempotency scenario verification.
