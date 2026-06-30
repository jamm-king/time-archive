# Add Request Completion Logging

## Objective

Emit a safe API request completion log line for each HTTP request so staging
CloudWatch Logs can be searched by `X-Request-Id` after the request ID smoke
workflow succeeds.

## Scope

- Add request completion logging to the existing request correlation filter.
- Include only safe operational fields: request ID, method, path, status, and
  duration.
- Avoid logging query strings, request bodies, response bodies, cookies,
  authorization headers, CSRF tokens, credentials, or presigned URLs.
- Update logging and CloudWatch operations documentation.

Out of scope:

- JSON structured logging.
- CloudWatch metric filters and alarms.
- Web access logging.
- Client IP logging before trusted Cloudflare client IP handling is reviewed.

## Relevant Files Or Modules

- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/web/RequestCorrelationFilter.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/web/RequestCorrelationFilterTest.kt`
- `docs/operations/logging-policy.md`
- `docs/operations/cloudwatch-log-operations.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- Reuse `RequestCorrelationFilter` because it already owns request ID generation,
  response header propagation, request attributes, and MDC lifecycle.
- Log the request URI path without query string to avoid leaking tokens or
  presigned URL parameters.
- Log normal completion at `INFO`.
- If the downstream chain throws, log sanitized exception class metadata without
  raw exception messages or stack traces, then rethrow.

## Step-by-step Execution Plan

1. Create this implementation plan.
2. Add completion logging around the existing filter chain execution.
3. Add focused tests for successful completion and thrown downstream errors.
4. Update operations documentation and release readiness notes.
5. Run focused API tests and relevant static checks.

## Risks And Rollback Strategy

- Risk: logging unsafe request data. Mitigation: log only method, URI path,
  status, duration, request ID, and exception class.
- Risk: duplicate request logs if another access logger is added later.
  Mitigation: document this filter as the current API request completion log
  source.
- Rollback: remove the completion log lines and documentation updates.

## Verification Plan

- Run focused `RequestCorrelationFilterTest`.
- Run backend tests if practical.
- Run CloudWatch operations static validator.
- Run `git diff --check`.
- After merge and staging deployment, rerun `Smoke staging request ID` and
  search `/time-archive/staging/api` for the request ID.

## Open Questions

- None.

## Progress

- Created `feature/request-completion-logging` from latest `main`.
- Added safe API request completion logging to `RequestCorrelationFilter`.
- Added focused tests for completion logs, query-string exclusion, and
  sanitized exception metadata.
- Updated logging policy, CloudWatch operations, and release readiness
  documentation.

## Completion Summary

API requests now emit one safe completion log line from the request correlation
filter. The log line includes `requestId`, `method`, `path`, `status`, and
`durationMs`; downstream exceptions add only sanitized exception class metadata.
Query strings, bodies, cookies, authorization headers, CSRF tokens, credentials,
and presigned URLs are not logged.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/web/RequestCorrelationFilter.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/web/RequestCorrelationFilterTest.kt`
- `docs/implementation-plan/2026-06-30/add-request-completion-logging.md`
- `docs/operations/cloudwatch-log-operations.md`
- `docs/operations/logging-policy.md`
- `docs/operations/release-readiness-checklist.md`

## Tests Run And Results

- `apps/api/gradlew.bat test --tests com.timearchive.adapter.inbound.web.RequestCorrelationFilterTest --max-workers=2`
  passed.
- `apps/api/gradlew.bat test --max-workers=2` passed.
- `PYTHONPATH=temp/cfn-lint ./scripts/verify-cloudwatch-log-operations.sh`
  passed.
- `git diff --check` passed.

## Manual Verification Results

- Live staging CloudWatch request ID search was not rerun because this branch
  must be merged, published, and deployed before the new completion log exists
  in staging.

## Known Limitations

- Logs are plain text rather than JSON structured logs.
- Web request completion logging is not added in this task.
- Client IP logging is intentionally deferred until trusted Cloudflare client
  IP handling is reviewed.

## Follow-up Recommendations

- After merge and staging deployment, rerun `Smoke staging request ID` and
  search `/time-archive/staging/api` for the smoke request ID.
- Consider JSON structured logging only if CloudWatch field extraction or
  metrics require it.
