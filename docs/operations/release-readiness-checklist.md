# Release Readiness Checklist

This checklist is the release gate for the Time Archive MVP. It focuses on the
work required before exposing the application outside local development.

Current baseline: `main` after PR #64 on 2026-06-24.

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
- Production secret injection, access control, and rotation through the chosen
  deployment platform.
- Cloudflare edge abuse controls and deployed trusted-client attribution;
  application-level Redis rate limiting is implemented.
- Production observability for application logs, errors, health, and security
  events.
- File signature validation and a documented malware-scanning path for uploaded
  media.

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
- Redis-backed rate limiting for authentication, public reads, purchase, media,
  and admin surfaces.
- Explicit ignored local environment files with required secret values and no
  committed runtime secret fallbacks.
- Local Cloudflare R2 configuration and R2-backed media upload verification
  using resources isolated from production.

## Security

| Area | Status | Release Gate |
| --- | --- | --- |
| Session authentication | Ready | Staging auth smoke workflow verifies registration, login, logout, `/api/me`, and deployed `HttpOnly`, `Secure`, `SameSite=Lax` session cookie attributes. Repeat after auth changes and before production. |
| CSRF protection | Ready | Staging auth smoke workflow verifies mutation rejection without `X-XSRF-TOKEN`; fake provider callbacks remain excluded from browser-facing deployed environments. Repeat after auth or payment callback changes. |
| Admin authorization | Ready | Staging admin smoke workflow verifies unauthenticated rejection, non-admin rejection, and admin moderation-list access. Repeat before production and extend if new admin actions are added. |
| Admin bootstrap | Blocked for production | Staging has an operator-controlled SSM admin grant script; production still needs an approved provisioning process and role-change audit path. |
| Password policy | Ready for MVP | Registration enforces a minimum password length of 8 characters, passwords are stored through BCrypt hashing, and registration tests cover short-password rejection. Password reset remains a post-MVP follow-up. |
| Application rate limiting | Ready | Redis-backed limits cover auth, public reads, purchase, media mutation, and admin routes with atomic counters and fail-closed behavior. |
| Edge rate limiting and client identity | Needs verification | Restrict direct origin access, configure the trusted client IP header, and add Cloudflare edge limits in the deployed environment. |
| Sensitive logging | Ready | After the generated default password logging fix was deployed to staging, API/Web CloudWatch keyword sampling and Logs Insights regex checks found no confirmed sensitive-log matches for passwords, session cookies, CSRF tokens, authorization headers, storage credentials, presigned URLs, or payment payload secrets. Repeat after logging, auth, storage, payment, or deployment changes. |
| Security headers | Ready | After redeploying staging from the security-header change, the manual `Smoke staging security headers` workflow passed against the public HTTPS hostname and verified HSTS, frame policy, content type sniffing protection, conservative referrer policy, minimal CSP, and browser permission restrictions. Repeat after Web routing, Cloudflare, or header-policy changes. |

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
| Local Cloudflare R2 flow | Ready | Separate local R2 configuration, bucket isolation, and an R2-backed media upload were verified without committing credentials. |
| Production Cloudflare R2 | Blocked for production | Provision a separate production bucket and least-privilege credentials, then verify CORS, private access, upload, preview, and playback from staging. |
| Presigned upload URLs | Needs verification | Confirm TTL, content type, content length, and CORS behavior from the deployed web origin. |
| Staging media upload and admin preview | Needs verification | Manual staging media preview smoke workflow uploads to the pre-granted `[7000, 7001)` range, verifies admin list visibility, and downloads through a short-lived admin preview URL. Run before a release candidate. |
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
| Ownership transaction boundaries | Needs verification | Local purchase flows are covered; staging media smoke can use explicit `ADMIN_GRANT` owned ranges while real provider ownership remains a production blocker. |
| Migration execution | Needs verification | Confirm Flyway migrations run in staging before production. |
| Staging database user | Needs verification | The staging `timearchive_app` database user exists and login/DDL bootstrap checks passed; verify Flyway migrations and runtime queries during first deployment. |
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
- Production deployment policy and Linux ARM64 image builds.
- Staging CloudFormation schema and architecture-policy validation.
- Staging provisioning input and read-only command policy validation.
- Staging image-publication workflow policy validation.
- Staging deployment workflow policy validation.
- Staging public smoke workflow policy validation.
- Staging request ID smoke workflow policy validation.
- Staging auth smoke workflow policy validation.
- Staging admin role grant script policy validation.
- Staging admin smoke workflow policy validation.
- Staging owned range grant script policy validation.
- Staging media preview smoke workflow policy validation.
- Staging rollback drill policy validation.

