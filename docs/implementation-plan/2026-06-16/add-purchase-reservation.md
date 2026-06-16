# Add Purchase Reservation

## Objective

Add purchase reservation persistence and the first reservation use case so Time Archive can temporarily hold available seconds before payment checkout.

This step builds on canonical ownership persistence. It must preserve the product constraint that Time Archive has one 24-hour archive of 86,400 seconds and no seasons or repeatable timelines.

## Scope

- Add `PurchaseReservation` domain model.
- Add `PurchaseReservationStatus`.
- Add `PurchaseReservationRepository` port.
- Add a `ReserveTimeRange` application use case.
- Add Flyway V2 migration for `purchase_reservations`.
- Add PostgreSQL constraints for reservation range validity and active reservation overlap prevention.
- Add JDBC outbound persistence adapter.
- Add unit tests for reservation domain behavior and use case behavior.
- Add Testcontainers integration tests for reservation persistence and constraints.

## Out of Scope

- Payment provider integration
- Checkout session creation
- Payment webhook handling
- Purchase finalization
- Ownership creation from reservation
- Reservation expiration background worker
- REST API endpoints
- Authentication and authorization

## Relevant Files or Modules

Expected new or changed files:

- `src/main/kotlin/com/timearchive/domain/model/PurchaseReservation.kt`
- `src/main/kotlin/com/timearchive/domain/model/PurchaseReservationStatus.kt`
- `src/main/kotlin/com/timearchive/domain/port/PurchaseReservationRepository.kt`
- `src/main/kotlin/com/timearchive/application/ReserveTimeRange.kt`
- `src/main/resources/db/migration/V2__create_purchase_reservations.sql`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcPurchaseReservationRepository.kt`
- `src/test/kotlin/com/timearchive/domain/model/PurchaseReservationTest.kt`
- `src/test/kotlin/com/timearchive/application/ReserveTimeRangeTest.kt`
- `src/test/kotlin/com/timearchive/adapter/outbound/persistence/JdbcPurchaseReservationRepositoryIntegrationTest.kt`
- `docs/implementation-plan/2026-06-16/add-purchase-reservation.md`

Potentially changed files:

- `docs/architecture/domain-model.md`
- `docs/architecture/transaction-boundaries.md`

## Key Design Decisions

- Reservation ranges use the same inclusive start and exclusive end semantics as ownership.
- Reservation ranges must fit within the canonical 86,400-second archive.
- Reservation amount is stored as `amount_cents` in USD for exact integer arithmetic.
- Active reservation statuses are `HELD` and `CHECKOUT_CREATED`.
- Active reservation overlap is prevented in PostgreSQL by an exclusion constraint.
- Expired reservations must be marked `EXPIRED` before a new overlapping reservation can be stored because PostgreSQL partial indexes cannot use a dynamic `now()` predicate safely.
- `ReserveTimeRange` expires overdue reservations before checking availability and saving a new reservation.
- `ReserveTimeRange` checks active ownership overlap through `OwnershipRepository`.
- The first implementation remains payment-provider neutral.

## Proposed Database Design

### `purchase_reservations`

Initial columns:

- `id uuid primary key`
- `buyer_id uuid not null`
- `start_second bigint not null`
- `end_second bigint not null`
- `amount_cents bigint not null`
- `currency varchar(3) not null`
- `status varchar(32) not null`
- `expires_at timestamptz not null`
- `created_at timestamptz not null`
- `updated_at timestamptz not null`

Constraints:

- `start_second >= 0`
- `end_second > start_second`
- `end_second <= 86400`
- `amount_cents > 0`
- `currency = 'USD'`
- `status` restricted to known reservation statuses
- active reservation ranges must not overlap

## PostgreSQL Overlap Strategy

Use a partial exclusion constraint:

```sql
exclude using gist (
  int8range(start_second, end_second, '[)') with &&
)
where (status in ('HELD', 'CHECKOUT_CREATED'));
```

Expired reservations are excluded from overlap only after their status is updated to `EXPIRED`.

## Use Case Behavior

`ReserveTimeRange` should:

1. Validate the requested range.
2. Expire overdue reservations through the reservation repository.
3. Check active ownership overlap.
4. Check active reservation overlap.
5. Calculate amount as duration seconds times 100 cents.
6. Save a `HELD` reservation with a short expiration window.

## Testing Plan

### Unit Tests

- Reservation amount equals duration in seconds times 100 cents.
- Reservation outside the canonical archive is rejected.
- Expired reservation detection works.
- `ReserveTimeRange` rejects ranges overlapping active ownership.
- `ReserveTimeRange` rejects ranges overlapping active reservation.
- `ReserveTimeRange` creates a held reservation for available range.

### Integration Tests

Use Testcontainers PostgreSQL for:

- Flyway V2 migration success.
- Insert active reservation.
- Reject overlapping active reservation.
- Allow adjacent active reservation.
- Allow overlapping expired reservation.
- Reject reservation beyond 86,400 seconds.
- Query active overlapping reservations.
- Expire overdue reservations.

## Verification Plan

- Run `.\gradlew.bat test`.
- Run `.\gradlew.bat build`.
- Run `docker build -t time-archive-api:local .` if Docker packaging is affected.
- Confirm Git status contains only intended changes.

## Risks and Rollback Strategy

- Risk: Active reservation overlap and active ownership overlap cannot be enforced by a single cross-table database constraint.
  - Mitigation: Enforce reservation-vs-reservation in PostgreSQL and reservation-vs-ownership in the transactional application use case. Re-check ownership during payment finalization later.
- Risk: Expired reservations can still block overlap if not marked `EXPIRED`.
  - Mitigation: `ReserveTimeRange` calls `expireOverdue` before overlap checks. A scheduled worker should be added later.
- Risk: User persistence does not exist yet.
  - Mitigation: Store `buyer_id` as UUID without FK until user persistence is implemented.
- Rollback: Revert this implementation commit before production. No production data exists yet.

## Open Questions

- Should reservation duration start at 10 minutes or a shorter value?
- Should `CHECKOUT_CREATED` be introduced now or only when checkout exists?

## Progress

- [x] Implementation plan created.
- [x] Domain model added.
- [x] Repository port added.
- [x] Use case added.
- [x] Flyway migration added.
- [x] Persistence adapter added.
- [x] Unit tests added.
- [x] Integration tests added.
- [x] Verification commands run.
- [x] Documentation updates completed if needed.
- [x] Completion details recorded.

## Completion Summary

Added purchase reservation support for the canonical 24-hour archive timeline.

The implementation introduces `PurchaseReservation`, `PurchaseReservationStatus`, `PurchaseReservationRepository`, the `ReserveTimeRange` use case, Flyway V2 schema migration, and a JDBC persistence adapter. Active reservation overlap is enforced in PostgreSQL for `HELD` and `CHECKOUT_CREATED` reservations. The application use case expires overdue reservations before checking ownership and reservation overlap.

## Files Changed

- `docs/architecture/domain-model.md`
- `docs/architecture/transaction-boundaries.md`
- `docs/implementation-plan/2026-06-16/add-purchase-reservation.md`
- `src/main/kotlin/com/timearchive/application/ReserveTimeRange.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcPurchaseReservationRepository.kt`
- `src/main/kotlin/com/timearchive/domain/model/PurchaseReservation.kt`
- `src/main/kotlin/com/timearchive/domain/model/PurchaseReservationStatus.kt`
- `src/main/kotlin/com/timearchive/domain/port/PurchaseReservationRepository.kt`
- `src/main/resources/db/migration/V2__create_purchase_reservations.sql`
- `src/test/kotlin/com/timearchive/application/ReserveTimeRangeTest.kt`
- `src/test/kotlin/com/timearchive/adapter/outbound/persistence/JdbcPurchaseReservationRepositoryIntegrationTest.kt`
- `src/test/kotlin/com/timearchive/domain/model/PurchaseReservationTest.kt`

## Tests Run and Results

- `.\gradlew.bat test` - passed.
- `.\gradlew.bat build` - passed.
- `docker build -t time-archive-api:local .` - passed.

## Manual Verification Results

- Confirmed Flyway V2 migration applies in Testcontainers PostgreSQL.
- Confirmed overlapping active reservations are rejected by PostgreSQL.
- Confirmed adjacent active reservations are allowed.
- Confirmed expired reservations do not block new overlapping reservations after status is `EXPIRED`.
- Confirmed `ReserveTimeRange` rejects active ownership overlap.
- Confirmed `ReserveTimeRange` rejects active reservation overlap.
- Confirmed reservation amount is calculated as 100 cents per second.

## Known Limitations

- No checkout creation exists yet.
- No payment webhook handling exists yet.
- No scheduled reservation expiration worker exists yet.
- `buyer_id` is not a foreign key because user persistence does not exist yet.
- `ReserveTimeRange` is not exposed through a REST API yet.

## Follow-Up Recommendations

- Add purchase/payment records and payment event idempotency next.
- Add audit log and outbox records before finalizing paid reservations into ownership.
- Add an inbound API only after authentication and request validation decisions are clear.
