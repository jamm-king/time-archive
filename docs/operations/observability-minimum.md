# Observability Minimum

## Purpose

This runbook defines the minimum observability baseline for Time Archive before
PayPal integration and paid production traffic. It covers what CloudWatch,
Sentry, metrics, and alerts must provide at MVP scale.

This document does not create AWS alarms, Sentry projects, metric filters, or
application instrumentation. It defines the release gate those implementations
must satisfy.

## Current Baseline

Already implemented or verified:

- API request correlation through `X-Request-Id`.
- API error responses include `requestId`.
- Safe API request completion logs include request ID, method, path, status,
  duration, and sanitized exception metadata.
- API/Web CloudWatch logs are environment-scoped.
- CloudWatch log retention target is 14 days.
- Staging request ID smoke and CloudWatch request ID search passed.
- Staging sensitive-log keyword checks passed after the generated default
  password logging fix.
- Staging CloudFormation defines basic EC2 and RDS alarms.

Gaps before paid production:

- Sentry SDKs are not integrated.
- Application metrics are not emitted as structured metrics.
- Production alert routing is not verified.
- Payment webhook failure alerts do not exist.
- Storage upload/preview/playback failure alerts do not exist.

## Tooling Decision

Minimum MVP tooling:

- CloudWatch Logs for runtime logs.
- CloudWatch metrics and alarms for infrastructure, container, RDS, deployment,
  and coarse application failure signals.
- Sentry Developer for API and Web exception grouping after SDK integration.
- SNS email notifications for the first production alert route unless a better
  incident channel is selected.

OpenTelemetry is deferred. The application should remain compatible with future
OpenTelemetry instrumentation, but paid MVP launch does not require a tracing
backend.

## Sentry Requirements

When Sentry is added, use:

- separate `staging` and `production` environments;
- immutable release names based on the Git SHA;
- API exception capture for unexpected backend failures;
- Web exception capture for client and server rendering failures;
- source maps for Web if production bundles are uploaded;
- strict filtering before events leave the process.

Sentry events must not include:

- passwords or password hashes;
- session cookies;
- CSRF tokens;
- Authorization headers;
- Cloudflare Tunnel tokens;
- AWS credentials;
- R2 access keys or secret keys;
- database credentials;
- rate-limit key salts;
- payment webhook signatures;
- raw PayPal payloads containing sensitive data;
- presigned upload, preview, or playback URLs;
- raw uploaded media content.

## Minimum Metrics

The first paid production release should be able to observe:

| Surface | Minimum signal |
| --- | --- |
| API availability | health check failure or sustained 5xx responses |
| Web availability | public Web health or smoke failure |
| API latency | request duration trend or high-latency alarm when metrics exist |
| Authentication | repeated login/register failures and rate-limit spikes |
| Purchase flow | checkout creation failures and reservation completion failures |
| PayPal webhook | signature failure, processing failure, duplicate delivery, and idempotency conflict counts |
| Media upload | upload request creation failure and completion failure counts |
| Storage | R2 metadata, PUT, preview, and playback presign failures |
| Database | RDS CPU, free storage, connections, freeable memory, and connectivity |
| Host | EC2 status checks, disk usage, memory usage, and container restarts |
| Deployment | migration failure, health-check failure, and failed deploy command |

CloudWatch metric filters can cover coarse log-derived signals at first.
Dedicated application metrics can be added after PayPal and production traffic
make thresholds meaningful.

## Minimum Alerts

Paid production must alert on:

- EC2 instance or system status check failure.
- Host disk usage above the approved threshold.
- Host memory pressure above the approved threshold.
- RDS CPU sustained above the approved threshold.
- RDS free storage below the approved threshold.
- RDS connection pressure or unavailable database health.
- API health check failure.
- Web health check failure.
- Repeated container restarts.
- Deployment or migration failure.
- Sustained API 5xx responses.
- Repeated `UNEXPECTED_ERROR` API responses.
- Payment webhook signature verification failure after PayPal is added.
- Payment webhook processing failure after PayPal is added.
- Storage upload completion or presigned URL generation failure spikes.

Suggested first routing:

- staging: SNS email to the operator address used for staging alarms;
- production: SNS email to the production operator address, with escalation to
  a stronger channel after traffic grows.

## Alert Noise Policy

Every alert must have:

- owner;
- reason;
- threshold;
- action to take;
- runbook link;
- expected false-positive cases;
- rollback or mitigation path.

Do not create broad alerts that operators will ignore. Prefer a small number of
actionable paid-production alarms over a large noisy set.

## Verification Before PayPal

Before PayPal implementation reaches production, verify:

1. CloudWatch log groups exist and retain logs for 14 days.
2. API request ID search works in staging.
3. Sensitive-log sampling passes after the latest deployment.
4. Deployment and migration failure paths are visible in logs.
5. At least one alert route is confirmed by receiving a test notification.
6. Sentry project/environment/release naming is decided, or Sentry integration
   is explicitly deferred with CloudWatch-only risk acceptance.

## Verification Before Paid Production

Before paid production traffic, verify:

1. Production CloudWatch log groups exist.
2. Production alarm destinations are subscribed and confirmed.
3. Production EC2/RDS/container alarms exist.
4. API/Web health alarms exist.
5. Sentry API/Web error grouping is live or explicitly risk-accepted.
6. Payment webhook failure alerts exist after PayPal integration.
7. Storage failure alerts exist.
8. Operators can trace an incident from user report to request ID, CloudWatch
   logs, deployment SHA, and Sentry issue if available.

## Release Gate

Observability can be marked ready for paid production only after:

- CloudWatch logs and retention are verified in production;
- alert delivery is tested;
- core infrastructure and deployment alarms exist;
- API/Web failure detection exists;
- PayPal webhook failure alerts exist after PayPal integration;
- Sentry is integrated with filtering or explicitly deferred with an accepted
  CloudWatch-only risk record.