The PR #58 CI baseline passed all required checks after Compose startup and
MinIO initialization were stabilized. Future release candidates must pass the
same checks from their own commit and must not rely on this historical result.

Release candidate verification:

- Start from a clean Docker Compose state.
- Run all local shell verification scripts.
- Manually verify registration, login, logout, purchase, owned ranges, upload,
  admin moderation, original preview, and public playback.
- Confirm all GitHub Actions checks are green on the release PR.

## Deployment And Configuration

| Area | Status | Release Gate |
| --- | --- | --- |
| Deployment architecture | Ready | EC2, RDS PostgreSQL, Redis on EC2, R2, Cloudflare Tunnel, SSM Parameter Store, CloudWatch, and Sentry Developer are selected and documented. |
| Staging infrastructure as code | Ready | Corrected 34-resource stack reached `CREATE_COMPLETE`; EC2 bootstrap, private RDS, ECR, IAM/OIDC, logs, alarms, and network boundaries were verified, with database egress hardening tracked separately. |
| Staging provisioning preflight | Ready | Non-root SSO, real operator inputs, GitHub OIDC metadata, SSM SecureString metadata, RDS offering, and target-account template validation passed in `ap-northeast-2`; no change set has been created. |
| Staging image publication | Ready | Manual OIDC workflow publishes paired ARM64 images with immutable full Git SHA tags, provenance, SBOM, and digest verification from `main`. |
| Docker images | Needs verification | ARM64 builds pass CI and staging images publish to ECR; review ECR scan findings, attestations, and digest-qualified deployment references before deployment. |
| Local environment variables | Ready | Local and R2 values use explicit ignored env files created from committed placeholder templates. |
| Staging secret injection | Needs verification | The staging SSM runtime contract and metadata validator are implemented; provision real parameters and verify metadata before deployment. |
| Production secret injection | Blocked for production | The SSM runtime renderer and staging/production parameter contracts are implemented; provision production-scoped parameters, IAM access, KMS policy, and rotation procedure. |
| Committed secret defaults | Ready | Compose and Spring no longer provide committed database, object storage, or rate-limit secret fallbacks. |
| HTTPS | Ready | Cloudflare-managed edge TLS and Tunnel ingress were verified in staging through browser access to the published HTTPS hostname. Production must still verify secure cookies, forwarded protocol behavior, and redirect policy. |
| Cloudflare | Needs verification | Staging Published Application routing to `web:3000` was verified through Cloudflare Tunnel. Production still needs edge cache bypass policy, WAF, rate limits, and public health checks. |
| Staging deployment workflow | Ready | Manual SSM Run Command workflow deployed `813c73b1f2def9f64c8e9bde0115a59db4bd210e` from `main` with digest-pinned Redis/cloudflared images, after the deploy-role ECR verification permission was applied. |
| Application health checks | Ready | Staging API, Web, and Redis containers were healthy; API returned `UP`, Web responded internally, `cloudflared` passed connectivity prechecks, and a manual public smoke workflow is available for the staging hostname. |
| Rollback | Ready | Staging image rollback and forward recovery were verified on 2026-06-30 using the documented drill. Database rollback remains a separate high-impact recovery procedure. |

## Observability And Operations

| Area | Status | Release Gate |
| --- | --- | --- |
| Application logs | Ready | API request correlation and safe request completion logging are implemented with `X-Request-Id`; CloudWatch log groups and retention are statically verified, and staging verification confirmed the request ID smoke workflow succeeds and the request ID is searchable in `/time-archive/staging/api`. Repeat after request-correlation, logging, or deployment logging changes. |
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
- No Cloudflare edge rate limits or deployed trusted-client attribution are
  configured yet.
- Application rate-limit thresholds have not been tuned from production
  traffic.

## Production R2 Readiness Checklist

The local R2 integration path is ready: configuration is externalized, a local
verification bucket is isolated from production, and an R2-backed media upload
has passed. The following gates apply before connecting staging or production
R2 resources:

- Keep the existing local verification bucket isolated from every deployed
  environment.
- Provision a dedicated production media bucket with the approved access
  policy.
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

The current `main` baseline meets the automated private-demo gate as of PR #61.
A demo release should still repeat the manual verification steps above from the
exact release candidate.

For any public or paid launch, every `Blocked` item in this checklist must be
resolved or explicitly accepted by the project owner with a documented rollback
and incident response plan.
