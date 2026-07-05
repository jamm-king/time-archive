# Release Readiness Checklist

This checklist is the release gate for the Time Archive MVP. It focuses on the
work required before exposing the application outside local development.

Current baseline: `main` after PR #117 on 2026-07-05.

Status legend:

- `Ready`: acceptable for the current MVP release gate.
- `Needs verification`: implemented or designed, but must be verified in the
  target environment.
- `Blocked`: not acceptable for a public or paid production release.
- `Deferred`: intentionally outside the first MVP release, but documented.

## Release Decision Summary

Time Archive has passed the core staging MVP backend flows, including PayPal
Sandbox checkout, capture, signature-verified webhook processing, and ownership
finalization. It is not ready for paid production until the blockers below are
resolved.

Production blockers:

- PayPal return-page confirmation UX so users can see that a paid purchase has
  completed after the verified provider webhook grants ownership.
- Production PayPal live application, credentials, webhook, first live-payment
  drill, and rollback/refund operating procedure.
- Production object storage configuration, preferably Cloudflare R2, using
  managed secrets and private buckets.
- Production database backups, restore testing, and migration procedure.
- Production secret injection, access control, and rotation through the chosen
  deployment platform.
- Cloudflare edge abuse controls and deployed trusted-client attribution;
  application-level Redis rate limiting is implemented.
- Production observability for application logs, errors, health, and security
  events, including PayPal webhook failure alerts.
- Media safety operating process acceptance for limited launch, or automatic
  malware scanning before broader public scale.

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
- Staging PayPal Sandbox checkout, capture, webhook signature verification,
  payment event processing, purchase completion, and active ownership creation.

## Security

| Area | Status | Release Gate |
| --- | --- | --- |
| Session authentication | Ready | Staging auth smoke workflow verifies registration, login, logout, `/api/me`, and deployed `HttpOnly`, `Secure`, `SameSite=Lax` session cookie attributes. Repeat after auth changes and before production. |
| CSRF protection | Ready | Staging auth smoke workflow verifies mutation rejection without `X-XSRF-TOKEN`; fake provider callbacks remain excluded from browser-facing deployed environments. Repeat after auth or payment callback changes. |
| Admin authorization | Ready | Staging admin smoke workflow verifies unauthenticated rejection, non-admin rejection, and admin moderation-list access. Repeat before production and extend if new admin actions are added. |
| Admin bootstrap | Blocked for production | Staging has an operator-controlled SSM admin grant script; production still needs an approved provisioning process and role-change audit path. |
| Password policy | Ready for MVP | Registration enforces a minimum password length of 8 characters, passwords are stored through BCrypt hashing, and registration tests cover short-password rejection. Password reset remains a post-MVP follow-up. |
| Application rate limiting | Ready | Redis-backed limits cover auth, public reads, purchase, media mutation, and admin routes with atomic counters and fail-closed behavior. |
| Edge rate limiting and client identity | Ready | Staging now forwards reviewed Cloudflare headers through the Web proxy, the deployed API runtime uses `CF-Connecting-IP`, Cloudflare Free plan edge rate limiting is configured for auth endpoints, and staging smoke workflows passed after the edge changes. Repeat after Cloudflare routing, rate-limit, or runtime header changes. |
| Sensitive logging | Ready | After the generated default password logging fix was deployed to staging, API/Web CloudWatch keyword sampling and Logs Insights regex checks found no confirmed sensitive-log matches for passwords, session cookies, CSRF tokens, authorization headers, storage credentials, presigned URLs, or payment payload secrets. Repeat after logging, auth, storage, payment, or deployment changes. |
| Security headers | Ready | After redeploying staging from the security-header change, the manual `Smoke staging security headers` workflow passed against the public HTTPS hostname and verified HSTS, frame policy, content type sniffing protection, conservative referrer policy, minimal CSP, and browser permission restrictions. Repeat after Web routing, Cloudflare, or header-policy changes. |

## Payment

| Area | Status | Release Gate |
| --- | --- | --- |
| Local fake payment flow | Ready | Keep only for local and CI verification. |
| Fake webhook endpoint | Ready | Disabled by default and registered only when `TIME_ARCHIVE_PAYMENT_FAKE_ENABLED=true`; never enable it in production. |
| PayPal integration design | Ready | The real-payment flow, runtime parameters, webhook boundary, idempotency model, verification plan, and rollback expectations are documented in [PayPal Integration Design](paypal-integration-design.md). |
| PayPal checkout foundation | Ready for staging | Staging PayPal Sandbox order approval and server-side capture passed after runtime SSM PayPal parameters were provisioned and the API was redeployed. Repeat after PayPal adapter, runtime parameter, or deployment changes. |
| Provider webhook verification | Ready for staging | Staging PayPal Sandbox `PAYMENT.CAPTURE.COMPLETED` webhooks verified with PayPal signature verification `SUCCESS`, API webhook `200`, `payment_events.PROCESSED`, `purchases.OWNERSHIP_GRANTED`, and active ownership records. The PayPal Sandbox application is isolated from other projects. |
| Checkout redirect flow | Needs UX follow-up | PayPal approval return and server-side capture work, and ownership is finalized only after the verified PayPal webhook. The Web return page still needs polling/status UX so users do not remain on `Waiting for provider confirmation` after ownership is granted. |
| PayPal return confirmation UX | Blocked for paid production | Add a server-side status read API and Web polling/success/delayed/failure states for the PayPal return page before collecting real money. |
| Payment idempotency | Needs verification | Re-run checkout retry, capture retry, duplicate webhook resend, amount mismatch, and currency mismatch scenarios against the real PayPal integration. The successful staging webhook path proves the happy path only. |
| Production PayPal live setup | Blocked for production | Provision a dedicated production PayPal live app, live webhook URL, production SSM parameters, first live low-value payment drill, refund/rollback procedure, and production Dashboard reconciliation before enabling paid production traffic. |

