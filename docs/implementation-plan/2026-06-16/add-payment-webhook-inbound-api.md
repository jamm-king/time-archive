# Add Payment Webhook Inbound API

## Objective

Expose the existing primary purchase completion use case through a development-stage inbound webhook API so the local MVP purchase flow can be verified end to end.

The current backend can reserve a range and create checkout, and `CompletePrimaryPurchase` can finalize payment internally. This step adds the inbound REST boundary for fake/local payment confirmation while preserving the rule that ownership is granted only after a payment event is processed.

## Scope

- Update `docs/api/openapi.yaml` before implementation.
- Add a development-stage payment webhook endpoint.
- Add request and response DTOs for fake/local payment confirmation.
- Call `CompletePrimaryPurchase` from the inbound REST adapter.
- Return stable response fields including duplicate-event behavior.
- Add controller tests for success, duplicate event behavior, validation, and safe error responses.
- Keep real payment provider signature verification out of scope but document it clearly.

## Out of Scope

- Real Stripe integration
- Real webhook signature verification
- Payment provider SDK dependency
- Checkout attempt persistence
- Authentication changes
- Frontend changes
- Outbox dispatcher
- Refund, failure, or dispute handling

## Relevant Files or Modules

Expected new or changed files:

- `docs/api/openapi.yaml`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/PaymentWebhookController.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/PaymentWebhookDtos.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `src/test/kotlin/com/timearchive/adapter/inbound/rest/PaymentWebhookControllerTest.kt`
- `docs/implementation-plan/2026-06-16/add-payment-webhook-inbound-api.md`

Potentially changed files:

- `README.md`
- `docs/architecture/security-and-operations.md`
- `docs/architecture/transaction-boundaries.md`

## Current State

- `CompletePrimaryPurchase` exists and is wired as a Spring bean.
- Payment event idempotency exists through `(provider, providerEventId)`.
- Purchase, ownership, audit log, and outbox persistence adapters exist.
- Reservation and checkout creation are exposed through REST.
- No inbound payment webhook endpoint exists.
- No real webhook signature verification exists.

## Key Design Decisions

- Follow OpenAPI-first sequencing.
- Treat this endpoint as development-stage only.
- Use a fake/local provider path to avoid implying production payment readiness.
- Keep browser checkout redirect separate from payment confirmation.
- Do not introduce a payment provider SDK yet.
- Do not add a new dependency.
- Reuse `CompletePrimaryPurchase` as the application boundary.
- Preserve duplicate webhook behavior by returning `alreadyProcessed`.

## Proposed Endpoint Shape

```text
POST /api/internal/payments/fake/webhooks/primary-purchase-completed
```

Possible request:

```json
{
  "providerEventId": "evt_local_1",
  "eventType": "payment_intent.succeeded",
  "payloadHash": "sha256-local-test-payload",
  "reservationId": "00000000-0000-0000-0000-000000000010",
  "paymentReference": "pi_local_1",
  "requestId": "local-request-1"
}
```

Possible response:

```json
{
  "purchaseId": "00000000-0000-0000-0000-000000000020",
  "ownershipRecordId": "00000000-0000-0000-0000-000000000030",
  "alreadyProcessed": false
}
```

For a duplicate already-processed event:

```json
{
  "purchaseId": "00000000-0000-0000-0000-000000000020",
  "ownershipRecordId": null,
  "alreadyProcessed": true
}
```

## Security Considerations

- This endpoint is not production-safe.
- Production webhook endpoints must verify provider signatures before processing.
- The fake endpoint must be clearly named as internal/fake/development-stage.
- Do not expose raw payloads or secrets in request or response.
- Do not use browser redirects as payment confirmation.
- Do not log sensitive provider payloads.

## Error Response Standard

Use the existing `ApiErrorResponse` shape.

Expected mappings:

- Missing or malformed fields: `400 INVALID_REQUEST`
- Missing reservation: `404 RESOURCE_NOT_FOUND`
- Expired reservation: `409 RESERVATION_EXPIRED`
- Reservation not payable: `409 RESERVATION_NOT_PAYABLE`
- Ownership conflict: `409 TIME_RANGE_ALREADY_OWNED`
- Duplicate in-progress event: `409 PAYMENT_EVENT_ALREADY_PROCESSING`
- Unexpected failures: `500 UNEXPECTED_ERROR`

This step may add `PAYMENT_EVENT_ALREADY_PROCESSING` to the OpenAPI error code enum.

## Testing Plan

### Controller Tests

- Completes payment and returns purchase and ownership IDs.
- Returns `alreadyProcessed = true` for duplicate processed event result.
- Rejects invalid request body.
- Maps missing reservation to `RESOURCE_NOT_FOUND`.
- Maps expired reservation to `RESERVATION_EXPIRED`.
- Maps non-payable reservation to `RESERVATION_NOT_PAYABLE`.
- Maps ownership conflict to `TIME_RANGE_ALREADY_OWNED`.
- Maps duplicate in-progress event to `PAYMENT_EVENT_ALREADY_PROCESSING`.
- Does not expose internal exception details.

### Existing Integration Tests

Existing `CompletePrimaryPurchaseIntegrationTest` already verifies:

- purchase, ownership, audit logs, and outbox events are created transactionally
- duplicate processed event does not create duplicate ownership
- expired reservation rollback
- ownership conflict rollback

No new database integration test is required unless controller wiring reveals a gap.

## Verification Plan

