# Add PayPal Idempotency And Error Verification

## Objective

Strengthen PayPal payment readiness by verifying retry, duplicate webhook, and
provider/local state mismatch scenarios around the real PayPal integration
boundary.

## Scope

- Add focused backend tests for PayPal capture retry behavior.
- Add focused backend tests for PayPal webhook duplicate processing and mismatch
  rejection behavior.
- Update release-readiness documentation to separate automated backend
  verification from remaining staging/provider-dashboard drills.

Out of scope:

- Calling the live PayPal API from automated tests.
- Changing the PayPal webhook signature verification contract.
- Adding production PayPal live credentials or production payment drills.
- Adding CloudWatch alert rules.

## Relevant Files or Modules

- `apps/api/src/main/kotlin/com/timearchive/application/CapturePayPalOrder.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/CompletePayPalWebhook.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/CapturePayPalOrderTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/CompletePayPalWebhookTest.kt`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- Use unit-level application tests with fakes/mocks for deterministic coverage
  of local state transitions and mismatch handling.
- Keep provider signature verification mocked in these tests; the HTTP client
  signature verification path already has its own adapter tests.
- Treat duplicate PayPal webhook idempotency as the result of
  `CompletePrimaryPurchase` returning `alreadyProcessed = true`, with
  `CompletePayPalWebhook` preserving that response.
- Treat amount, currency, order id, capture id, and capture status mismatches as
  rejected local-state conflicts.

## Step-by-Step Execution Plan

- [x] Inspect existing PayPal capture and webhook tests.
- [x] Create this implementation plan.
- [x] Add capture retry-after-failure verification.
- [x] Add duplicate webhook already-processed verification.
- [x] Add amount, currency, order id, and non-completed capture rejection tests.
- [x] Update release-readiness documentation.
- [x] Run focused PayPal tests, API tests, and diff checks.

## Risks and Rollback Strategy

- Risk: Tests overfit implementation details instead of business behavior.
  Mitigation: assert externally meaningful outcomes, command fields, and
  rejection messages rather than private helper behavior.
- Risk: Documentation overstates readiness.
  Mitigation: explicitly keep staging/provider-dashboard resend and production
  live payment drills as separate follow-ups.
- Rollback: revert this branch. Existing PayPal behavior remains unchanged.

## Verification Plan

- Run focused tests:
  - `CapturePayPalOrderTest`
  - `CompletePayPalWebhookTest`
- Run the full API test suite.
- Run `git diff --check`.

## Open Questions

- None. Staging PayPal resend drills may be added later as manual or GitHub
  Actions workflow work.

## Progress

- 2026-07-05: Confirmed existing tests cover happy path, unverified signature,
  unsupported event, capture reference mismatch, already-captured retry, and
  capture failure recording.
- 2026-07-05: Added tests for capture retry after failure, duplicate webhook
  idempotency response propagation, order mismatch, amount mismatch, currency
  mismatch, non-completed capture rejection, and webhook arrival before local
  capture recording.
- 2026-07-05: Focused PayPal tests passed.
- 2026-07-05: Full API test suite and diff check passed.

## Completion Summary

PayPal payment error and idempotency verification was strengthened with focused
application tests. The tests now cover capture retry after a recorded capture
failure, already captured retry behavior, duplicate webhook idempotency response
propagation, local capture missing before webhook finalization, non-completed
capture rejection, and capture reference, order reference, amount, and currency
mismatches.

No production behavior was changed. This task only adds verification coverage
and updates release readiness documentation.

## Files Changed

- `apps/api/src/test/kotlin/com/timearchive/application/CapturePayPalOrderTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/CompletePayPalWebhookTest.kt`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-05/add-paypal-idempotency-error-verification.md`

## Tests Run and Results

- `./gradlew.bat test --tests "com.timearchive.application.CapturePayPalOrderTest" --tests "com.timearchive.application.CompletePayPalWebhookTest"`:
  passed.
- `./gradlew.bat test --max-workers=2`: passed.
- `git diff --check`: passed.

## Manual Verification Results

- Not applicable for this local verification-only branch.
- Staging PayPal Sandbox webhook resend and capture retry drills remain
  follow-up operational checks before marking payment idempotency fully Ready.

## Known Limitations

- These tests do not call PayPal Sandbox or PayPal Live directly.
- Provider Dashboard resend behavior still needs a staging drill.

## Follow-up Recommendations

- Add a staging PayPal webhook resend runbook or smoke workflow if the manual
  drill proves stable.
- Continue with production PayPal live setup planning and first low-value
  payment/refund drill preparation.
