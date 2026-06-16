# Add Canonical Timeline Ownership Persistence

## Objective

Implement the first persistence foundation for Time Archive by adding ownership records for one canonical 24-hour timeline and integration tests that prove active ownership ranges cannot overlap.

The product does not use seasons, editions, or repeatable timeline releases. Time Archive has one finite archive:

```text
24 hours = 86,400 seconds
```

This constraint is part of the product value. Adding new seasons after sellout would reduce the scarcity of owning one second in the archive, so the domain model must not introduce a `Season` concept.

## Scope

- Remove the previously proposed `Season` concept from the next implementation plan.
- Add ownership domain modeling for a single canonical 24-hour timeline.
- Add repository ports for ownership persistence.
- Add Flyway migration for the initial ownership schema.
- Use PostgreSQL constraints to enforce:
  - valid ownership ranges
  - ownership ranges stay within `0 <= second < 86,400`
  - active ownership ranges do not overlap
- Add outbound persistence adapter.
- Add Testcontainers-based integration tests for migrations and ownership overlap prevention.
- Update architecture documents that currently refer to seasons.

## Out of Scope

- Multiple timelines
- Seasons
- Editions
- Future archive expansion
- Purchase reservations
- Payment flow
- Media asset upload or moderation
- Timeline player API
- Authentication and authorization
- Admin APIs
- Resale, offers, transactions, and platform fees

## Relevant Files or Modules

Expected new or changed files:

