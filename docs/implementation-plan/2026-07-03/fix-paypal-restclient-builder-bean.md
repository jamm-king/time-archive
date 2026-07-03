# Fix PayPal RestClient Builder Bean

## Objective

Fix the staging deployment failure that occurs when PayPal is enabled. The API
fails to start because `RestClientPayPalOrderClient` requires a
`RestClient.Builder` bean that is not currently registered.

## Scope

- Add a small infrastructure configuration that exposes `RestClient.Builder`.
- Add a focused context test for the real PayPal order client bean when PayPal
  is enabled.
- Run relevant API tests.

## Relevant Files Or Modules

- `apps/api/src/main/kotlin/com/timearchive/configuration/HttpClientConfiguration.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/payment/RestClientPayPalOrderClient.kt`
- `apps/api/src/test/kotlin/com/timearchive/configuration/HttpClientConfigurationTest.kt`

## Key Design Decisions

- Keep the fix in configuration, not in the PayPal adapter, so outbound HTTP
  clients can share the same framework boundary later.
- Do not add a dependency because Spring Web already provides `RestClient`.
- Do not change PayPal runtime values or payment behavior in code.

## Step-By-Step Execution Plan

- [x] Inspect the staging SSM command failure output.
- [x] Add the `RestClient.Builder` bean.
- [x] Add a PayPal-enabled context test that creates the real client.
- [x] Run focused API tests.
- [x] Document completion results.

## Risks And Rollback Strategy

- Risk: The new bean could conflict with future custom RestClient settings.
  - Mitigation: Register only the default builder with no side effects.
- Risk: Context test could accidentally call PayPal.
  - Mitigation: Only assert bean creation; do not invoke network methods.

Rollback is a normal code revert. No database or runtime state changes are
required.

## Verification Plan

- Run the new configuration test.
- Run payment configuration tests.
- Run full API tests if the focused tests pass quickly.

## Open Questions

None.

## Progress Log

- 2026-07-03: Identified the staging failure root cause from SSM command
  `5605d805-f50e-4f16-83de-81a78da1601a`: missing `RestClient.Builder` bean.
- 2026-07-03: Added `HttpClientConfiguration` and a context test that creates
  the real PayPal REST client when PayPal is enabled.
- 2026-07-03: Focused configuration tests and the full API test suite passed.

## Completion Summary

The PayPal-enabled API startup failure is fixed by registering a
`RestClient.Builder` bean. A context test now covers real PayPal REST client
creation with PayPal enabled, which matches the staging runtime path.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/configuration/HttpClientConfiguration.kt`
- `apps/api/src/test/kotlin/com/timearchive/configuration/HttpClientConfigurationTest.kt`
- `docs/implementation-plan/2026-07-03/fix-paypal-restclient-builder-bean.md`

## Tests Run And Results

- `.\gradlew.bat test --tests "com.timearchive.configuration.HttpClientConfigurationTest" --tests "com.timearchive.configuration.FakePaymentConfigurationTest" --max-workers=2`: passed.
- `.\gradlew.bat test --max-workers=2`: passed.

## Manual Verification Results

The failed staging SSM command output was inspected directly. The root cause was
the missing `RestClient.Builder` bean during PayPal-enabled application startup.

## Known Limitations

No staging redeploy has been rerun from this fix branch yet.

## Follow-Up Recommendations

- Commit and push this fix branch.
- Merge it to `main`.
- Publish staging images and deploy staging again.
