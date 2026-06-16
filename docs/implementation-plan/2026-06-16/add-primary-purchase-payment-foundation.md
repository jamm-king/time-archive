# Add Primary Purchase Payment Foundation

## Objective

Add the internal purchase, payment event, idempotency, audit log, and outbox foundation needed to safely finalize a paid reservation into active ownership.

This step should prepare Time Archive for payment provider integration without coupling the domain to a specific provider such as Stripe.

## Scope

- Add `Purchase` domain model.
- Add `PurchaseStatus`.
- Add `PaymentEvent` domain model.
- Add `PaymentEventStatus`.
- Add payment event idempotency persistence using `(provider, provider_event_id)`.
- Add `AuditLog` persistence foundation for money and ownership actions.
- Add `OutboxEvent` persistence foundation for future asynchronous processing.
- Add repository ports for purchase, payment event, audit log, and outbox records.
- Add Flyway V3 migration for related tables and unique constraints.
- Add application use case for completing a primary purchase from a verified payment confirmation.
- Convert a `PurchaseReservation` to:
  - completed reservation
  - paid purchase
  - active ownership record
  - payment event record
  - audit log entries
  - outbox events
- Add unit and Testcontainers integration tests for idempotency and ownership finalization.

## Out of Scope

- Real payment provider integration
- Checkout session creation with an external provider
- Webhook signature verification
- Public REST API endpoint
- Authentication and authorization
- Seller payout
- Resale transaction flow
- Media upload and moderation

## Relevant Files or Modules

Expected new or changed files:

