# Security and Operations

## Security Goals

Time Archive accepts payments, stores ownership records, and hosts user-uploaded media. The system must protect users, admins, payment flows, media storage, and public visitors.

## Authentication and Authorization

Recommended controls:

- HTTPS only
- Secure, HTTP-only cookies when using session-based authentication
- CSRF protection for cookie-authenticated flows
- Strict CORS policy
- Role-based access control
- Separate admin authorization checks
- Re-authentication or stronger checks for sensitive admin actions

The MVP authentication foundation uses server-side sessions stored in Redis.
Clients receive an HTTP-only session cookie and should not send self-asserted
identity claims for production behavior.

Purchase APIs derive buyer identity from authenticated server-side session
identity. Clients must not send buyer or owner identity claims for purchase
reservation creation or checkout creation.

Browser mutation APIs use CSRF protection with a cookie-backed token. Clients
must call `GET /api/csrf`, keep the returned cookies, and send the token value
in `X-XSRF-TOKEN` for mutating API requests. The CSRF token is not an
authentication secret; it protects the HttpOnly session cookie from cross-site
request forgery.

Implemented roles:

- `USER`
- `ADMIN`

## Payment Security

Required controls:

- Verify payment webhook signatures.
- Store provider event IDs uniquely.
- Never trust browser redirects as payment confirmation.
- Never log full payment payloads if they contain sensitive data.
- Keep payment provider secrets out of source control.
- Use environment variables or a secret manager.
- Store only payment references needed for reconciliation.

The development-stage fake payment webhook endpoint is for local MVP verification only. It must not be exposed as a production payment confirmation path. Production payment webhooks must verify provider signatures before calling payment completion logic.

The fake payment webhook endpoint is excluded from CSRF protection because it
models a server-to-server provider callback, not a browser session mutation.

## Media Upload Security

User uploads are high risk.

Required controls:

- Enforce file size limits.
- Enforce allowed media types.
- Do not trust file extensions.
- Do not trust client-provided content types.
- Inspect actual file signatures.
- Reject HTML and SVG uploads by default.
- Store original uploads privately.
- Publish only approved and processed media.
- Generate safe thumbnails.
- Consider malware scanning before publication.
- Re-encode videos before public delivery when feasible.

Recommended MVP media policy:

- Allow images and short MP4 videos only.
- Require videos to fit the owned range duration.
- Use muted autoplay for public playback.
- Reject files that cannot be safely processed.

Current media persistence stores URLs and moderation state. Upload completion
verifies ownership, upload request expiration, object existence, expected
content length, and expected content type before creating a media asset. File
signature inspection, malware scanning, transcoding, and thumbnail generation
remain required before production publication.

Owned range media APIs derive current-user identity from the authenticated
server-side session and must reject client-provided owner identity claims. The
metadata-only media creation API does not prove that the referenced file exists,
belongs to the caller, or passed file signature and content safety checks.

Upload request APIs issue short-lived S3-compatible presigned upload URLs and store server-generated object keys in `media_upload_requests`. Local development uses MinIO through the same S3-compatible storage port that can later target Cloudflare R2. Upload request creation still does not prove that the object was uploaded correctly; a completion step must verify the object key, expected content length, expected content type, ownership, expiration, and actual file signature before a `MediaAsset` is created or moved into moderation.

The initial upload completion implementation verifies ownership, expiration, object existence, expected content length, and expected content type before creating an `UPLOADED` `MediaAsset`. It does not yet inspect file signatures, scan malware, transcode video, or generate thumbnails, so moderation and processing must still treat uploaded media as untrusted.

Admin original-media preview uses short-lived presigned download URLs generated
after server-side admin authorization. This keeps original uploads private while
still allowing moderation review.

Cloudflare R2 should be connected after the MinIO local flow and upload completion verification are implemented. R2 integration should be configuration-only from the domain perspective: endpoint, bucket, region, credentials, and public delivery base URL should come from environment or secret management.

## Moderation

Moderation status:

- `UPLOADED`
- `PENDING_REVIEW`
- `APPROVED`
- `REJECTED`
- `HIDDEN`
- `DELETED_BY_OWNER`

Important distinction:

- `REJECTED` means the media was never approved for public display.
- `HIDDEN` means previously visible media was removed from public display.

