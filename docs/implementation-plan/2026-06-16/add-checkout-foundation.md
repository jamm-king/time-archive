# Add Checkout Foundation

## Objective

Add a checkout foundation that can create a payment checkout session for a held purchase reservation through a provider-agnostic payment port.

This step bridges the current reservation and payment-finalization foundations without coupling the application to a real payment provider yet.

## Scope

- Add a `PaymentPort` for checkout creation.
- Add payment checkout request and response models.
- Add `CreateCheckout` application use case.
- Add a fake payment adapter for local and test use.
- Add repository support for marking a reservation as `CHECKOUT_CREATED`.
- Add purchase creation or checkout tracking decision based on the final implementation shape.
- Add unit tests for checkout behavior.
- Add integration tests for reservation status transition and transaction behavior.
- Update architecture documents if final decisions differ from existing docs.

## Out of Scope

- Real Stripe integration
- Webhook signature verification
- Public REST endpoint
- Authentication and authorization
- Frontend checkout UI
- Payment provider secrets management
- Ownership finalization changes

## Relevant Files or Modules

Expected new or changed files:

- `src/main/kotlin/com/timearchive/domain/port/PaymentPort.kt`
- `src/main/kotlin/com/timearchive/domain/model/CheckoutSession.kt`
- `src/main/kotlin/com/timearchive/application/CreateCheckout.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/payment/FakePaymentAdapter.kt`
- `src/main/kotlin/com/timearchive/domain/port/PurchaseReservationRepository.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcPurchaseReservationRepository.kt`
- `src/test/kotlin/com/timearchive/application/CreateCheckoutTest.kt`
- `src/test/kotlin/com/timearchive/adapter/outbound/persistence/JdbcPurchaseReservationRepositoryIntegrationTest.kt`
- `docs/implementation-plan/2026-06-16/add-checkout-foundation.md`

Potentially changed files:

- `docs/architecture/domain-model.md`
- `docs/architecture/transaction-boundaries.md`

## Key Design Decisions

- Keep payment provider integration behind `PaymentPort`.
- Do not introduce Stripe-specific types into domain or application code.
- Treat checkout creation as a transactionally controlled reservation status transition.
- Move reservation status from `HELD` to `CHECKOUT_CREATED` only after checkout creation succeeds.
- Do not create ownership during checkout creation.
- Do not trust checkout redirect for payment confirmation.
- Payment finalization remains owned by `CompletePrimaryPurchase`.

## Proposed Use Case Behavior

`CreateCheckout` should:

1. Load reservation with a row-level lock.
2. Validate reservation exists.
3. Validate reservation status is `HELD`.
4. Validate reservation is not expired.
5. Call `PaymentPort.createCheckout`.
6. Mark reservation as `CHECKOUT_CREATED`.
7. Return checkout URL and provider reference.

## Transaction Boundary Note

External network calls should not be held inside long database transactions. For the fake adapter this is not an issue, but the real provider implementation should avoid long lock duration.

When a real provider is added, the flow may need one of these approaches:

- create a pending checkout attempt before provider call, then mark reservation after success
- keep a short transaction around status update only
- add idempotent checkout creation using a provider idempotency key

This implementation should document any compromise clearly.

## Testing Plan

### Unit Tests

- Creates checkout for held reservation.
- Rejects missing reservation.
- Rejects expired reservation.
- Rejects reservation that is already `CHECKOUT_CREATED`.
- Does not mark reservation if payment port fails.

### Integration Tests

- Marks reservation as `CHECKOUT_CREATED`.
- Prevents checkout creation for expired reservation.
- Verifies status transition from `HELD` to `CHECKOUT_CREATED`.

## Verification Plan

- Run `.\gradlew.bat test`.
- Run `.\gradlew.bat build`.
- Run `docker build -t time-archive-api:local .` if packaging is affected.
- Confirm Git status contains only intended changes.

## MVP Completion Impact

This step moves the backend closer to the primary purchase flow, but still does not provide a complete user-facing purchase because there is no REST API, authentication, frontend, or real payment provider integration yet.

## Risks and Rollback Strategy

- Risk: Checkout creation with real providers can be tricky because it combines external calls and reservation locking.
  - Mitigation: Keep this step provider-neutral and document the future real-provider transaction strategy.
- Risk: A fake adapter may hide real provider edge cases.
  - Mitigation: Limit fake adapter claims to local/test use only.
- Risk: Status transition rules may need adjustment once checkout expiration is modeled.
  - Mitigation: Keep transition logic focused and covered by tests.
- Rollback: Revert the implementation commit before production. No production data exists yet.

## Open Questions