- `src/main/kotlin/com/timearchive/domain/model/Purchase.kt`
- `src/main/kotlin/com/timearchive/domain/model/PurchaseStatus.kt`
- `src/main/kotlin/com/timearchive/domain/model/PaymentEvent.kt`
- `src/main/kotlin/com/timearchive/domain/model/PaymentEventStatus.kt`
- `src/main/kotlin/com/timearchive/domain/model/AuditLog.kt`
- `src/main/kotlin/com/timearchive/domain/model/OutboxEvent.kt`
- `src/main/kotlin/com/timearchive/domain/port/PurchaseRepository.kt`
- `src/main/kotlin/com/timearchive/domain/port/PaymentEventRepository.kt`
- `src/main/kotlin/com/timearchive/domain/port/AuditLogPort.kt`
- `src/main/kotlin/com/timearchive/domain/port/OutboxPort.kt`
- `src/main/kotlin/com/timearchive/application/CompletePrimaryPurchase.kt`
- `src/main/resources/db/migration/V3__create_purchase_payment_audit_outbox_tables.sql`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/...`
- `src/test/kotlin/com/timearchive/application/CompletePrimaryPurchaseTest.kt`
- `src/test/kotlin/com/timearchive/adapter/outbound/persistence/...`
- `docs/implementation-plan/2026-06-16/add-primary-purchase-payment-foundation.md`

Potentially changed files:

- `docs/architecture/domain-model.md`
- `docs/architecture/transaction-boundaries.md`

## Key Design Decisions

- Keep payment-provider-specific behavior behind future ports.
- Treat browser redirects as non-authoritative.
- Final ownership is created only from a verified payment confirmation command.
- Use `provider_event_id` uniqueness to make payment event handling idempotent.
- Use purchase status transitions to prevent duplicate ownership creation.
- Re-check active ownership overlap inside finalization before creating ownership.
- Complete reservation and create ownership in one database transaction.
- Write audit logs and outbox events in the same transaction as ownership creation.
- Store money as integer cents.
- Keep `buyer_id` as UUID without FK until user persistence exists.

## Proposed Database Design

### `purchases`

Initial columns:

- `id uuid primary key`
- `buyer_id uuid not null`
- `reservation_id uuid not null references purchase_reservations(id)`
- `start_second bigint not null`
- `end_second bigint not null`
- `amount_cents bigint not null`
- `currency varchar(3) not null`
- `status varchar(32) not null`
- `payment_provider varchar(64) null`
- `payment_reference varchar(255) null`
- `created_at timestamptz not null`
- `updated_at timestamptz not null`

Suggested statuses:

- `PENDING_PAYMENT`
- `PAID`
- `OWNERSHIP_GRANTED`
- `FAILED`
- `EXPIRED`
- `REFUNDED`

### `payment_events`

Initial columns:

- `id uuid primary key`
- `provider varchar(64) not null`
- `provider_event_id varchar(255) not null`
- `event_type varchar(128) not null`
- `payload_hash varchar(128) not null`
- `processing_status varchar(32) not null`
- `received_at timestamptz not null`
- `processed_at timestamptz null`

Constraints:

- `(provider, provider_event_id)` unique

### `audit_logs`

Initial columns:

- `id uuid primary key`
- `actor_user_id uuid null`
- `actor_type varchar(32) not null`
- `action varchar(128) not null`
- `resource_type varchar(128) not null`
- `resource_id uuid not null`
- `before_state jsonb null`
- `after_state jsonb null`
- `request_id varchar(128) null`
- `created_at timestamptz not null`

### `outbox_events`

Initial columns:

- `id uuid primary key`
- `event_type varchar(128) not null`
- `aggregate_type varchar(128) not null`
- `aggregate_id uuid not null`
- `payload jsonb not null`
- `status varchar(32) not null`
- `created_at timestamptz not null`
- `processed_at timestamptz null`
- `retry_count integer not null default 0`
- `last_error text null`

## Use Case Behavior

`CompletePrimaryPurchase` should:

1. Receive a verified payment confirmation command.
2. Insert or find the payment event by `(provider, providerEventId)`.
3. If the payment event was already processed, return the already-finalized result without side effects.
4. Load the reservation with a row-level lock.
5. Reject expired, cancelled, or already completed reservations unless the operation is idempotent.
6. Re-check active ownership overlap.
7. Create or update purchase state.
8. Mark purchase as `OWNERSHIP_GRANTED`.
9. Mark reservation as `COMPLETED`.
10. Create active ownership record.
11. Mark payment event as processed.
12. Append audit logs.
13. Append outbox events such as `PurchaseCompleted`, `OwnershipCreated`, and `TimelineManifestInvalidated`.

## Testing Plan

### Unit Tests

- Purchase status transitions reject invalid transitions.
- Duplicate payment event command is idempotent.
- Expired reservation cannot be finalized.
- Finalization rejects ownership overlap.
- Successful finalization creates ownership and outbox event records.

### Integration Tests

Use Testcontainers PostgreSQL for:

- Flyway V3 migration success.
- Payment event provider event ID uniqueness.
- Purchase reservation row locking where practical.
- Successful finalization writes purchase, ownership, audit, and outbox records.
- Repeated payment event does not create duplicate ownership.
- Finalization fails if ownership was created for the same range after reservation.

## Verification Plan

- Run `.\gradlew.bat test`.
- Run `.\gradlew.bat build`.
- Run `docker build -t time-archive-api:local .` if Docker packaging is affected.
- Confirm Git status contains only intended changes.

## Risks and Rollback Strategy

- Risk: This step touches several high-integrity tables at once.
  - Mitigation: Keep external provider integration out of scope and focus on internal transaction correctness.
- Risk: Row-level lock support may require explicit SQL beyond current repository methods.
  - Mitigation: Use explicit JDBC SQL in outbound adapters where transaction semantics must be precise.
- Risk: Audit/outbox JSON payloads can become inconsistent if shaped ad hoc.
  - Mitigation: Keep payloads minimal and stable for the first implementation.
- Risk: Payment provider details may later require schema adjustments.
  - Mitigation: Use generic provider and provider reference fields now.
- Rollback: Revert the implementation commit before production. No production data exists yet.

## Open Questions

- Decision: `Purchase` is created when payment is confirmed in this step. Checkout creation remains future work.
- Decision: `PaymentEvent` stores payload hash only for now.
- Decision: Audit and outbox payloads are represented as JSON strings in domain/application code and stored as PostgreSQL JSONB by adapters.
- Decision: Outbox events are generated by the use case for now.

## Proposed Execution Order

1. Create an implementation branch from latest `main`.
2. Add Flyway V3 migration.
3. Add domain models and ports.
4. Add persistence adapters with explicit SQL and row locking where needed.
5. Add `CompletePrimaryPurchase` use case.
6. Add unit tests.
7. Add Testcontainers integration tests.
8. Update architecture documents with final decisions.
9. Run verification commands.
10. Record completion details in this plan.
11. Commit and push the implementation branch.

## Progress

- [x] Implementation plan created.
- [x] Implementation branch created.
- [x] Flyway migration added.
- [x] Domain models added.
- [x] Repository ports added.
- [x] Persistence adapters added.
- [x] Use case added.
- [x] Unit tests added.
- [x] Integration tests added.
- [x] Documentation updates completed if needed.
- [x] Verification commands run.
- [x] Completion details recorded.

## Completion Summary

Added the internal primary purchase payment foundation without integrating a real payment provider.

The implementation adds purchase, payment event, audit log, outbox event, and transaction ports; JDBC adapters; Flyway V3 tables; and the `CompletePrimaryPurchase` use case. A verified payment confirmation can now finalize a held reservation into an `OWNERSHIP_GRANTED` purchase, a completed reservation, an active ownership record, audit logs, and outbox events within a Spring-managed transaction.

Payment webhook idempotency is represented by the unique `(provider, provider_event_id)` payment event key and by the processed payment event path in `CompletePrimaryPurchase`.

## Files Changed

- `docs/architecture/domain-model.md`
- `docs/architecture/transaction-boundaries.md`
- `docs/implementation-plan/2026-06-16/add-primary-purchase-payment-foundation.md`
- `src/main/kotlin/com/timearchive/application/CompletePrimaryPurchase.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcAuditLogAdapter.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcOutboxAdapter.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcPaymentEventRepository.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcPurchaseRepository.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcPurchaseReservationRepository.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/SpringTransactionAdapter.kt`
- `src/main/kotlin/com/timearchive/domain/model/AuditLog.kt`
- `src/main/kotlin/com/timearchive/domain/model/OutboxEvent.kt`
- `src/main/kotlin/com/timearchive/domain/model/PaymentEvent.kt`
- `src/main/kotlin/com/timearchive/domain/model/PaymentEventStatus.kt`
- `src/main/kotlin/com/timearchive/domain/model/Purchase.kt`
- `src/main/kotlin/com/timearchive/domain/model/PurchaseStatus.kt`
- `src/main/kotlin/com/timearchive/domain/port/AuditLogPort.kt`
- `src/main/kotlin/com/timearchive/domain/port/OutboxPort.kt`
- `src/main/kotlin/com/timearchive/domain/port/PaymentEventRepository.kt`
- `src/main/kotlin/com/timearchive/domain/port/PurchaseRepository.kt`
- `src/main/kotlin/com/timearchive/domain/port/PurchaseReservationRepository.kt`
- `src/main/kotlin/com/timearchive/domain/port/TransactionPort.kt`
- `src/main/resources/db/migration/V3__create_purchase_payment_audit_outbox_tables.sql`
- `src/test/kotlin/com/timearchive/application/CompletePrimaryPurchaseIntegrationTest.kt`
- `src/test/kotlin/com/timearchive/application/CompletePrimaryPurchaseTest.kt`
- `src/test/kotlin/com/timearchive/application/ReserveTimeRangeTest.kt`

## Tests Run and Results

- `.\gradlew.bat test` - passed.
- `.\gradlew.bat build` - passed.
- `docker build -t time-archive-api:local .` - passed.

## Manual Verification Results

- Confirmed Flyway V3 migration applies through Testcontainers PostgreSQL.
- Confirmed successful finalization writes purchase, ownership, payment event, audit log, and outbox records.
- Confirmed duplicate payment event processing does not create duplicate purchase or ownership records.
- Confirmed expired reservation finalization rolls back all payment-side writes.
- Confirmed finalization rolls back if active ownership appears before payment finalization.

## Known Limitations

- No real payment provider adapter exists yet.
- No checkout creation exists yet.
- No webhook signature verification exists yet.
- No REST endpoint exposes this use case yet.
- Audit and outbox payloads are minimal JSON strings and may need typed event payloads later.

## Follow-Up Recommendations

- Add a payment provider port and fake checkout adapter before adding real Stripe integration.
- Add inbound REST APIs only after authentication and request validation are introduced.
- Add a background outbox dispatcher after media/timeline manifest invalidation becomes executable.
