# Add Availability Read API

## Objective

Add a read API that lets clients check whether a range of seconds is available before creating a purchase reservation.

The current API can create reservations and checkout sessions, but clients do not have a safe read endpoint for availability. This step should add a minimal, OpenAPI-first availability endpoint that reports ownership and active reservation conflicts without changing purchase state.

## Scope

- Update `docs/api/openapi.yaml` before implementation.
- Add an availability query use case.
- Add a REST endpoint for checking a candidate time range.
- Add request validation for time range query parameters.
- Return a stable response that distinguishes available ranges from owned or reserved conflicts.
- Add focused unit tests for the use case.
- Add controller tests for success and validation/error responses.
- Keep the read path simple and backed by existing repositories.

## Out of Scope

- Timeline media playback APIs
- Full timeline manifest generation
- CDN-cacheable manifest chunks
- Search or filtering beyond range availability
- Authentication changes
- Reservation creation changes
- Checkout or payment changes
- Redis caching
- Background reservation expiration worker

## Relevant Files or Modules

Expected new or changed files:

- `docs/api/openapi.yaml`
- `src/main/kotlin/com/timearchive/application/CheckAvailability.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/AvailabilityController.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/AvailabilityDtos.kt`
- `src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `src/test/kotlin/com/timearchive/application/CheckAvailabilityTest.kt`
- `src/test/kotlin/com/timearchive/adapter/inbound/rest/AvailabilityControllerTest.kt`
- `docs/implementation-plan/2026-06-16/add-availability-read-api.md`

Potentially changed files:

- `README.md`
- `docs/architecture/domain-model.md`
- `docs/architecture/transaction-boundaries.md`

## Current State

- `ReserveTimeRange` checks active ownership overlap and active reservation overlap before creating a reservation.
- `PurchaseReservationRepository.findActiveOverlapping` exists.
- `OwnershipRepository.findActiveOverlapping` exists.
- OpenAPI already documents reservation and checkout creation.
- No read endpoint exists for availability.

## Key Design Decisions

- Follow OpenAPI-first sequencing.
- Keep availability read logic in an application use case, not in a controller.
- Reuse existing repository ports instead of adding new persistence abstractions.
- Treat `HELD` and `CHECKOUT_CREATED` reservations as unavailable.
- Expire overdue reservations before checking availability to reduce false conflicts.
- Do not create or mutate reservations from the availability endpoint.
- Return conflict summaries without leaking internal implementation details.
- Keep response shape stable enough for future frontend use.

## Proposed Endpoint Shape

```text
GET /api/archive/availability?startSecond=3600&endSecond=3660
```

Possible available response:

```json
{
  "startSecond": 3600,
  "endSecond": 3660,
  "available": true,
  "conflicts": []
}
```

Possible unavailable response:

```json
{
  "startSecond": 3600,
  "endSecond": 3660,
  "available": false,
  "conflicts": [
    {
      "type": "RESERVATION",
      "startSecond": 3600,
      "endSecond": 3610
    }
  ]
}
```

Recommended conflict types:

- `OWNERSHIP`
- `RESERVATION`

## Use Case Behavior

`CheckAvailability` should:

1. Build and validate a `TimeRange`.
2. Require the range to be within the canonical 24-hour archive.
3. Get current time from `ClockPort`.
4. Expire overdue reservations through `PurchaseReservationRepository.expireOverdue`.
5. Query active overlapping ownership records.
6. Query active overlapping purchase reservations.
7. Return `available = true` only when both conflict lists are empty.

## API Error Response

Use the existing API error response shape:

```json
{
  "code": "INVALID_REQUEST",
  "message": "Request validation failed",
  "details": [
    {
      "field": "startSecond",
      "message": "must be greater than or equal to 0"
    }
  ]
}
```

Expected validation:

- `startSecond >= 0`
- `startSecond <= 86399`
- `endSecond >= 1`
- `endSecond <= 86400`
- `endSecond > startSecond`

The last rule may be enforced by the use case and mapped to `INVALID_REQUEST`.

## Security Considerations

- Availability data is public read data for the MVP.
- Do not include owner IDs, buyer IDs, purchase IDs, payment references, or reservation IDs in the response.
- Do not leak whether a specific user owns a range.
- Keep the endpoint unauthenticated for development and public product use unless abuse requires rate limiting.
- Add rate limiting later if the endpoint becomes a scraping or abuse vector.

## Testing Plan

### Unit Tests

- Returns available when no ownership or reservation overlaps.
- Returns unavailable when ownership overlaps.
- Returns unavailable when active reservation overlaps.
- Expires overdue reservations before querying overlaps.
- Rejects invalid time ranges.

### Controller Tests

- Returns available response for valid query.
- Returns unavailable response with conflict summaries.
- Rejects missing query parameters.
- Rejects malformed query parameters.
- Rejects out-of-range values.
- Maps invalid range ordering to `INVALID_REQUEST`.

## Verification Plan

- Run `.\gradlew.bat test`.
- Run `.\gradlew.bat build`.
- Run `docker build -t time-archive-api:local .` because Spring wiring and REST endpoints change.
- Optionally run the app locally and manually call:

```text
GET /api/archive/availability?startSecond=0&endSecond=10
```

## MVP Completion Impact

This step improves the public API usability of the primary purchase flow. After this step, clients can check availability, create a reservation, and create checkout through the API.

Estimated MVP 1 completion after this step:

- Backend domain and persistence foundation: high
- Primary purchase backend flow: high for fake/local payment flow
- Public API usability: medium-high
- Production payment readiness: low
- Authentication and user readiness: low
- Frontend readiness: low
- Media moderation readiness: low

Overall MVP 1 completion would move from roughly 45-50% to roughly 50-55%.

## Risks and Rollback Strategy

- Risk: Availability can become stale between read and reservation creation.
  - Mitigation: Keep reservation creation as the source of truth and preserve transaction-time overlap checks.
- Risk: Returning too much conflict detail can leak user or purchase data.
  - Mitigation: Return only range and conflict type.
- Risk: Read traffic may grow faster than write traffic.
  - Mitigation: Keep implementation simple now; add caching or manifest-style read models only after real pressure appears.
- Rollback: Revert the implementation commit. The existing reservation and checkout APIs remain usable.

## Open Questions

- Should availability return all conflicts or only the first conflict?
- Should the endpoint live under `/api/archive/availability` or `/api/purchase/availability`?
- Should future frontend use this endpoint for hover/selection feedback, or should a broader timeline read model be added first?

## Proposed Execution Order

1. Create an implementation branch from latest `main`.
2. Add this implementation plan.
3. Update `docs/api/openapi.yaml`.
4. Add `CheckAvailability` use case.
5. Wire `CheckAvailability` in Spring configuration.
6. Add REST DTOs and controller.
7. Add unit tests for the use case.
8. Add controller tests.
9. Update docs if endpoint behavior needs clarification.
10. Run verification commands.
11. Record completion details in this plan.
12. Commit and push the implementation branch.

## Progress

- [x] Latest `main` pulled.
- [x] Implementation plan created.
- [ ] Implementation branch created.
- [ ] OpenAPI contract updated.
- [ ] Availability use case added.
- [ ] Spring configuration updated.
- [ ] REST DTOs added.
- [ ] Availability endpoint added.
- [ ] Unit tests added.
- [ ] Controller tests added.
- [ ] Documentation updates completed if needed.
- [ ] Verification commands run.
- [ ] Completion details recorded.
