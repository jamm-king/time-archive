# CloudWatch Log Operations

## Purpose

This runbook defines the CloudWatch Logs baseline for Time Archive staging and
the MVP production deployment model. It covers log groups, retention, request ID
search, and sensitive-log checks.

## Managed Log Groups

Staging CloudFormation manages these log groups with 14 days of retention:

| Source | Log group |
| --- | --- |
| API container | `/time-archive/staging/api` |
| Web container | `/time-archive/staging/web` |
| Redis container | `/time-archive/staging/redis` |
| Cloudflare Tunnel container | `/time-archive/staging/cloudflared` |
| Migration container | `/time-archive/staging/migration` |
| RDS PostgreSQL | `/aws/rds/instance/time-archive-staging-postgres/postgresql` |

The production MVP target is also 14 days unless a separate cost and privacy
review approves a different value.

Docker Compose services must use the CloudWatch `awslogs` driver and must write
to the environment-scoped log group prefix configured by
`TIME_ARCHIVE_CLOUDWATCH_LOG_GROUP_PREFIX`.

## Retention Verification

From a local shell with AWS access:

```bash
aws logs describe-log-groups \
  --log-group-name-prefix /time-archive/staging \
  --query 'logGroups[].{name:logGroupName,retention:retentionInDays}' \
  --output table
```

Confirm every `/time-archive/staging/*` log group has:

```text
retention-in-days = 14
```

For the RDS PostgreSQL log group:

```bash
aws logs describe-log-groups \
  --log-group-name-prefix /aws/rds/instance/time-archive-staging-postgres/postgresql \
  --query 'logGroups[].{name:logGroupName,retention:retentionInDays}' \
  --output table
```

## Request ID Search

API responses include `X-Request-Id`, and API request completion logs include
the same request ID. To trace a request:

1. Capture the `X-Request-Id` response header from the browser, curl, or smoke
   workflow logs.
2. Search the API log group:

```bash
aws logs filter-log-events \
  --log-group-name /time-archive/staging/api \
  --filter-pattern '"<request-id>"' \
  --max-items 50
```

3. Use the same request ID when comparing Web, API, and related operational
   logs.

The expected API log event is a safe request completion line containing
`api request completed`, `requestId`, `method`, `path`, `status`, and
`durationMs`. It must not include query strings, request bodies, cookies,
authorization headers, CSRF tokens, credentials, or presigned URLs.

## Staging Verification Record

On 2026-07-01, after PR #87 was merged and deployed to staging, the manual
`Smoke staging request ID` workflow passed. The smoke request ID was then found
in the `/time-archive/staging/api` CloudWatch log group through the safe API
request completion log line.

This verifies the current staging path:

- Cloudflare Tunnel and Web proxy preserve `X-Request-Id`.
- API responses return the effective `X-Request-Id`.
- API error responses include the same `requestId`.
- API request completion logs are searchable by request ID in CloudWatch.

## Sensitive Log Checks

During staging release verification, sample API and Web logs and confirm they do
not contain:

- passwords or password hashes;
- session cookies;
- CSRF tokens;
- Authorization headers;
- Cloudflare Tunnel tokens;
- AWS credentials;
- R2 access keys or secret keys;
- database credentials;
- rate-limit key salts;
- payment webhook signatures or raw provider payloads;
- presigned upload, preview, or playback URLs;
- raw uploaded media content.

Useful exploratory checks:

```bash
aws logs filter-log-events \
  --log-group-name /time-archive/staging/api \
  --filter-pattern '?password ?cookie ?csrf ?authorization ?presigned ?X-Amz-Signature' \
  --max-items 50
```

Treat matches as review candidates, not automatic incidents. Some words may
appear in sanitized policy messages or documentation-like startup output.

### Staging Sensitive Logging Verification Record

On 2026-07-01, API and Web CloudWatch logs were sampled for the reviewed
sensitive keywords. The Web log group did not return matches for the reviewed
keywords. The API log group returned Spring Boot generated default password
startup messages, which are not acceptable for staging or production logs.

The application now declares an empty `UserDetailsService` because Time Archive
uses session-derived authentication and does not need Spring Boot's generated
default user. After this fix is merged and deployed, rerun the API/Web sensitive
keyword checks before marking sensitive logging ready.

The reviewed `X-Amz-Signature` API candidates were false positives from JVM
option text, not presigned URLs.

After PR #89 was merged and redeployed to staging on 2026-07-01, API and Web
CloudWatch logs generated after the deployment were rechecked. Keyword-count
checks for `password`, `cookie`, `csrf`, `authorization`, `presigned`,
`X-Amz-Signature`, `AWS_ACCESS_KEY_ID`, and `AWS_SECRET_ACCESS_KEY` returned no
confirmed sensitive-log matches in the API or Web log groups. A stricter Logs
Insights regex search across the same API and Web log groups also returned zero
matches.

## CI Guard

The static validator:

```bash
./scripts/verify-cloudwatch-log-operations.sh
```

checks that:

- staging CloudFormation defines the reviewed log groups;
- every reviewed staging log group uses 14 days of retention;
- production Docker Compose services use the `awslogs` driver;
- Compose does not create unmanaged log groups at runtime;
- this runbook and the logging policy contain the required operational checks.

The validator does not contact AWS. Live retention and request ID search must be
verified manually in staging.
