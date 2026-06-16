# Add REST API Foundation

## Objective

Expose the existing MVP application use cases through a minimal, well-bounded REST API foundation without weakening ownership integrity, payment safety, or future authentication boundaries.

The current backend has core application use cases for reservation, checkout creation, and payment completion, but no inbound HTTP adapters. This step should add the first inbound adapter layer, stable request validation, stable error responses, and Spring wiring for existing use cases.

## Scope

- Add REST controllers for the current MVP purchase path where safe for the current development stage.
- Add request and response DTOs for inbound HTTP boundaries.
- Add centralized API error handling with stable error codes.
- Add validation for request body fields.
- Add Spring bean wiring for application use cases that are not currently registered as beans.
- Add controller tests for validation, success responses, and error responses.
- Document temporary development-stage authentication assumptions clearly.

## Out of Scope

- Real user registration and login
- JWT/session authentication implementation
- OAuth/social login
- Admin APIs
- Frontend implementation
- Real payment provider integration
- Real payment webhook signature verification
- Public production deployment configuration
- Resale, offer, or media upload APIs

## Relevant Files or Modules

Expected new or changed files:

- `src/main/kotlin/com/timearchive/adapter/inbound/rest/PurchaseController.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiErrorResponse.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/PurchaseDtos.kt`
- `src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `src/main/kotlin/com/timearchive/configuration/SecurityConfiguration.kt`
- `src/test/kotlin/com/timearchive/adapter/inbound/rest/PurchaseControllerTest.kt`
- `docs/implementation-plan/2026-06-16/add-rest-api-foundation.md`

Potentially changed files:

- `docs/architecture/security-and-operations.md`
- `docs/architecture/transaction-boundaries.md`
- `README.md`

## Current State

- `ReserveTimeRange` exists but is not exposed through HTTP.
- `CreateCheckout` exists but is not exposed through HTTP.
- `CompletePrimaryPurchase` exists but is not exposed through HTTP.
- Spring Web, Spring Security, and Spring Validation are already dependencies.
- No REST controllers exist yet.
- No API error response convention exists yet.
- No authentication domain exists yet.

## Key Design Decisions

- Keep REST controllers as inbound adapters only.
- Do not put business rules in controllers.
- Keep request and response DTOs out of the domain layer.
- Use application use cases as the boundary from HTTP into core behavior.
- Add a centralized exception handler before adding multiple controllers.
- Use stable API error codes from the first REST endpoint.
- Do not expose payment finalization as a browser-success callback.
- Do not treat checkout redirect success as payment confirmation.

## Recommended Endpoint Shape

Initial development-stage endpoints:

```text
POST /api/purchase/reservations
POST /api/purchase/reservations/{reservationId}/checkout
```

Possible request and response shapes:

```json
{
  "buyerId": "00000000-0000-0000-0000-000000000001",
  "startSecond": 3600,
  "endSecond": 3660
}
```

```json
{
  "reservationId": "00000000-0000-0000-0000-000000000010",
  "buyerId": "00000000-0000-0000-0000-000000000001",
  "startSecond": 3600,
  "endSecond": 3660,
  "amountCents": 6000,
  "currency": "USD",
  "status": "HELD",
  "expiresAt": "2026-06-16T09:00:00Z"
}
```

```json
{
  "provider": "fake",
  "providerReference": "fake_checkout_00000000-0000-0000-0000-000000000010",
  "checkoutUrl": "https://payments.example.test/checkout/00000000-0000-0000-0000-000000000010"
}
```

## Authentication Assumption

Because no user or authentication implementation exists yet, this step should either:

1. Keep `buyerId` in the request body and document that the endpoint is development-stage only, or
2. Add a narrow development-only authenticated principal resolver.

Recommendation: keep `buyerId` in the request body for this step and clearly mark it as non-production behavior. This keeps the API foundation small while avoiding a speculative authentication design.

Before production, `buyerId` must come from authenticated server-side identity, not from the request body.

## Error Response Standard

Use a stable response shape from the first controller:

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

Recommended initial error codes:

- `INVALID_REQUEST`
- `RESOURCE_NOT_FOUND`
- `TIME_RANGE_ALREADY_OWNED`
- `TIME_RANGE_ALREADY_RESERVED`
- `RESERVATION_EXPIRED`
- `RESERVATION_NOT_PAYABLE`
- `UNEXPECTED_ERROR`

This step may initially map current `IllegalArgumentException` and `IllegalStateException` to safe API errors. A later step should introduce explicit domain/application exceptions once the API surface stabilizes.

## Security Considerations

- Do not expose stack traces in API responses.
- Do not log secrets, payment payloads, or private user data.
- Do not trust request-body `buyerId` beyond local development.
- Keep CSRF and authentication behavior explicit in `SecurityConfiguration`.
- Keep payment completion out of public unauthenticated browser flows.

## Testing Plan

### Controller Tests

- Creates a reservation with valid input.
- Rejects invalid time range input.
- Rejects malformed UUID input.
- Creates checkout for an existing held reservation.
- Returns a stable error response when checkout is not allowed.
- Does not expose internal exception details.

### Configuration Tests

- Verifies use case beans can be created by the Spring context.
- Verifies controller wiring starts with application context.

## Verification Plan

- Run `.\gradlew.bat test`.
- Run `.\gradlew.bat build`.
- Run `docker build -t time-archive-api:local .` if packaging or Spring wiring is changed.
- Optionally run the app locally and manually call the endpoints with HTTP requests.

## MVP Completion Impact

This step makes the current backend behavior externally callable for development and manual verification. It does not make the product production-ready because authenticated identity, real payment provider integration, webhook verification, and frontend flows remain missing.

Estimated MVP 1 completion after this step:

- Backend domain and persistence foundation: high
- Primary purchase backend flow: medium-high
- Public API usability: medium
- Production payment readiness: low
- Authentication and user readiness: low
- Frontend readiness: low

Overall MVP 1 completion would move from roughly 35-40% to roughly 45-50%.

## Risks and Rollback Strategy

- Risk: Exposing `buyerId` in request body can be mistaken for production-safe identity.
  - Mitigation: Document it as development-stage only and keep production authentication as a required follow-up.
- Risk: Mapping generic exceptions may create unstable error semantics.
  - Mitigation: Centralize mapping now and introduce explicit application exceptions later.
- Risk: Spring Security defaults may block intended endpoints or accidentally allow too much.
  - Mitigation: Add an explicit minimal security configuration and controller tests.
- Rollback: Revert the implementation commit. No production API compatibility exists yet.

## Open Questions

- Should the development-stage API allow request-body `buyerId`, or should a temporary authenticated principal be introduced immediately?
- Should payment completion be exposed as an internal test-only endpoint, or wait until webhook signature verification exists?
- Should availability read APIs be added in this step or kept separate from purchase command APIs?

## Proposed Execution Order

1. Create an implementation branch from latest `main`.
2. Add this implementation plan.
3. Add use case Spring configuration.
4. Add REST DTOs.
5. Add centralized API error response and exception handler.
6. Add minimal explicit Spring Security configuration.
7. Add reservation creation endpoint.
8. Add checkout creation endpoint.
9. Add focused controller tests.
10. Update architecture or README docs if endpoint behavior is documented.
11. Run verification commands.
12. Record completion details in this plan.
13. Commit and push the implementation branch.

## Progress

- [x] Latest `main` pulled.
- [x] Implementation plan created.
- [ ] Implementation branch created.
- [ ] Use case Spring configuration added.
- [ ] REST DTOs added.
- [ ] API error handling added.
- [ ] Security configuration added.
- [ ] Reservation endpoint added.
- [ ] Checkout endpoint added.
- [ ] Controller tests added.
- [ ] Documentation updates completed if needed.
- [ ] Verification commands run.
- [ ] Completion details recorded.