## Storage And Media

| Area | Status | Release Gate |
| --- | --- | --- |
| Local MinIO flow | Ready | Verified by local upload, public timeline, and admin preview scripts. |
| Local Cloudflare R2 flow | Ready | Separate local R2 configuration, bucket isolation, and an R2-backed media upload were verified without committing credentials. |
| Production Cloudflare R2 | Needs verification | Production R2 requirements are documented in [Production R2 Readiness](production-r2-readiness.md). Provision a dedicated production bucket and least-privilege credentials, then verify CORS, private access, upload, preview, and playback against production before marking this Ready. |
| Presigned upload URLs | Ready | After applying the staging R2 bucket CORS policy, the manual staging presigned upload CORS smoke workflow passed and verified upload request creation, CORS preflight for `PUT` with `content-type`, and actual presigned `PUT` response CORS headers from the deployed Web origin. Repeat after storage bucket, CORS, Web origin, or upload-header changes. |
| Staging media upload and admin preview | Ready | Manual staging media preview smoke passed through the public HTTPS hostname using the pre-granted `[7000, 7001)` range. It verifies owner login, owned range lookup, presigned object upload, completion, admin moderation-list visibility, short-lived admin preview URL creation, and preview download byte equality. |
| Upload completion verification | Ready | Existing checks cover object existence, expected content length, expected content type, ownership, and expiration. |
| Video duration validation | Ready | Local API tests, OpenAPI validation, and the manual staging media duration smoke workflow passed. The staging smoke verifies short `video/mp4` upload completion with `durationMs`, over-duration completion rejection with `MEDIA_DURATION_EXCEEDS_OWNED_RANGE`, and no media asset creation for the rejected upload. Repeat after upload completion, MP4 parsing, storage, or media API changes. |
| File signature validation | Ready | Upload completion validates supported media signatures before creating media assets. Local API tests pass, and the manual staging media signature smoke workflow passed against the deployed staging public HTTPS hostname. Repeat after upload completion, storage, or media-type validation changes. |
| Media safety and malware scanning | Needs verification | The limited-launch media safety policy is documented in [Media Safety Policy](media-safety-policy.md). Automatic scanning is deferred, but admin approval remains the publication gate. Project-owner acceptance of this residual risk is required before marking this Ready. |
| Transcoding and thumbnail generation | Deferred | MVP can use original approved objects, but production should generate safe derived media. |
| Approved storage references | Ready | Approval rejects URLs that do not belong to the configured storage base URL. |
| Public playback URLs | Ready | Public timeline returns short-lived presigned GET URLs and `Cache-Control: no-store`. |
| Storage backend changes | Ready for MVP | [Storage Backend Change Procedure](storage-backend-change-procedure.md) defines bucket, endpoint, and object-reference base URL changes as high-impact operational changes requiring migration, verification, rollback, and explicit approval. |

## Database And Data Integrity