- `src/main/kotlin/com/timearchive/domain/model/ArchiveTimeline.kt`
- `src/main/kotlin/com/timearchive/domain/model/OwnershipRecord.kt`
- `src/main/kotlin/com/timearchive/domain/model/OwnershipStatus.kt`
- `src/main/kotlin/com/timearchive/domain/model/AcquisitionType.kt`
- `src/main/kotlin/com/timearchive/domain/port/OwnershipRepository.kt`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/...`
- `src/main/resources/db/migration/V1__create_ownership_records.sql`
- `src/test/kotlin/com/timearchive/adapter/outbound/persistence/...`
- `docs/architecture/domain-model.md`
- `docs/architecture/time-archive-architecture.md`
- `docs/architecture/mvp-scope.md`
- `docs/architecture/transaction-boundaries.md`
- `docs/implementation-plan/2026-06-16/add-canonical-timeline-ownership-persistence.md`

Potentially changed files:

- `build.gradle.kts`
- `.gitattributes`
- `Dockerfile`
- `README.md`

## Key Design Decisions

- Time Archive has one canonical timeline.
- The canonical timeline length is fixed at 86,400 seconds.
- The product should not expose or imply multiple seasons.
- Ownership is stored as a ledger-style history table.
- Use inclusive start and exclusive end semantics:
  - `start_second` is inclusive.
  - `end_second` is exclusive.
- Represent current ownership with active rows where `valid_until` is null.
- Enforce active range non-overlap in PostgreSQL, not only in Kotlin code.
- Keep domain models free of Spring annotations.
- Add `org.springframework.boot:spring-boot-flyway` because Spring Boot 4.1.0 did not run Flyway migrations from `flyway-core` alone during integration testing.
- Add `.gitattributes` and normalize `gradlew` in Docker builds so the Linux container can execute the wrapper when the Windows working tree uses CRLF.

## Proposed Domain Shape

### `ArchiveTimeline`

`ArchiveTimeline` is not a user-facing feature. It is a small domain policy or value object that defines the fixed archive boundary:

```text
totalSeconds = 86,400
valid range = 0 <= second < 86,400
```

It may be implemented as an object or policy instead of a database table.

### `OwnershipRecord`

Ownership should be stored as history.

- `id`
- `startSecond`
- `endSecond`
- `ownerId`
- `status`
- `validFrom`
- `validUntil`
- `acquisitionType`
- `sourcePurchaseId`
- `sourceTransactionId`
- `createdAt`
- `updatedAt`

Current ownership is represented by active records where `validUntil` is null.

## Proposed Database Design

### `ownership_records`

Initial columns:

- `id uuid primary key`
- `start_second bigint not null`
- `end_second bigint not null`
- `owner_id uuid not null`
- `status varchar not null`
- `valid_from timestamptz not null`
- `valid_until timestamptz null`
- `acquisition_type varchar not null`
- `source_purchase_id uuid null`
- `source_transaction_id uuid null`
- `created_at timestamptz not null`
- `updated_at timestamptz not null`

Constraints:

- `start_second >= 0`
- `end_second > start_second`
- `end_second <= 86400`
- active ownership range must not overlap another active ownership range
- `status` restricted to known values
- `acquisition_type` restricted to known values

## PostgreSQL Overlap Strategy

Use PostgreSQL range semantics to enforce overlap prevention.

Preferred constraint:

```sql
exclude using gist (
  int8range(start_second, end_second, '[)') with &&
)
where (status = 'ACTIVE' and valid_until is null);
```

This avoids a `season_id` or `timeline_id` dimension because the product has exactly one archive timeline.

## Testing Plan

### Unit Tests

Add tests for:

- `ArchiveTimeline` exposes `86,400` total seconds.
- Time ranges must fit within the canonical timeline.
- `OwnershipRecord` rejects ranges outside the archive duration.
- Ownership status and acquisition type remain explicit.

### Integration Tests

Use Testcontainers PostgreSQL for:

- Flyway migration success.
- Insert active ownership record.
- Reject overlapping active ownership.
- Allow adjacent active ownership.
- Allow overlapping historical ownership when the previous record is no longer active.
- Reject ownership beyond 86,400 seconds.

### Verification Commands

Run:

```text
.\gradlew.bat test
.\gradlew.bat build
```

If persistence changes affect Docker packaging, also run:

```text
docker build -t time-archive-api:local .
```

## Risks and Rollback Strategy

- Risk: Hard-coding 86,400 seconds can look inflexible.
  - Mitigation: This is intentional product scarcity, not a technical limitation.
- Risk: Removing `Season` requires updating existing architecture documents.
  - Mitigation: Update the documents in the same implementation branch before code changes.
- Risk: PostgreSQL exclusion constraints may need careful migration syntax.
  - Mitigation: Verify through Testcontainers PostgreSQL.
- Risk: Spring Data JDBC mapping may not fit ledger-style ownership queries cleanly.
  - Mitigation: Keep repository ports independent and allow the adapter to use explicit SQL where clearer.
- Rollback: Revert this implementation commit before it reaches production. No production data exists yet.

## Verification Plan

- Run all unit tests.
- Run Testcontainers-backed integration tests.
- Run full Gradle build.
- Confirm Flyway migration applies on PostgreSQL.
- Confirm Git status contains only intended implementation and documentation changes.

## Open Questions

- Decision: `owner_id` remains a UUID without FK until user persistence is implemented.
- Decision: active ownership overlap prevention uses both `status = 'ACTIVE'` and `valid_until is null`.
- Decision: the outbound adapter uses explicit `NamedParameterJdbcTemplate` SQL for clarity around PostgreSQL range queries and constraints.

## Proposed Execution Order

1. Create the implementation branch from latest `main`.
2. Update architecture documents to remove `Season` and describe the canonical 24-hour timeline.
3. Add and verify Flyway migration for `ownership_records`.
4. Add canonical timeline and ownership domain models.
5. Add ownership repository port.
6. Add outbound persistence adapter with explicit SQL where needed.
7. Add Testcontainers integration tests for overlap behavior.
8. Run `.\gradlew.bat test`.
9. Run `.\gradlew.bat build`.
10. Record completion details in this plan.
11. Commit and push the implementation branch.

## Progress

- [x] Implementation plan created.
- [x] Removed `Season` from the proposed next step.
- [x] Implementation branch created.
- [x] Architecture documents updated.
- [x] Flyway migration added.
- [x] Domain models added.
- [x] Repository port added.
- [x] Persistence adapter added.
- [x] Unit tests added.
- [x] Integration tests added.
- [x] Verification commands run.
- [x] Completion details recorded.

## Completion Summary

Implemented canonical 24-hour archive ownership persistence without introducing seasons, editions, or multiple timelines.

The implementation adds an `ArchiveTimeline` domain policy fixed at 86,400 seconds, `OwnershipRecord` ledger-style domain modeling, an `OwnershipRepository` port, an explicit JDBC outbound adapter, and a PostgreSQL Flyway migration that prevents overlapping active ownership ranges with an exclusion constraint.

Architecture documents were updated to remove season-based modeling and describe Time Archive as one finite archive.

## Files Changed

- `.gitattributes`
- `Dockerfile`
- `build.gradle.kts`
- `docs/architecture/domain-model.md`
- `docs/architecture/mvp-scope.md`
- `docs/architecture/security-and-operations.md`
- `docs/architecture/time-archive-architecture.md`
- `docs/architecture/transaction-boundaries.md`
- `docs/implementation-plan/2026-06-16/add-backend-skeleton.md`
- `docs/implementation-plan/2026-06-16/add-canonical-timeline-ownership-persistence.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcOwnershipRepository.kt`
- `src/main/kotlin/com/timearchive/domain/model/AcquisitionType.kt`
- `src/main/kotlin/com/timearchive/domain/model/ArchiveTimeline.kt`
- `src/main/kotlin/com/timearchive/domain/model/OwnershipRecord.kt`
- `src/main/kotlin/com/timearchive/domain/model/OwnershipStatus.kt`
- `src/main/kotlin/com/timearchive/domain/port/OwnershipRepository.kt`
- `src/main/resources/db/migration/V1__create_ownership_records.sql`
- `src/test/kotlin/com/timearchive/adapter/outbound/persistence/JdbcOwnershipRepositoryIntegrationTest.kt`
- `src/test/kotlin/com/timearchive/domain/model/ArchiveTimelineTest.kt`
- `src/test/kotlin/com/timearchive/domain/model/OwnershipRecordTest.kt`
- `src/test/kotlin/com/timearchive/domain/model/TimeRangeTest.kt`

## Tests Run and Results

- `.\gradlew.bat test` - passed.
- `.\gradlew.bat build` - passed.
- `docker build -t time-archive-api:local .` - passed after adding `.gitattributes` and normalizing `gradlew` line endings in the Docker build.

## Manual Verification Results

- Confirmed Flyway applies `V1__create_ownership_records.sql` in Testcontainers PostgreSQL.
- Confirmed active overlapping ownership ranges are rejected by PostgreSQL.
- Confirmed adjacent active ownership ranges are allowed.
- Confirmed overlapping historical ownership is allowed.
- Confirmed ranges beyond 86,400 seconds are rejected.
- Confirmed architecture documents no longer model `Season` as a domain entity.

## Known Limitations

- User persistence does not exist yet, so `owner_id` is not a foreign key.
- Purchase and reservation tables do not exist yet.
- No public timeline API exists yet.
- Security is still framework-default and must be explicitly configured before exposing endpoints.

## Follow-Up Recommendations

- Add purchase reservation persistence next, using the same canonical archive boundary.
- Add audit log and outbox tables before payment finalization logic.
- Add a read model or query use case for timeline manifest windows.
