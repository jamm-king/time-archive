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

Current development-stage purchase APIs may accept `buyerId` in the request body only to enable local and early MVP verification before user authentication exists. This is not production-safe. Production APIs must derive buyer identity from authenticated server-side identity and must ignore any client-provided owner or buyer identity claims.

Roles:

- `USER`
- `ADMIN`
- `SYSTEM`

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

Current media persistence stores URLs and moderation state only. Actual upload handling must still validate file size, file signature, media type, and ownership before creating a media asset.

Owned range media APIs currently use the `X-User-Id` request header as development-stage identity input. This is not production-safe. Production media APIs must derive the current user from authenticated server-side identity and must reject client-provided owner identity claims. The current API is metadata-only and does not prove that the referenced file exists, belongs to the caller, or passed file signature and content safety checks.

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
