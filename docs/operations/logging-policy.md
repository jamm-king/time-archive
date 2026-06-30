# Logging Policy

## Purpose

This policy defines the minimum logging and request correlation rules for Time
Archive API and Web operations. The goal is to make incidents diagnosable
without exposing credentials, private user data, payment secrets, or presigned
storage URLs.

## Request Correlation

The API uses `X-Request-Id` as the request correlation header.

Rules:

- If a request includes a valid `X-Request-Id`, the API reuses it.
- If the header is missing or invalid, the API generates a new UUID request ID.
- The API returns the effective request ID in the `X-Request-Id` response
  header.
- API error responses include the same value in `requestId`.
- Request-scoped API logs include the request ID in the logging MDC as
  `requestId`.
- The API emits one safe request completion log line after each request.
- The request ID must be cleared after the request completes.

Accepted request IDs are limited to 8-128 characters using only letters,
digits, `.`, `_`, and `-`. This keeps untrusted header values safe to include in
logs.

## API Log Fields

Application logs should prefer structured fields where the log sink supports
them. At minimum, production logs should make the following values searchable:

- `timestamp`
- `level`
- `logger`
- `message`
- `requestId`
- `environment`
- `service`
- `method`
- `path`
- `status`
- `durationMs`
- `userId`, only when authenticated and operationally necessary
- `errorCode`, when an API error response is returned

Use stable identifiers instead of raw request bodies. Prefer domain IDs such as
`reservationId`, `ownershipRecordId`, `mediaAssetId`, and `uploadRequestId` only
when they are needed for debugging.

The default API request completion log line must include only:

- `requestId`
- `method`
- `path`, without query string
- `status`
- `durationMs`
- `exception`, only as sanitized exception class metadata when the request
  fails before normal completion

## Values That Must Not Be Logged

Never log:

- Passwords or password hashes.
- Session cookies.
- CSRF tokens.
- Authorization headers.
- Cloudflare Tunnel tokens.
- AWS credentials.
- R2 access keys or secret keys.
- Database usernames or passwords.
- Rate-limit key salts.
- Payment webhook signatures or raw provider payloads.
- Presigned upload, preview, or playback URLs.
- Raw uploaded media content.
- Full request or response bodies from authentication, payment, media upload,
  or admin moderation endpoints.

When handling unexpected exceptions, log the request ID and sanitized exception
metadata. Do not return or log raw exception messages when they may contain
credentials or provider payloads.

## Retention

Default retention target:

- Staging: 14 days.
- Production MVP: 14 days.

Longer retention requires an explicit cost and privacy review. Audit logs stored
in the application database are governed separately from application runtime
logs.

## CloudWatch Baseline

For the current EC2 deployment model, API, Web, Redis, `cloudflared`, and
migration logs should be centralized in CloudWatch Logs. Log group names should
remain environment-scoped under the configured `/time-archive/<environment>`
prefix.

Before public launch, confirm:

- API logs contain `requestId`.
- API request completion logs are searchable by `requestId` for successful and
  failed requests.
- Web proxy or frontend logs can be correlated to API responses through
  `X-Request-Id` where practical.
- The manual `Smoke staging request ID` workflow passes after each staging
  deployment that changes request correlation or Web proxy behavior.
- Log groups have the approved retention period.
- Operators can search by request ID during an incident.
- Logs do not contain the forbidden values listed above.

See [CloudWatch Log Operations](cloudwatch-log-operations.md) for the current
staging log groups, retention checks, and request ID search procedure.

## Follow-up Work

- Replace plain request completion logs with JSON structured logs if CloudWatch
  field extraction becomes necessary.
- Add CloudWatch metric filters or alarms for repeated `UNEXPECTED_ERROR`,
  rate-limit storage failures, storage failures, and payment failures.
- Add error tracking only after the event fields and sensitive-data scrubbing
  policy are reviewed.
