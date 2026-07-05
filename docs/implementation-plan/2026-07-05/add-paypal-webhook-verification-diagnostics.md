# Add PayPal Webhook Verification Diagnostics

## Objective

Make PayPal webhook signature verification failures diagnosable after the
webhook reaches the API directly. The goal is to distinguish PayPal
`verification_status=FAILURE` from HTTP-level verification API errors without
logging secrets or raw payment payloads.

## Scope

- Add safe diagnostic logging to the PayPal webhook verifier client.
- Return `false` for PayPal verification API HTTP failures so webhook handling
  fails closed as an invalid signature instead of surfacing as an unexpected
  provider exception.
- Add focused tests for successful verification, failed verification, and
  HTTP-level verification errors.
- Do not change public API contracts, database schema, or payment finalization
  semantics.

## Relevant Files

- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/payment/RestClientPayPalWebhookVerifierClient.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/outbound/payment/RestClientPayPalWebhookVerifierClientTest.kt`

## Key Design Decisions

- Do not log PayPal access tokens, client secrets, transmission signatures,
  webhook IDs, raw webhook payloads, or full cert URLs.
- Log only safe metadata:
  - event id and event type;
  - verification status;
  - HTTP status for verification API failures;
  - auth algorithm;
  - cert URL host;
  - masked transmission id.
- Keep the verifier port return type unchanged.
- Treat non-`SUCCESS` verification as `false`.

## Step-by-Step Execution Plan

1. Create a dedicated branch from latest `main`.
2. Add this implementation plan.
3. Add safe diagnostic logging and HTTP error handling to the verifier client.
4. Add focused tests using Spring `MockRestServiceServer`.
5. Run focused verifier tests.
6. Run relevant PayPal webhook application tests.
7. Run `git diff --check`.
8. Update this plan with completion details.

## Risks and Rollback Strategy

- Risk: new logs may expose sensitive values.
  - Mitigation: restrict log fields to non-secret event metadata and masked
    identifiers.
- Risk: swallowing HTTP errors as `false` hides provider availability problems.
  - Mitigation: log HTTP status and keep webhook processing fail-closed; PayPal
    will retry the webhook because the API rejects it.
- Rollback: revert this branch. Webhook verification returns to the previous
  behavior.

## Verification Plan

- `apps/api/gradlew.bat test --tests "com.timearchive.adapter.outbound.payment.RestClientPayPalWebhookVerifierClientTest" --max-workers=2`
- `apps/api/gradlew.bat test --tests "com.timearchive.application.CompletePayPalWebhookTest" --max-workers=2`
- `git diff --check`
- After deployment, trigger a PayPal Sandbox webhook and inspect CloudWatch for
  `paypal webhook verification`.

## Open Questions

- The exact PayPal verification API response for the current staging failure is
  unknown until this change is deployed and the webhook is retried.

## Progress

- [x] Created implementation plan.
- [x] Added verifier diagnostics.
- [x] Added tests.
- [x] Ran verification.

## Completion Summary

Added safe PayPal webhook verification diagnostics and changed the verification
request body construction to explicitly serialize the PayPal payload with the
application `ObjectMapper`. Focused tests showed that relying on the default
`RestClient` body conversion did not preserve the nested `webhook_event` fields
as expected in the verification request. The verifier now sends a JSON string
with the expected PayPal `webhook_event` object shape.

Verification failures now log safe metadata only:

- event id;
- event type;
- verification status;
- HTTP status for verification API failures;
- auth algorithm;
- cert URL host;
- masked transmission id.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/payment/RestClientPayPalWebhookVerifierClient.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/outbound/payment/RestClientPayPalWebhookVerifierClientTest.kt`
- `docs/implementation-plan/2026-07-05/add-paypal-webhook-verification-diagnostics.md`

## Tests Run and Results

- `apps/api/gradlew.bat test --tests "com.timearchive.adapter.outbound.payment.RestClientPayPalWebhookVerifierClientTest" --max-workers=2`: passed.
- `apps/api/gradlew.bat test --tests "com.timearchive.application.CompletePayPalWebhookTest" --tests "com.timearchive.configuration.HttpClientConfigurationTest" --max-workers=2`: passed.
- `git diff --check`: passed.

## Manual Verification Results

- Not deployed yet. After deployment, run the PayPal Sandbox checkout flow again
  and inspect CloudWatch API logs for `paypal webhook verification completed`.

## Known Limitations

- The staging PayPal webhook success still needs live verification after this
  branch is merged and redeployed.
- The verifier still fails closed for HTTP-level PayPal verification errors by
  returning `false`; PayPal webhook processing will reject the delivery and rely
  on PayPal retry behavior.

## Follow-up Recommendations

- Merge, publish staging images, deploy staging, and rerun the PayPal Sandbox
  purchase flow.
- If the verification status remains `FAILURE`, compare the logged auth
  algorithm, cert host, event id, and PayPal dashboard delivery metadata.
