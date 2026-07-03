# Add PayPal Capture Flow

## Objective

Add the PayPal approval return and server-side capture flow while preserving the
payment finalization boundary. Capturing a PayPal order must record provider
state only; ownership must still wait for a future verified PayPal webhook.

## Scope

- Add checkout attempt capture columns and statuses.
- Add a PayPal capture use case and repository methods.
- Extend the PayPal order client with order capture.
- Add API and Web proxy routes for authenticated capture.
- Add a PayPal return page and cancel page for browser flow coordination.
- Update OpenAPI and operational documentation.
- Add focused tests for authorization, idempotency, mapping, and routing.

## Out Of Scope

- PayPal webhook signature verification.
- Ownership finalization from PayPal events.
- Production PayPal live configuration.
- Creating or updating real PayPal, SSM, GitHub, or Cloudflare resources.
- Automated staging PayPal Sandbox smoke workflow.

## Relevant Files Or Modules

- `apps/api/src/main/kotlin/com/timearchive/application/CapturePayPalOrder.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/model/CheckoutAttempt.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/port/CheckoutAttemptRepository.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/payment/PayPalOrderClient.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/payment/RestClientPayPalOrderClient.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/PayPalPaymentController.kt`
- `apps/web/src/app/payments/paypal/return/page.tsx`
- `apps/web/src/app/payments/paypal/cancel/page.tsx`
- `apps/web/src/app/api/payments/paypal/orders/[orderId]/capture/route.ts`
- `docs/api/openapi.yaml`

## Key Design Decisions

- PayPal redirects to Web pages, not directly to an API mutation endpoint.
- The Web return page calls the API through a same-origin POST with normal
  session and CSRF handling.
- Capture uses a stable provider request ID derived from the checkout attempt so
  retries are idempotent.
- Capture success records the provider capture reference and moves the attempt
  to `CAPTURED_PENDING_WEBHOOK`.
- Capture success does not call `CompletePrimaryPurchase` and does not create
  ownership.
- A repeated capture request for an already captured attempt returns the stored
  capture reference without calling PayPal again.

## Official Reference Notes

PayPal Orders API supports capturing an approved order through the order capture
endpoint. PayPal REST APIs support `PayPal-Request-Id` on POST requests for
idempotency. The implementation uses those provider boundaries but keeps
ownership finalization for the later verified webhook branch.

Source checked on 2026-07-03:

- PayPal Orders API v2 documentation.
- PayPal REST API idempotency documentation.

## Step-By-Step Execution Plan

- [x] Create this implementation plan.
- [x] Add capture columns and status migration.
- [x] Extend checkout attempt model and repository.
- [x] Add PayPal capture client boundary and REST client mapping.
- [x] Add `CapturePayPalOrder` use case.
- [x] Add API and Web routes.
- [x] Add return/cancel pages.
- [x] Update OpenAPI and operational docs.
- [x] Run API/Web tests and static verification.

## Risks And Rollback Strategy

- Risk: Capturing an expired reservation could collect money without ownership.
  - Mitigation: Validate reservation ownership, status, and expiration before
    calling PayPal capture.
- Risk: Repeated browser refreshes could duplicate capture calls.
  - Mitigation: Store capture result and return idempotently after capture.
- Risk: PayPal capture succeeds but local result recording fails.
  - Mitigation: Use a stable `PayPal-Request-Id`; retries should recover the
    latest provider result.

Rollback before real PayPal staging enablement is a normal code rollback. After
staging PayPal is enabled, keep webhook and reconciliation evidence before
changing payment records manually.

## Verification Plan

- Run focused API tests for capture use case and PayPal client mapping.
- Run Web lint/build if pages or routes change.
- Run full API tests with Docker Desktop Linux Engine.
- Run `docker compose --env-file .env.local.example config --quiet`.
- Run `git diff --check`.

## Open Questions

- PayPal Sandbox credentials and staging return/cancel URLs are required before
  staging browser approval and capture verification can run.

## Progress Log

- 2026-07-03: Created the plan after PR #110 was merged and staging smoke
  checks were reported complete.
- 2026-07-03: Added checkout attempt capture state, PayPal capture use case,
  API/Web routes, return/cancel pages, OpenAPI updates, and focused tests.
- 2026-07-03: Verified API tests, Web lint/build, OpenAPI validation, compose
  config, and whitespace checks.

## Completion Summary

PayPal approval return and server-side capture are implemented. Approved orders
can be captured through an authenticated API call, capture state is persisted on
the checkout attempt, and repeated capture calls return the stored capture
reference without another provider call. Capture does not grant ownership.

## Files Changed

- Added checkout attempt capture migration, model fields, repository methods,
  PayPal client capture mapping, capture use case, API controller, Web proxy
  route, and PayPal return/cancel pages.
- Updated purchase UI to redirect PayPal checkouts to the provider approval URL.
- Updated OpenAPI, PayPal operational design, runtime examples, release
  readiness checklist, and local environment example.
- Added focused API tests for capture use case and controller routing.

## Tests Run And Results

- `.\gradlew.bat test --max-workers=2` from `apps/api`: passed.
- `npm.cmd run lint` from `apps/web`: passed.
- `npm.cmd run build` from `apps/web`: passed.
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-openapi.sh`: passed.
- `docker compose --env-file .env.local.example config --quiet`: passed.
- `git diff --check`: passed.

## Manual Verification Results

No live PayPal Sandbox browser approval was run in this branch because staging
PayPal runtime credentials and provider app URLs must be configured before that
flow can be exercised.

## Known Limitations

- Verified PayPal webhook handling is still required before real payment
  ownership can be finalized.
- Staging Sandbox order approval and capture must be tested after SSM runtime
  parameters and PayPal app return/cancel URLs are configured.

## Follow-Up Recommendations

- Configure staging PayPal Sandbox credentials and return/cancel URLs.
- Redeploy staging with PayPal enabled for a short integration window.
- Add a staging Sandbox approval/capture smoke after the flow is manually
  verified.
