# Add PayPal Checkout Foundation

## Objective

Add the first PayPal checkout foundation while preserving the existing payment
and ownership transaction boundary. The implementation should persist checkout
attempts, make checkout creation idempotent per reservation, and allow a PayPal
adapter to create provider orders without holding a long reservation database
lock.

## Scope

- Add a persisted checkout attempt model and repository.
- Add a Flyway migration for checkout attempts.
- Refactor `CreateCheckout` so provider network calls happen outside the
  reservation validation transaction.
- Add a PayPal outbound adapter for order creation behind explicit
  configuration.
- Keep fake payment available for local and CI flows.
- Keep disabled payment as the default when neither fake nor PayPal is enabled.
- Add focused tests for checkout idempotency, transaction splitting, adapter
  mapping, and configuration selection.
- Update relevant documentation and release readiness notes.

## Out Of Scope

- PayPal approval return endpoint.
- PayPal order capture endpoint.
- PayPal verified webhook endpoint.
- Ownership finalization from PayPal events.
- Provisioning PayPal credentials, SSM parameters, or GitHub secrets.
- Running PayPal Sandbox or live provider tests.

## Relevant Files Or Modules

- `apps/api/src/main/kotlin/com/timearchive/application/CreateCheckout.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/model/CheckoutRequest.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/model/CheckoutAttempt.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/port/CheckoutAttemptRepository.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/port/PaymentPort.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcCheckoutAttemptRepository.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/payment/PayPalPaymentAdapter.kt`
- `apps/api/src/main/resources/db/migration/V10__create_checkout_attempts.sql`
- `apps/api/src/main/resources/application.yml`
- `docs/operations/paypal-integration-design.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- `CreateCheckout` uses two short database transactions around one provider
  call:
  1. validate reservation, create or reuse checkout attempt, and mark the
     reservation `CHECKOUT_CREATED`;
  2. call the provider outside the database transaction;
  3. record provider reference and checkout URL, or mark the attempt failed.
- A reservation has one checkout attempt in the first implementation. Retries
  reuse the same provider request key.
- The PayPal adapter uses OAuth2 client credentials and PayPal Orders API
  create-order semantics.
- `PayPal-Request-Id` is derived from the persisted checkout attempt request key
  so provider retries stay idempotent.
- PayPal is enabled only when `time-archive.payment.paypal.enabled=true`.
- Fake payment remains controlled by `time-archive.payment.fake.enabled=true`.
- Disabled payment remains the default fallback.

## Official Reference Notes

PayPal REST APIs authenticate with OAuth2 access tokens and JSON responses.
PayPal's idempotency guidance says REST `POST` calls can use
`PayPal-Request-Id`; repeated calls with the same header return the latest
status instead of duplicating the operation while PayPal stores the ID.

Sources checked on 2026-07-03:

- PayPal REST API getting started documentation.
- PayPal REST API idempotency documentation.
- PayPal REST API response and error behavior documentation.

## Step-By-Step Execution Plan

- [x] Read current payment design, release checklist, and checkout code.
- [x] Create this implementation plan.
- [x] Add checkout attempt domain model and repository port.
- [x] Add Flyway migration and JDBC repository.
- [x] Refactor `CreateCheckout` around persisted checkout attempts.
- [x] Add PayPal properties and conditional adapter.
- [x] Update fake and disabled payment adapter configuration.
- [x] Add or update tests.
- [x] Update operational docs and OpenAPI notes if response behavior changes.
- [x] Run relevant backend tests and `git diff --check`.

## Risks And Rollback Strategy

- Risk: Checkout behavior can regress for existing local fake payment scripts.
  - Mitigation: Keep fake provider semantics and run local purchase-related
    tests.
- Risk: PayPal order payload details can differ by current PayPal API behavior.
  - Mitigation: Keep mapping tests explicit and perform Sandbox verification in
    a follow-up branch before launch.
- Risk: A provider call succeeds but recording the checkout attempt fails.
  - Mitigation: Reuse the same provider request key on retry and update the
    existing attempt from PayPal's idempotent response.
- Risk: A provider call fails after the reservation was marked
  `CHECKOUT_CREATED`.
  - Mitigation: Mark the attempt failed and allow retry against the same
    checkout attempt while the reservation remains unexpired.

Rollback is a code rollback before PayPal is enabled. Existing fake local flows
must remain available.

## Verification Plan

- Run focused API tests for checkout and payment configuration.
- Run adapter mapping tests without real PayPal credentials.
- Run `git diff --check`.
- If feasible, run the full API test suite.

## Open Questions

- The PayPal adapter will be implemented against the REST create-order flow.
  Sandbox verification is required before marking checkout redirect flow ready.

## Progress Log

- 2026-07-03: Created the plan after confirming `main` includes the PayPal
  design PR and the working tree is clean.
- 2026-07-03: Added checkout attempts, refactored checkout creation around
  short transactions, added a PayPal order creation adapter, and kept PayPal
  disabled by default in deployed runtime configuration.
- 2026-07-03: Verified focused API tests and deployment rendering scripts. Full
  API tests could not complete locally because Docker Desktop Linux Engine was
  not running.
- 2026-07-03: After Docker Desktop Linux Engine was started, the full API test
  suite passed. Added local Compose and `.env.local.example` PayPal variables so
  local containers can receive PayPal checkout configuration when explicitly
  enabled.

## Completion Summary

The PayPal checkout foundation is implemented. Checkout creation now persists a
checkout attempt before calling the provider, reuses provider request IDs for
retries, and records provider-created or provider-failed attempt state after the
external call. PayPal is available only when explicitly enabled by runtime
configuration.

## Files Changed

- Added checkout attempt domain model, repository port, JDBC adapter, and
  Flyway migration.
- Refactored `CreateCheckout` to avoid provider calls inside long reservation
  transactions.
- Added PayPal checkout properties, order client boundary, REST client
  implementation, and payment adapter.
- Updated fake and disabled payment adapter configuration.
- Added checkout, PayPal adapter, payment configuration, and checkout attempt
  repository tests.
- Updated deployment runtime rendering and Compose to support optional PayPal
  configuration while keeping PayPal disabled by default.
- Updated local Compose and `.env.local.example` to support optional PayPal
  configuration while keeping fake payment enabled by default for local flows.
- Updated domain, PayPal, runtime parameter, and release readiness documents.

## Tests Run And Results

- `./gradlew.bat test --tests "com.timearchive.application.CreateCheckoutTest" --tests "com.timearchive.adapter.outbound.payment.PayPalPaymentAdapterTest" --tests "com.timearchive.configuration.FakePaymentConfigurationTest" --max-workers=2`: passed.
- `C:\Program Files\Git\bin\bash.exe scripts/verify-staging-deployment-runtime.sh`: passed.
- `C:\Program Files\Git\bin\bash.exe scripts/verify-staging-runtime-parameters.sh`: passed.
- `C:\Program Files\Git\bin\bash.exe scripts/verify-production-deployment.sh`: passed.
- `docker compose --env-file .env.local.example config --quiet`: passed.
- `./gradlew.bat test --max-workers=2`: passed after Docker Desktop Linux
  Engine was started.
- `git diff --check`: passed.

## Manual Verification Results

- Confirmed PayPal remains disabled by default.
- Confirmed staging and production SSM fixtures do not require PayPal secrets
  before the PayPal environment is intentionally provisioned.
- Confirmed runtime rendering writes optional PayPal variables without printing
  values.

## Known Limitations

- PayPal approval return, capture, and verified webhook processing are not
  implemented in this branch.
- PayPal Sandbox order creation has not been run because no real PayPal
  credentials were provisioned by this change.
- Full API tests require Docker Desktop Linux Engine because repository
  integration tests use Testcontainers.

## Follow-Up Recommendations

- Add PayPal approval return and server-side capture flow.
- Add verified PayPal webhook processing and complete ownership finalization.
- Add a staging PayPal Sandbox smoke workflow after Sandbox credentials and
  webhook configuration are provisioned.
