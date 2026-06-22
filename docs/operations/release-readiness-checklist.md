# Release Readiness Checklist

This checklist is the release gate for the Time Archive MVP. It focuses on the
work required before exposing the application outside local development.

Status legend:

- `Ready`: acceptable for the current MVP release gate.
- `Needs verification`: implemented or designed, but must be verified in the
  target environment.
- `Blocked`: not acceptable for a public or paid production release.
- `Deferred`: intentionally outside the first MVP release, but documented.

## Release Decision Summary

Time Archive is close to a local MVP, but it is not production-ready until the
blockers below are resolved.

Production blockers:

- Real payment provider integration and signature-verified provider webhooks.
- Production object storage configuration, preferably Cloudflare R2, using
  managed secrets and private buckets.
- Production database backups, restore testing, and migration procedure.
- Production secret management and removal of local default credentials.
- Basic abuse controls for public reads, authentication, purchase, and upload
  surfaces.
- Production observability for application logs, errors, health, and security
  events.

MVP-ready areas after target-environment verification:

- Canonical single 24-hour archive model.
- Session-derived user identity for purchase and owned media flows.
- CSRF protection for browser-authenticated mutation APIs.
- Owned media upload through short-lived presigned PUT URLs.
- Upload completion verification for object existence, content length, content
  type, ownership, and expiration.
- Admin moderation with role-based authorization and audit logging.
- Admin original preview through short-lived presigned GET URLs.
- Public timeline delivery through approved-media presigned playback URLs.
- OpenAPI validation in CI.

## Security

| Area | Status | Release Gate |
| --- | --- | --- |
| Session authentication | Needs verification | Verify session cookie attributes in the deployed HTTPS environment. |
| CSRF protection | Needs verification | Confirm every browser mutation requires `X-XSRF-TOKEN`; fake provider callbacks must not be browser-facing. |
| Admin authorization | Needs verification | Confirm every admin route derives identity from the server-side session and requires `ADMIN`. |
| Admin bootstrap | Blocked for production | Replace local `TIME_ARCHIVE_INITIAL_ADMIN_EMAILS` bootstrap with an operator-controlled provisioning process or tightly controlled one-time bootstrap. |
| Password policy | Needs verification | Confirm minimum length and hashing are acceptable for MVP; add reset flow later. |
| Rate limiting | Needs verification | Redis-backed application limits cover auth, public reads, purchase, media mutation, and admin routes; verify deployed client identity and add Cloudflare edge limits. |
| Sensitive logging | Needs verification | Confirm logs never include passwords, session cookies, CSRF tokens, storage credentials, presigned URLs, or payment payload secrets. |
| Security headers | Needs verification | Confirm HTTPS, HSTS, secure cookies, frame policy, content type sniffing protection, and conservative referrer policy at the edge or app layer. |

## Payment

| Area | Status | Release Gate |
| --- | --- | --- |
| Local fake payment flow | Ready | Keep only for local and CI verification. |
| Fake webhook endpoint | Ready | Disabled by default and registered only when `TIME_ARCHIVE_PAYMENT_FAKE_ENABLED=true`; never enable it in production. |
| Provider webhook verification | Blocked for production | Add a real provider webhook with signature verification, replay protection, idempotency, and auditability. |
| Checkout redirect flow | Blocked for production | Replace fake checkout with real provider checkout before collecting money. |
| Payment idempotency | Needs verification | Re-run duplicate webhook and retry scenarios against the real provider integration. |

## Storage And Media

| Area | Status | Release Gate |
| --- | --- | --- |
| Local MinIO flow | Ready | Verified by local upload, public timeline, and admin preview scripts. |
| Cloudflare R2 | Blocked for production | Configure endpoint, bucket, access keys, region compatibility, CORS, and private bucket policy. |
| Presigned upload URLs | Needs verification | Confirm TTL, content type, content length, and CORS behavior from the deployed web origin. |
| Upload completion verification | Ready | Existing checks cover object existence, expected content length, expected content type, ownership, and expiration. |
| File signature validation | Blocked for production | Add signature sniffing before trusting content type. |
| Malware scanning | Blocked for production | Add scanning or a documented media safety process before public launch. |
| Transcoding and thumbnail generation | Deferred | MVP can use original approved objects, but production should generate safe derived media. |
| Approved storage references | Ready | Approval rejects URLs that do not belong to the configured storage base URL. |
| Public playback URLs | Ready | Public timeline returns short-lived presigned GET URLs and `Cache-Control: no-store`. |
| Storage backend changes | Blocked for production | Do not change bucket, storage backend, or storage base URL without an object migration, database update, verification, and rollback plan. |

## Database And Data Integrity

