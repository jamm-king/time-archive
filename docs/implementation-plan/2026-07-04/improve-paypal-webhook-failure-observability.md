# Improve PayPal Webhook Failure Observability

## Objective

Make PayPal webhook rejection causes observable without logging secrets, raw payloads,
or signature material. The immediate goal is to distinguish whether staging webhook
events are rejected because of signature verification, missing/invalid payload fields,
or local payment state mismatches.

## Scope

- Add safe failure reason logging to PayPal webhook finalization.
- Map PayPal webhook-specific failures to explicit API error codes.
- Add focused tests for the new error response mapping.
- Keep the existing PayPal webhook endpoint and payload contract unchanged.

## Relevant Files

- `apps/api/src/main/kotlin/com/timearchive/application/CompletePayPalWebhook.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest/PayPalWebhookControllerTest.kt`

## Key Design Decisions

- Do not log raw webhook payloads, PayPal signatures, cert URLs, webhook IDs,
  client secrets, or access tokens.
- Keep the existing exception style to minimize risk in the current release branch.
- Emit stable reason codes in logs so CloudWatch queries can identify the failure
  class quickly.
- Keep unsupported webhook events as successful ignored responses.

## Execution Plan

1. Create a dedicated fix branch from latest `main`.
2. Add this implementation plan.
3. Add PayPal webhook failure reason classification and warning logs.
4. Add explicit REST error mappings for invalid webhook input and local state
   mismatches.
5. Update and extend controller tests.
6. Run focused tests and static diff checks.
7. Update this plan with completion details.

## Risks and Rollback Strategy

- Risk: overly broad message matching could change an unrelated error response.
  Mitigation: limit new mappings to PayPal webhook-specific message prefixes.
- Risk: logs may accidentally include sensitive values.
  Mitigation: log only stable reason codes and non-secret event identifiers.
- Rollback: revert this branch. The runtime behavior returns to generic 400
  responses without detailed webhook failure logs.

## Verification Plan

- Run focused PayPal webhook controller tests.
- Run PayPal webhook application tests.
- Run `git diff --check`.
- After deployment, resend or trigger a PayPal sandbox webhook and query
  CloudWatch for `paypalWebhookReason`.

## Open Questions

- The exact staging failure class is still unknown until a real PayPal webhook is
  retried after deployment.

## Progress

- [x] Created implementation plan.
- [x] Added safe webhook failure logging.
- [x] Added explicit REST error mappings.
- [x] Added or updated tests.
- [x] Ran verification.

## Completion Summary

PayPal webhook rejection handling now emits safe, searchable warning logs with
`paypalWebhookReason` while avoiding raw payloads, signature headers, cert URLs,
webhook IDs, and secrets. REST error responses now distinguish invalid PayPal
webhooks from local payment state mismatches.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/application/CompletePayPalWebhook.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest/PayPalWebhookControllerTest.kt`
- `docs/implementation-plan/2026-07-04/improve-paypal-webhook-failure-observability.md`

## Tests Run and Results

- `apps/api/gradlew.bat test --tests "com.timearchive.adapter.inbound.rest.PayPalWebhookControllerTest" --tests "com.timearchive.application.CompletePayPalWebhookTest" --max-workers=2`: passed.
- `git diff --check`: passed.

## Manual Verification Results

- Not deployed yet. After deployment, trigger or resend a PayPal sandbox webhook
  and search CloudWatch API logs for `paypalWebhookReason`.

## Known Limitations

- This change does not fix the underlying PayPal webhook rejection yet. It makes
  the next staging webhook retry diagnosable.
- JSON parse failures before a PayPal event id can be extracted still use the
  existing generic request handling.

## Follow-up Recommendations

- Redeploy staging and trigger a PayPal sandbox webhook.
- If `paypalWebhookReason=SIGNATURE_VERIFICATION_FAILED`, verify sandbox/live
  environment consistency and the exact PayPal webhook id stored in SSM.
- If a local mismatch reason appears, compare the PayPal capture/order fields
  with the local checkout attempt and reservation records.
