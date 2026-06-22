# Add Production Payment Safety Guard

## Objective

Prevent the local fake payment checkout and completion webhook from being
available unless they are explicitly enabled for local development or CI.

## Scope

- Make fake payment components conditional on an explicit configuration flag.
- Keep the application startable when fake payment is disabled.
- Return a stable service-unavailable API error when checkout is attempted
  without a configured payment provider.
- Enable fake payment explicitly in the local Docker Compose stack.
- Document the configuration and environment boundary.
- Add focused tests for enabled and disabled behavior.

Out of scope:

- Real payment provider integration.
- Provider webhook signature verification.
- Checkout frontend redesign.
- Production deployment configuration.

## Relevant Files Or Modules

- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/PaymentWebhookController.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/payment/FakePaymentAdapter.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/payment/DisabledPaymentAdapter.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `apps/api/src/main/resources/application.yml`
- `apps/api/src/test/kotlin/com/timearchive/configuration/FakePaymentConfigurationTest.kt`
- `docker-compose.yml`
- `docs/api/openapi.yaml`
- `docs/architecture/security-and-operations.md`
- `docs/operations/release-readiness-checklist.md`
- `README.md`

## Key Design Decisions

- Fake payment is disabled by default and requires
  `TIME_ARCHIVE_PAYMENT_FAKE_ENABLED=true`.
- The fake outbound adapter and fake inbound webhook are enabled by the same
  flag so checkout and completion cannot be configured inconsistently.
- A disabled payment adapter keeps application wiring valid and fails checkout
  requests with a stable service-unavailable error until a real provider is
  configured.
- The local Docker Compose stack explicitly enables fake payment. A deployment
  that omits the flag remains safe by default.
- The core payment port remains unchanged; provider selection stays in outbound
  and configuration concerns.

## Step-By-Step Execution Plan

- [x] Confirm latest `main` and create a dedicated feature branch.
- [x] Inspect fake payment controller, adapter, security, configuration, and
  tests.
- [x] Add this implementation plan.
- [x] Add conditional fake payment registration.
- [x] Add disabled payment adapter and stable API error mapping.
- [x] Enable fake payment explicitly for local Docker Compose.
- [x] Update OpenAPI and operations documentation.
- [x] Add focused tests.
- [x] Run relevant backend tests and build checks.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: Existing local startup paths outside Docker Compose may lose fake
  checkout behavior.
  - Mitigation: Document the explicit environment flag and keep Compose local
    defaults working.
- Risk: Conditional beans can leave `PaymentPort` wiring ambiguous.
  - Mitigation: Use mutually exclusive conditions and verify both contexts.
- Risk: Clients may receive an unexpected error when payment is disabled.
  - Mitigation: Return a documented `503 PAYMENT_PROVIDER_UNAVAILABLE` error.

Rollback:

- Remove the conditional annotations and disabled adapter, restore the prior
  application and Compose configuration, and revert documentation updates.

## Verification Plan

- Verify fake payment controller and adapter are absent when the flag is not
  enabled.
- Verify the disabled adapter is present and returns the expected failure.
- Verify fake payment controller and adapter are present when the flag is true.
- Run focused backend tests.
- Run the backend test suite and build if focused tests pass.
- Run `git diff --check`.

## Open Questions

- None for this task. Real provider selection remains a separate design task.

## Progress Log

- 2026-06-22: Confirmed local `main` matches `origin/main`.
- 2026-06-22: Created `feature/production-safety-guards`.
- 2026-06-22: Selected explicit opt-in fake payment configuration instead of a
  profile-only guard so omitted deployment configuration is safe by default.
- 2026-06-22: Added conditional fake payment inbound and outbound adapters plus
  a disabled adapter for application startup without a payment provider.
- 2026-06-22: Added stable `503 PAYMENT_PROVIDER_UNAVAILABLE` checkout behavior.
- 2026-06-22: Enabled fake payment explicitly in the default local Docker
  Compose stack and updated API, security, README, and release readiness docs.
- 2026-06-22: Focused tests, full backend tests, backend build, OpenAPI
  validation, Compose rendering, and diff checks passed.

## Completion Summary

Fake payment is now disabled by default. The fake checkout adapter and fake
payment completion webhook are registered only when
`TIME_ARCHIVE_PAYMENT_FAKE_ENABLED=true`. When no payment provider is enabled,
the application remains startable and checkout returns a stable service
unavailable response. The local Docker Compose stack explicitly opts in so
existing local and CI verification flows retain their behavior.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/PaymentWebhookController.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/payment/FakePaymentAdapter.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/payment/DisabledPaymentAdapter.kt`
- `apps/api/src/main/resources/application.yml`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest/PurchaseControllerTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/configuration/FakePaymentConfigurationTest.kt`
- `docker-compose.yml`
- `docs/api/openapi.yaml`
- `docs/architecture/security-and-operations.md`
- `docs/operations/release-readiness-checklist.md`
- `README.md`
- `docs/implementation-plan/2026-06-22/add-production-payment-safety-guard.md`

## Tests Run And Results

- `./gradlew test --tests "com.timearchive.configuration.FakePaymentConfigurationTest" --tests "com.timearchive.adapter.inbound.rest.PurchaseControllerTest" --max-workers=2`: passed.
- `./gradlew test --max-workers=2`: passed.
- `./gradlew build --max-workers=2`: passed.
- `./scripts/verify-openapi.sh`: passed.
- `docker compose config --quiet`: passed.
- `git diff --check`: passed.

## Manual Verification Results

- No browser verification was required for this backend configuration guard.
- Local end-to-end purchase scripts were not rerun because the focused context
  tests verify both configuration states and Compose rendering confirms the
  local opt-in value. CI will continue exercising the Compose-backed local
  purchase flows.

## Known Limitations

- Checkout cannot succeed when fake payment is disabled until a real payment
  provider adapter is configured.
- The frontend still contains the local fake payment completion UI. It cannot
  complete payment when the backend guard is disabled, but production frontend
  behavior will be replaced during real provider integration.

## Follow-Up Recommendations

- Add rate limiting as the next release-readiness code blocker.
- Keep `TIME_ARCHIVE_PAYMENT_FAKE_ENABLED` unset or false in every deployed
  environment.
- Replace the disabled adapter with a real provider adapter during payment
  provider integration.