| Area | Status | Release Gate |
| --- | --- | --- |
| Canonical timeline constraint | Ready | Keep one fixed 86,400-second archive. |
| Ownership transaction boundaries | Needs verification | Re-run reservation, checkout, webhook, and duplicate event flows after deployment. |
| Migration execution | Needs verification | Confirm Flyway migrations run in staging before production. |
| Backups | Blocked for production | Enable automated PostgreSQL backups and point-in-time recovery. |
| Restore test | Blocked for production | Perform at least one restore drill before public launch. |
| Data retention policy | Needs verification | Define retention for audit logs, upload requests, sessions, and rejected media. |

## CI And Verification

Required checks before merging a release candidate:

- Backend tests.
- Backend build.
- Backend Docker image build.
- Web lint.
- Web build.
- OpenAPI validation.
- Local purchase flow.
- Local media upload flow.
- Local public timeline flow.
- Local admin preview flow.
- Local auth flows.
- Local web purchase and upload flows.
- Local web smoke check.

Release candidate verification:

- Start from a clean Docker Compose state.
- Run all local shell verification scripts.
- Manually verify registration, login, logout, purchase, owned ranges, upload,
  admin moderation, original preview, and public playback.
- Confirm all GitHub Actions checks are green on the release PR.

## Deployment And Configuration

| Area | Status | Release Gate |
| --- | --- | --- |
| Docker images | Needs verification | Build immutable images for API and web. |
| Environment variables | Needs verification | Local secrets are externalized into ignored env files; verify production injection through deployment secret management. |
| Committed secret defaults | Ready | Compose and Spring no longer provide committed database, object storage, or rate-limit secret fallbacks. |
| HTTPS | Blocked for production | Terminate HTTPS at Cloudflare or the deployment platform. |
| Cloudflare | Needs verification | Configure DNS, TLS, caching bypass for API responses with presigned URLs, and basic security rules. |
| Application health checks | Needs verification | Confirm `/actuator/health` and web smoke checks are wired to deployment health probes. |
| Rollback | Needs verification | Define image rollback and database migration rollback policy. |

## Observability And Operations

| Area | Status | Release Gate |
| --- | --- | --- |
| Application logs | Needs verification | Centralize API and web logs with request correlation where possible. |
| Error tracking | Blocked for production | Add an error tracking or alerting path for API and web failures. |
| Metrics | Needs verification | Track request rate, error rate, latency, DB health, storage errors, and payment webhook failures. |
| Audit logs | Ready | Admin approval, rejection, and hiding append audit records in the moderation transaction. |
| Alerts | Blocked for production | Add alerts for health check failure, payment failures, upload failures, DB saturation, and storage errors. |

## Known MVP Limitations

- No real payment provider is integrated.
- No password reset flow exists.
- No email verification exists.
- No media scanning or transcoding exists.
- No user-facing support or dispute workflow exists.
- No resale or secondary market exists.
- No admin invitation or role management UI exists.
- No production R2 environment is configured yet.
- No explicit rate limiting is implemented yet.

## R2 Readiness Checklist

Before connecting Cloudflare R2:

- Create separate private buckets for local verification and production media
  objects.
- Create least-privilege access keys per environment and per bucket.
- Configure S3-compatible endpoint and region-compatible settings.
- Set `TIME_ARCHIVE_STORAGE_S3_ENDPOINT`.
  Example endpoint:
  `https://replace-with-account-id.r2.cloudflarestorage.com`.
- Set `TIME_ARCHIVE_STORAGE_S3_PRESIGNED_URL_ENDPOINT`.
  Example endpoint:
  `https://replace-with-account-id.r2.cloudflarestorage.com`.
- Set `TIME_ARCHIVE_STORAGE_S3_PUBLIC_BASE_URL` to the configured storage base
  URL used for managed object references in that environment.
- Set `TIME_ARCHIVE_STORAGE_S3_BUCKET`.
- Confirm local, staging, and production deployments do not share the same
  bucket unless explicitly approved as a high-impact operational decision.
- Treat `TIME_ARCHIVE_STORAGE_S3_BUCKET` and
  `TIME_ARCHIVE_STORAGE_S3_PUBLIC_BASE_URL` as data compatibility boundaries.
  Changing either value against an existing database requires a storage
  migration plan.
- Set access key and secret through secret management.
- Verify presigned PUT from the deployed browser origin.
- Verify presigned GET for admin preview.
- Verify presigned GET for public playback.
- Confirm API responses carrying presigned URLs are not cached by shared caches.

See [Cloudflare R2 Storage Setup](r2-storage-setup.md) for local verification.

## Go Or No-Go Rule

For a private demo, the system can be released when all CI checks are green and
all local verification scripts pass in a clean environment.

For any public or paid launch, every `Blocked` item in this checklist must be
resolved or explicitly accepted by the project owner with a documented rollback
and incident response plan.