Only `APPROVED` media can appear in the public timeline.

Admin moderation APIs derive admin identity and permissions from the
authenticated server-side session. MVP authorization uses a persisted user role
with `USER` and `ADMIN` values. Initial admin users can be bootstrapped through
configured normalized email addresses, but production admin lifecycle management
should use an explicit invitation, approval, or operator-controlled provisioning
process.

Admin approval, rejection, and hiding append audit logs in the same transaction
as the moderation state change. If audit append fails, the moderation action
should roll back rather than silently changing media state without traceability.

## External Links

External links attached to media should be treated as user-generated content.

Recommended controls:

- Validate URL scheme.
- Allow only `https://` links.
- Block dangerous schemes such as `javascript:`, `data:`, and `file:`.
- Consider a domain blocklist.
- Display outbound link warnings if needed.
- Add appropriate `rel` attributes when rendering links.

## Rate Limiting

Rate limiting should cover:

- Login attempts
- Signup attempts
- Checkout creation
- Reservation creation
- Media uploads
- Offer submission
- Admin login
- Payment webhook endpoints

Redis is a good fit for distributed rate limiting when the application runs on multiple instances.

## Logging

Application logs should be structured and safe.

Include:

- Request ID
- User ID when authenticated
- Endpoint
- HTTP status
- Latency
- Error code
- Exception class

Do not log:

- Secrets
- Tokens
- Full payment payloads
- Raw credentials
- Sensitive personal data

## Audit Logging

Audit logs should be append-only and separate from ordinary application logs.

Audit these actions:

- Reservation creation
- Checkout creation
- Payment confirmation
- Ownership creation
- Ownership transfer
- Media upload
- Media approval
- Media rejection
- Media hiding
- Offer submission
- Offer acceptance
- Offer rejection
- Admin actions
- Platform fee records

Audit entries should include:

- Actor
- Action
- Resource type
- Resource ID
- Before state
- After state
- Request ID
- Timestamp

## Observability

Recommended initial observability:

- Structured JSON logs
- Request correlation IDs
- Application metrics
- Database connection pool metrics
- Payment webhook success and failure metrics
- Media processing failure metrics
- Error tracking for backend and frontend

OpenTelemetry-compatible instrumentation should be considered from the beginning, even if the initial backend is CloudWatch-based.

## Data Integrity and Recovery

Recommended controls:

- PostgreSQL automated backups
- Point-in-time recovery
- Migration rollback plans
- Flyway migration checks
- No destructive schema changes without explicit approval
- Regular restore testing

Ownership and payment records should be treated as financial-grade records and should not be physically deleted under normal user flows.

## Secrets Management

Required controls:

- Do not commit secrets.
- Use `.gitignore` for local-only files.
- Provide `*.example` configuration files.
- Use AWS Secrets Manager, SSM Parameter Store, or equivalent for deployed environments.
- Rotate secrets when exposure is suspected.

## Licensing

Dependency policy:

- Prefer Apache-2.0, MIT, and BSD licenses.
- Avoid GPL and AGPL dependencies unless explicitly approved.
- Check FFmpeg licensing if video processing uses FFmpeg.
- Verify licenses for sample media assets.
- Track third-party frontend libraries and icon sets.

Product legal clarity:

- Users buy a limited right to display approved media in a specific time range of the canonical 24-hour archive.
- Users do not buy time itself.
- Users do not acquire copyright in other users' media.
- Platform terms should define moderation rights, takedown rights, refund policy, and resale rules.

Legal terms should be reviewed by a qualified professional before production launch.

## Operational Runbooks

Initial runbooks should cover:

- Payment webhook failures
- Duplicate payment events
- Ownership mismatch reports
- Media takedown
- CDN cache invalidation
- Timeline manifest regeneration
- Database migration failure
- Object storage outage
- Admin account recovery

## Manual Validation Checklist

Before release:

- Verify that unapproved media is never public.
- Verify duplicate webhooks do not create duplicate ownership.
- Verify overlapping ownership cannot be inserted.
- Verify expired reservations cannot be completed incorrectly.
- Verify admin actions are audited.
- Verify external links are sanitized.
- Verify media upload restrictions work.
- Verify timeline manifests refresh after approval and hiding.
