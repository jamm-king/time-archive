# Add Request Correlation And Logging Policy

## Objective

Introduce a minimal request correlation foundation for the API and document the
logging policy needed for staging and production operations.

## Scope

- Add API request correlation ID handling.
- Return the correlation ID in response headers.
- Include the correlation ID in API error responses.
- Put the correlation ID into the logging MDC for request-scoped logs.
- Document logging fields, retention expectations, and sensitive values that
  must not be logged.
- Update API documentation when the error response contract changes.

Out of scope:

- Sentry integration.
- CloudWatch metric filters and alarms.
- Distributed tracing.
- Web frontend error tracking.

## Relevant Files Or Modules

- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiErrorResponse.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/web/RequestCorrelationFilter.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/SecurityConfiguration.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/web/RequestCorrelationFilterTest.kt`
- `docs/operations/logging-policy.md`
- `docs/api/openapi.yaml`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- Use `X-Request-Id` as the public correlation header.
- Accept caller-provided request IDs only when they match a conservative
  printable token shape; otherwise generate a UUID.
- Add the generated or accepted request ID to the response header for every API
  request that reaches the filter.
- Add the request ID to `MDC` under `requestId` and clear it after the request.
- Add `requestId` to structured API error responses as a backward-compatible
  optional field.

## Step-by-step Execution Plan

1. Create this implementation plan.
2. Add the request correlation filter and register it before authentication and
   rate limiting.
3. Include request IDs in normal Spring MVC error responses and security
   authentication/CSRF error responses.
4. Add focused tests for request ID generation, propagation, validation, MDC
   cleanup, and error response enrichment.
5. Update OpenAPI and operations documentation.
6. Run relevant backend tests and diff checks.

## Risks And Rollback Strategy

- Risk: changing the error response schema affects clients. Mitigation: add only
  an optional field and keep existing fields unchanged.
- Risk: logging untrusted request IDs. Mitigation: accept only constrained token
  characters and length; otherwise generate a UUID.
- Risk: MDC leakage across requests. Mitigation: always clear MDC in a `finally`
  block and cover this with tests.
- Rollback: remove the filter, remove `requestId` from error responses, and
  revert documentation updates.

## Verification Plan

- Run focused backend tests for the request correlation filter and representative
  REST error responses.
- Run `git diff --check`.

## Open Questions

- None.

## Progress

- Created `feature/request-correlation-logging-policy` from latest `main`.
- Added request correlation filter, response header propagation, MDC handling,
  and optional `requestId` on API error responses.
- Added focused request correlation tests and a representative REST error
  response assertion.
- Updated OpenAPI error response schema.
- Added the logging policy and updated release readiness notes.

## Completion Summary

Implemented API request correlation with `X-Request-Id`, MDC propagation, and
request ID enrichment for Spring MVC, Spring Security, and rate-limit error
responses. Added the first logging policy document and updated release
readiness to reflect the new correlation baseline.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/web/RequestCorrelationFilter.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiErrorResponse.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/security/ApiRateLimitingFilter.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/SecurityConfiguration.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/web/RequestCorrelationFilterTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest/PurchaseControllerTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/security/ApiRateLimitingFilterTest.kt`
- `docs/api/openapi.yaml`
- `docs/operations/logging-policy.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-06-30/add-request-correlation-logging-policy.md`

## Tests Run And Results

- `.\gradlew.bat test --tests "com.timearchive.adapter.inbound.web.RequestCorrelationFilterTest" --tests "com.timearchive.adapter.inbound.rest.PurchaseControllerTest" --tests "com.timearchive.adapter.inbound.security.ApiRateLimitingFilterTest" --max-workers=2`: passed.
- `.\gradlew.bat test --max-workers=2`: passed.
- `PYTHONPATH=D:\develop\time-archive\temp\cfn-lint python -c "import yaml; yaml.safe_load(open('docs/api/openapi.yaml', encoding='utf-8'))"`: passed.
- `git diff --check`: passed.

## Manual Verification Results

- Redocly OpenAPI validation through `scripts/verify-openapi.sh` was attempted
  but could not run because the local Docker Desktop Linux engine was not
  running.

## Known Limitations

- Request duration access logs are not added yet.
- CloudWatch log group retention and request ID search must still be verified
  in staging.
- Error tracking and alerting remain separate follow-up work.

## Follow-up Recommendations

- Add structured request completion logs with duration and status.
- Verify `X-Request-Id` searchability in staging CloudWatch logs.
- Add CloudWatch metric filters or alerting after log fields are confirmed.