- Should checkout attempts be persisted separately from purchase reservations?
- Should checkout creation create a `Purchase` in `PENDING_PAYMENT`, or should purchases continue to be created only when payment is confirmed?
- What should the initial checkout expiration duration be relative to reservation expiration?

## Proposed Execution Order

1. Create implementation branch from latest `main`.
2. Add `PaymentPort` and checkout models.
3. Add fake payment adapter.
4. Extend reservation repository for `CHECKOUT_CREATED` transition.
5. Add `CreateCheckout` use case.
6. Add unit tests.
7. Add integration tests.
8. Update architecture documents if needed.
9. Run verification commands.
10. Record completion details in this plan.
11. Commit and push the implementation branch.

## Progress

- [x] Implementation plan created.
- [x] Implementation branch created.
- [x] Payment port added.
- [x] Fake payment adapter added.
- [x] Reservation repository transition added.
- [x] Use case added.
- [x] Unit tests added.
- [x] Integration tests added.
- [x] Documentation updates completed if needed.
- [x] `.\gradlew.bat test` passed.
- [x] `.\gradlew.bat build` passed.
- [x] Docker image build completed and image existence verified.
- [x] Completion details recorded.

## Implementation Notes

- Added `PaymentPort` as the provider-neutral outbound boundary for checkout creation.
- Added provider-neutral checkout request and session models in the domain layer.
- Added a fake payment adapter for local use only. It does not perform network I/O and must not be treated as a production payment integration.
- Added `CreateCheckout` as an application use case that validates a held, unexpired reservation before creating checkout.
- Added repository support for transitioning a reservation from `HELD` to `CHECKOUT_CREATED`.
- Kept ownership and purchase finalization outside checkout creation. Verified payment completion remains the responsibility of `CompletePrimaryPurchase`.
- Documented the transaction compromise: the fake adapter can be called inside the current transaction, but real provider integration should introduce persisted checkout attempts, provider idempotency keys, or a shorter transaction split.

## Completion Summary

Checkout foundation was implemented for the primary purchase flow. The application can now create a provider-neutral checkout session for a held, unexpired reservation and mark the reservation as `CHECKOUT_CREATED`.

The implementation keeps payment provider details behind `PaymentPort`, uses a fake outbound adapter for local development, and preserves payment finalization as a separate verified-payment workflow.

## Files Changed

- `src/main/kotlin/com/timearchive/domain/model/CheckoutRequest.kt`
- `src/main/kotlin/com/timearchive/domain/model/CheckoutSession.kt`
- `src/main/kotlin/com/timearchive/domain/port/PaymentPort.kt`
- `src/main/kotlin/com/timearchive/application/CreateCheckout.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/payment/FakePaymentAdapter.kt`
- `src/main/kotlin/com/timearchive/domain/port/PurchaseReservationRepository.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcPurchaseReservationRepository.kt`
- `src/test/kotlin/com/timearchive/application/CreateCheckoutTest.kt`
- `src/test/kotlin/com/timearchive/application/ReserveTimeRangeTest.kt`
- `src/test/kotlin/com/timearchive/application/CompletePrimaryPurchaseTest.kt`
- `src/test/kotlin/com/timearchive/adapter/outbound/persistence/JdbcPurchaseReservationRepositoryIntegrationTest.kt`
- `docs/architecture/domain-model.md`
- `docs/architecture/transaction-boundaries.md`
- `docs/architecture/time-archive-architecture.md`
- `docs/implementation-plan/2026-06-16/add-checkout-foundation.md`

## Tests Run and Results

- `.\gradlew.bat test`: passed.
- `.\gradlew.bat build`: passed.
- `docker build -t time-archive-api:local .`: completed image export, but the shell command exceeded the 120-second timeout and returned exit code 124.
- `docker image inspect time-archive-api:local`: passed and confirmed the image exists.

## Manual Verification Results

- Verified the checkout use case rejects missing, expired, and non-held reservations.
- Verified the checkout use case does not mark checkout created when payment checkout creation fails.
- Verified JDBC reservation status transition from `HELD` to `CHECKOUT_CREATED`.
- Verified repeated transition for a non-held reservation does not update the row.

## Known Limitations

- No public REST endpoint exists for checkout creation yet.
- No authentication or authorization is applied to checkout creation yet.
- The fake payment adapter is local-only and does not collect real payments.
- Real payment provider integration still needs persisted checkout attempts, provider idempotency keys, and careful transaction splitting to avoid holding database locks during network calls.

## Follow-Up Recommendations

- Add a REST API endpoint for checkout creation with authenticated buyer validation.
- Add persisted checkout attempts before integrating a real payment provider.
- Add provider idempotency key handling before real checkout creation.
- Add payment provider webhook verification before production payment collection.