- Run `.\gradlew.bat test`.
- Run `.\gradlew.bat build`.
- Run `docker build -t time-archive-api:local .` because REST wiring changes.
- Optionally run the app locally and manually call the fake webhook endpoint after creating a reservation.

## MVP Completion Impact

This step closes the local backend primary purchase loop:

1. Check availability.
2. Create reservation.
3. Create checkout.
4. Confirm fake/local payment event.
5. Create ownership.

Estimated MVP 1 completion after this step:

- Backend primary purchase flow: high for fake/local payment flow
- Public API usability: medium-high
- Production payment readiness: still low until signature verification and real provider integration exist
- Authentication and user readiness: low
- Media moderation readiness: low
- Frontend readiness: low

Overall MVP 1 completion would move from roughly 50-55% to roughly 55-60%.

## Risks and Rollback Strategy

- Risk: A fake webhook endpoint can be mistaken for production-safe payment handling.
  - Mitigation: Use an explicit `/api/internal/payments/fake/...` path and document limitations.
- Risk: Generic exception-message mapping can become brittle.
  - Mitigation: Keep mappings centralized and add explicit exceptions later.
- Risk: Duplicate event behavior can be misunderstood.
  - Mitigation: Return `alreadyProcessed` explicitly.
- Rollback: Revert the implementation commit. Existing reservation and checkout APIs remain usable.

## Open Questions

- Should the fake webhook endpoint be disabled by configuration outside local/dev profiles?
- Should request signing for fake/local webhook be simulated now or deferred until real provider integration?
- Should `payloadHash` be computed server-side from raw body later instead of accepted from JSON?

## Proposed Execution Order

1. Create implementation branch from latest `main`.
2. Add this implementation plan.
3. Update `docs/api/openapi.yaml`.
4. Add webhook request/response DTOs.
5. Add payment webhook controller.
6. Update API exception mappings and OpenAPI error code enum.
7. Add controller tests.
8. Update architecture or README docs if needed.
9. Run verification commands.
10. Record completion details in this plan.
11. Commit and push the implementation branch.

## Progress

- [x] Latest `main` pulled.
- [x] Implementation branch created.
- [x] Implementation plan created.
- [x] OpenAPI contract updated.
- [x] Webhook DTOs added.
- [x] Webhook controller added.
- [x] API error mapping updated.
- [x] Controller tests added.
- [x] Documentation updates completed if needed.
- [x] `.\gradlew.bat test` passed.
- [x] `.\gradlew.bat build` passed.
- [x] Docker image build passed after retry.
- [x] Completion details recorded.

## Implementation Notes

- Added the fake/local webhook endpoint to `docs/api/openapi.yaml`.
- Added `PaymentWebhookController` under the inbound REST adapter layer.
- Added request and response DTOs for fake primary purchase completion.
- Hard-coded the provider as `fake` for the development-stage endpoint.
- Reused `CompletePrimaryPurchase` as the application boundary.
- Added `PAYMENT_EVENT_ALREADY_PROCESSING` to the API error code enum.
- Updated centralized API exception mapping for duplicate in-progress payment events.
- Documented that this endpoint is not production-safe and must be replaced by verified provider webhook handling.

## Completion Summary

The development-stage fake payment webhook inbound API was implemented. The backend can now complete the local primary purchase flow through HTTP:

1. Check availability.
2. Create reservation.
3. Create checkout.
4. Submit fake payment event.
5. Create purchase, ownership, audit logs, and outbox events through the existing transactional use case.

## Files Changed

- `README.md`
- `docs/api/openapi.yaml`
- `docs/architecture/security-and-operations.md`
- `docs/architecture/transaction-boundaries.md`
- `docs/implementation-plan/2026-06-16/add-payment-webhook-inbound-api.md`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/PaymentWebhookController.kt`
- `src/main/kotlin/com/timearchive/adapter/inbound/rest/PaymentWebhookDtos.kt`
- `src/test/kotlin/com/timearchive/adapter/inbound/rest/PaymentWebhookControllerTest.kt`

## Tests Run and Results

- `.\gradlew.bat test`: passed.
- `.\gradlew.bat build`: passed.
- `docker build -t time-archive-api:local .`: first attempt failed during Docker Desktop image unpack with a local snapshot error after `bootJar` succeeded; retry passed.

## Manual Verification Results

- Verified successful fake payment response shape through controller tests.
- Verified duplicate already-processed response shape through controller tests.
- Verified invalid request body returns `INVALID_REQUEST`.
- Verified missing reservation maps to `RESOURCE_NOT_FOUND`.
- Verified expired reservation maps to `RESERVATION_EXPIRED`.
- Verified non-payable reservation maps to `RESERVATION_NOT_PAYABLE`.
- Verified ownership conflict maps to `TIME_RANGE_ALREADY_OWNED`.
- Verified duplicate in-progress event maps to `PAYMENT_EVENT_ALREADY_PROCESSING`.
- Verified unexpected internal exception details are not exposed.

## Known Limitations

- The fake webhook endpoint is not production-safe.
- No real provider signature verification exists yet.
- No payment provider SDK or real checkout integration exists yet.
- `payloadHash` is accepted from JSON for local testing instead of computed from a raw signed payload.
- The endpoint is permitted by the current development-stage security configuration.

## Follow-Up Recommendations

- Add real provider webhook verification before production payment collection.
- Gate fake/internal endpoints by profile or remove them before production.
- Replace message-based exception mapping with explicit application exceptions.
- Add an end-to-end local manual verification script after frontend or API client tooling exists.
