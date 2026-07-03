# Add PayPal Webhook Finalization

## Objective

Finalize PayPal-captured purchases from verified PayPal webhooks. A verified
`PAYMENT.CAPTURE.COMPLETED` webhook should validate the provider payload against
server-side checkout and reservation records, then call `CompletePrimaryPurchase`
to create the purchase and ownership records exactly once.

## Scope

- Add PayPal webhook runtime configuration for `webhook-id`.
- Add a PayPal webhook verification client using PayPal's official verification
  endpoint.
- Add a PayPal webhook application use case that verifies signatures, parses the
  capture event, validates amount/currency/reservation/provider references, and
  finalizes the purchase.
- Add a public API endpoint for PayPal webhooks with CSRF ignored only for that
  endpoint.
- Update OpenAPI and operational documentation.
- Add focused tests for signature rejection, ignored events, successful
  completion, duplicate handling, and delayed webhook finalization.

## Out Of Scope

- Automated PayPal Sandbox webhook smoke workflow.
- Creating PayPal webhook resources through code.
- Handling refunds, reversals, disputes, or non-primary-purchase events.
- Storing raw PayPal webhook payloads.

## Relevant Files Or Modules

- `apps/api/src/main/kotlin/com/timearchive/application/CompletePayPalWebhook.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/CompletePrimaryPurchase.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/PayPalWebhookController.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/payment/PayPalWebhookVerifierClient.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/payment/RestClientPayPalWebhookVerifierClient.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/PayPalPaymentProperties.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/JsonConfiguration.kt`
- `docs/api/openapi.yaml`
- `docs/operations/paypal-integration-design.md`

## Key Design Decisions

- PayPal webhook signature verification is performed before payload-driven
  finalization.
- Only `PAYMENT.CAPTURE.COMPLETED` is finalized in this branch.
- Unsupported but verified PayPal events return success without side effects so
  PayPal does not retry irrelevant events forever.
- The webhook must validate the PayPal `custom_id` reservation ID, capture ID,
  order ID when present, amount, and currency against local records.
- Capture webhook delay must not break a valid payment. `CompletePrimaryPurchase`
  will allow finalization when the provider completion time is before the
  reservation expiration, even if the webhook arrives later.
- Raw webhook payloads are hashed for audit/idempotency but not stored.

## Official Reference Notes

PayPal documents a webhook signature verification endpoint under Webhooks
Management. The verification request includes the PayPal transmission headers,
the configured webhook ID, and the webhook event body. A completed capture event
uses `PAYMENT.CAPTURE.COMPLETED`.

Source checked on 2026-07-03:

- PayPal Webhooks Management API.
- PayPal webhook event names.

## Step-By-Step Execution Plan

- [x] Create this implementation plan.
- [x] Add `webhookId` PayPal runtime property and deployment env mapping.
- [x] Add PayPal webhook verification port/client.
- [x] Add PayPal webhook finalization use case.
- [x] Add PayPal webhook REST endpoint and CSRF exception.
- [x] Update payment completion expiration rule for provider completion time.
- [x] Add focused tests.
- [x] Update OpenAPI and operations docs.
- [x] Run API tests and OpenAPI validation.

## Risks And Rollback Strategy

- Risk: Incorrect webhook parsing could grant ownership for the wrong
  reservation.
  - Mitigation: Validate reservation ID, amount, currency, capture ID, and order
    ID against local checkout and reservation state before completing.
- Risk: Signature verification misconfiguration could block valid PayPal events.
  - Mitigation: Fail closed and document the required SSM `webhook-id`.
- Risk: Unsupported events could trigger provider retries.
  - Mitigation: Return success for verified unsupported events with no side
    effects.

Rollback is a normal code rollback before production payment collection. If
staging receives Sandbox events during rollback, reconcile PayPal dashboard
events against local `payment_events`, `purchases`, and `ownership_records`.

## Verification Plan

- Run focused PayPal webhook use case and controller tests.
- Run affected payment completion tests.
- Run full API tests.
- Run OpenAPI validation.
- Run `git diff --check`.

## Open Questions

- The staging PayPal webhook ID must be configured in SSM before live Sandbox
  webhook verification can pass.

## Progress Log

- 2026-07-03: Started after PayPal approval/capture reached
  `CAPTURED_PENDING_WEBHOOK` on staging.
- 2026-07-03: Added PayPal webhook verification client, finalization use case,
  REST endpoint, runtime config, docs, and focused tests.
- 2026-07-03: Full API tests, OpenAPI validation, deployment validation scripts,
  and whitespace checks passed.

## Completion Summary

PayPal webhook finalization is implemented for verified
`PAYMENT.CAPTURE.COMPLETED` events. The endpoint verifies PayPal signature
headers through PayPal's verification API, validates the capture payload against
local checkout and reservation records, and calls `CompletePrimaryPurchase` to
create purchase and ownership records. Unsupported verified events are
acknowledged without side effects.

## Files Changed

- Added PayPal webhook verification client and disabled fallback.
- Added `CompletePayPalWebhook` use case and REST endpoint.
- Added `TIME_ARCHIVE_PAYPAL_WEBHOOK_ID` runtime configuration.
- Updated `CompletePrimaryPurchase` so provider-completed payments can finalize
  when the webhook arrives after reservation expiration.
- Updated OpenAPI, operational docs, release readiness checklist, and
  deployment runtime examples.
- Added focused application and controller tests.

## Tests Run And Results

- `.\gradlew.bat test --tests "com.timearchive.application.CompletePayPalWebhookTest" --tests "com.timearchive.application.CompletePrimaryPurchaseTest" --tests "com.timearchive.adapter.inbound.rest.PayPalWebhookControllerTest" --max-workers=2`: passed.
- `.\gradlew.bat test --max-workers=2`: passed.
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-openapi.sh`: passed.
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-production-deployment.sh`: passed.
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-staging-deployment-runtime.sh`: passed.
- `git diff --check`: passed.

## Manual Verification Results

No live PayPal Sandbox webhook was triggered in this branch. Staging requires a
PayPal webhook resource and SSM `/time-archive/staging/paypal/webhook-id` before
live verification can pass.

## Known Limitations

- Only `PAYMENT.CAPTURE.COMPLETED` finalizes ownership.
- Refund, dispute, reversal, and denied capture handling remain follow-up work.
- Automated staging PayPal webhook smoke is not included yet.

## Follow-Up Recommendations

- Create/configure the PayPal Sandbox webhook URL:
  `https://staging.time-archive.com/api/payments/paypal/webhooks`.
- Store the Sandbox webhook ID in SSM as
  `/time-archive/staging/paypal/webhook-id`.
- Redeploy staging and run a PayPal Sandbox purchase through webhook
  finalization.