| Area | Status | Release Gate |
| --- | --- | --- |
| Canonical timeline constraint | Ready | Keep one fixed 86,400-second archive. |
| Ownership transaction boundaries | Ready for staging | Local purchase flows are covered, and staging PayPal Sandbox webhook processing completed `purchase_reservations`, `payment_events`, `purchases`, and `ownership_records` in the expected final states. Recheck after payment completion or ownership transaction changes. |
| Migration execution | Ready for staging | Staging deployments run Flyway through the migration profile before starting the API. Production migration execution still requires production deployment verification and rollback planning. |
| Staging database user | Ready | The staging `timearchive_app` database user exists, login/DDL bootstrap checks passed, and deployed runtime queries have been verified through staging smoke and PayPal purchase flows. |
| Backups | Needs verification | Backup policy and production requirements are documented in [Database Recovery Runbook](database-recovery-runbook.md). Enable production RDS automated backups with at least 7 days retention, deletion protection, and final snapshot behavior before marking this Ready. |
| Restore test | Blocked for production | Restore drill procedure is documented in [Database Recovery Runbook](database-recovery-runbook.md), but at least one staging or isolated restore drill must pass before paid production launch. |
| Data retention policy | Ready for MVP | Retention targets are documented in [Data Retention Policy](data-retention-policy.md). Runtime logs remain 14-day CloudWatch records, sessions and rate-limit keys are ephemeral, financial and ownership records are retained long term, and manual cleanup is accepted until cleanup automation is added. |

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
- Staging media duration smoke workflow policy validation.
- Staging media signature smoke workflow policy validation.
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
| Staging secret injection | Ready | Staging SSM runtime parameters are provisioned and rendered into the deployed containers. PayPal Sandbox client credentials and webhook ID were rotated to a Time Archive-specific Sandbox app and verified after redeployment without printing secret values. |
| Production secret injection | Needs verification | Production parameter contract and safety boundaries are documented in [Production Runtime Parameters](production-runtime-parameters.md). Provision production-scoped parameters, IAM access, KMS policy, and runtime rendering verification before marking this Ready. |
| Committed secret defaults | Ready | Compose and Spring no longer provide committed database, object storage, or rate-limit secret fallbacks. |
| HTTPS | Ready | Cloudflare-managed edge TLS and Tunnel ingress were verified in staging through browser access to the published HTTPS hostname. Production must still verify secure cookies, forwarded protocol behavior, and redirect policy. |
| Cloudflare | Ready for staging | Staging Published Application routing to `web:3000`, exact PayPal webhook path routing to `api:8080`, cache bypass, Free plan custom rules, auth endpoint edge rate limiting, trusted client IP runtime configuration, and staging smoke workflows were verified. Production still needs production-hostname Cloudflare policy configuration and verification. |
| Staging deployment workflow | Ready | Manual SSM Run Command workflow deploys immutable API/Web image SHAs from `main` with digest-pinned Redis/cloudflared images. The workflow has been reused for PayPal runtime parameter rotation and verification. |
| Application health checks | Ready | Staging API, Web, and Redis containers were healthy; API returned `UP`, Web responded internally, `cloudflared` passed connectivity prechecks, and a manual public smoke workflow is available for the staging hostname. |
| Rollback | Ready | Staging image rollback and forward recovery were verified on 2026-06-30 using the documented drill. Database rollback remains a separate high-impact recovery procedure. |

## Observability And Operations

| Area | Status | Release Gate |
| --- | --- | --- |
| Application logs | Ready | API request correlation and safe request completion logging are implemented with `X-Request-Id`; CloudWatch log groups and retention are statically verified; staging request ID search passed; PayPal webhook verification logs now expose safe event id/type, verification status, and masked transmission metadata without logging secrets or raw payloads. Repeat after request-correlation, logging, payment, or deployment logging changes. |
| Error tracking | Needs verification | Minimum error-tracking requirements are documented in [Observability Minimum](observability-minimum.md). Integrate Sentry for API/Web errors with sensitive-data filtering, or explicitly accept CloudWatch-only risk before paid production. |
| Metrics | Needs verification | Minimum metrics are documented in [Observability Minimum](observability-minimum.md). Verify infrastructure, deployment, API/Web, storage, and PayPal webhook signals before paid production. |
| Audit logs | Ready | Admin approval, rejection, and hiding append audit records in the moderation transaction. |
| Alerts | Needs verification | Minimum alert surfaces are documented in [Observability Minimum](observability-minimum.md). Create and test production alert delivery for health, deployment, RDS, storage, and PayPal webhook failures before marking this Ready. |

## Known MVP Limitations

- PayPal Sandbox is integrated and verified in staging; production PayPal live
  remains unverified.
- PayPal return confirmation UX does not yet poll ownership completion after
  capture.
- No password reset flow exists.
- No email verification exists.
- No automatic media scanning or transcoding exists.
- No user-facing support or dispute workflow exists.
- No resale or secondary market exists.
- No admin invitation or role management UI exists.
- No production R2 environment is verified yet.
- No automated data cleanup jobs exist yet.
- Production Cloudflare edge limits, PayPal webhook route, and trusted-client
  attribution are not configured yet.
- Application rate-limit thresholds have not been tuned from production
  traffic.

## Production Runtime Readiness References

Production runtime readiness is split across focused runbooks:

- [Production Runtime Parameters](production-runtime-parameters.md)
- [Production R2 Readiness](production-r2-readiness.md)
- [Storage Backend Change Procedure](storage-backend-change-procedure.md)
- [Database Recovery Runbook](database-recovery-runbook.md)
- [Observability Minimum](observability-minimum.md)
- [PayPal Integration Design](paypal-integration-design.md)

Local R2 verification remains documented in
[Cloudflare R2 Storage Setup](r2-storage-setup.md).

## Go Or No-Go Rule

For a private demo, the system can be released when all CI checks are green and
all local verification scripts pass in a clean environment.

The current `main` baseline has passed the core staging backend payment path as
of PR #117, but a demo release should still repeat the manual verification
steps above from the exact release candidate.

For any public or paid launch, every `Blocked` item in this checklist must be
resolved or explicitly accepted by the project owner with a documented rollback
and incident response plan. Real-money launch also requires PayPal return
confirmation UX, production PayPal live verification, production runtime
parameter verification, restore-drill completion, alert delivery verification,
and media safety residual-risk acceptance or scanning.
