# Data Retention Policy

## Purpose

This runbook defines the MVP data retention policy for Time Archive. It covers
which data is ephemeral, which data is retained for operational or financial
integrity, and which cleanup actions must be manual until automation exists.

This policy is not legal advice. Final public-launch retention periods,
account-deletion rules, tax obligations, and privacy disclosures must be
reviewed by a qualified professional.

## Retention Principles

- Preserve ownership, purchase, payment, and audit integrity.
- Keep runtime and security logs only as long as they are useful for operations.
- Keep original uploaded media private unless approved for public playback.
- Do not delete R2 objects independently from database state without a reviewed
  cleanup or incident procedure.
- Prefer anonymization or state transitions over physical deletion for records
  tied to ownership, payment, audit, or moderation history.
- Use environment-scoped cleanup rules. Staging data is synthetic and should
  not use production retention periods.

## Retention Matrix

| Data class | Initial MVP retention | Cleanup method | Notes |
| --- | --- | --- | --- |
| Server-side sessions | Until logout or session TTL, default 30 minutes | Redis expiration | Controlled by `TIME_ARCHIVE_SESSION_TIMEOUT`; no manual cleanup expected. |
| Rate-limit counters | Until the configured rate-limit window expires | Redis key expiration | Keys use HMAC-derived network identifiers for unauthenticated traffic. |
| CSRF tokens | Session scoped | Session expiration or logout | Tokens are not persisted separately. |
| Application runtime logs | 14 days | CloudWatch Logs retention | Applies to API, Web, Redis, `cloudflared`, migration, and RDS PostgreSQL logs unless a reviewed change says otherwise. |
| Request IDs | Same as runtime logs | CloudWatch Logs retention | Request IDs also appear in API responses and are not a durable business record. |
| Users | Account lifetime | Future account lifecycle process | Account deletion is not implemented; future deletion should anonymize where records must remain. |
| Purchases | Minimum 7 years after transaction or longer if legally required | Do not physically delete in ordinary flows | Required for reconciliation, disputes, tax, and ownership traceability. |
| Ownership records | Indefinite | Do not physically delete in ordinary flows | The canonical archive depends on historical and active ownership state. |
| Payment events | Minimum 7 years after event or longer if legally required | Do not physically delete in ordinary flows | Store provider references and hashes, not raw sensitive provider payloads. |
| Audit logs | Minimum 7 years | Append-only; no ordinary physical deletion | Covers admin moderation and future payment or ownership audit events. |
| Purchase reservations | 180 days for expired or cancelled reservations; completed reservations retained with purchase records | Manual cleanup until automated | Expired holds are operational records, not financial records unless completed. |
| Checkout sessions | 180 days for abandoned sessions; completed sessions retained with purchase records | Manual cleanup until automated | Provider references may be needed for support and reconciliation. |
| Media upload requests | 30 days after expiration or failure; completed requests retained while the media asset exists | Manual cleanup until automated | Cleanup must not remove requests needed for idempotent completion or support review. |
| Uploaded original objects for approved media | While the media asset remains approved or operationally needed | Reviewed media lifecycle operation | Original objects remain private; future derived-media storage may change this. |
| Uploaded original objects for rejected media | 30 days after rejection unless needed for dispute, abuse review, or legal hold | Manual R2 cleanup with audit trail | Keep DB moderation and audit records after object cleanup. |
| Uploaded original objects for hidden media | 180 days after hiding unless needed for dispute, abuse review, or legal hold | Manual R2 cleanup with audit trail | Hidden media was public before removal, so retain longer for incident review. |
| Orphaned upload objects | 7 days after upload request expiration | Manual cleanup until automated | Confirm no completed media asset references the object before deletion. |
| Public playback presigned URLs | Until URL expiration, default 10 minutes | Storage signature expiration | URLs must not be cached by shared caches. |
| Admin preview presigned URLs | Until URL expiration, default 5 minutes | Storage signature expiration | Do not log or persist generated URLs. |
| Staging smoke-test users | 30 days | Manual cleanup until automated | Synthetic accounts such as staging auth smoke users should not accumulate indefinitely. |
| Staging smoke-test media | 30 days unless needed for debugging | Manual moderation and R2 cleanup | Smoke assets should remain isolated from production data. |
| RDS automated backups | Initial production target: 7 days PITR | RDS backup retention | Separate from the restore-drill release gate. |
| Final snapshots | Until reviewed deletion | Manual deletion approval | Required before intentional production database deletion. |

## Environment Rules

### Local

Local data is disposable. Developers may destroy local PostgreSQL, Redis, and
MinIO data when testing. Local R2 buckets must remain isolated from staging and
production.

### Staging

Staging uses synthetic data and may be reset after release verification windows.
Before deleting staging data, preserve only the evidence needed for release
records, incident debugging, or failed smoke investigation.

Staging cleanup targets:

- smoke-test users older than 30 days;
- smoke-test media older than 30 days;
- expired upload requests older than 30 days;
- orphaned R2 objects older than 7 days after request expiration;
- CloudWatch logs through the configured 14-day retention.

### Production

Production must preserve financial, ownership, payment, and audit integrity.
Production deletion requires a reviewed operational procedure when it affects:

- purchase records;
- ownership records;
- payment events;
- audit logs;
- approved or previously approved media;
- R2 objects referenced by database rows;
- backups or snapshots.

## Account Deletion Boundary

Account deletion is not implemented in the MVP. When it is added, it should:

- disable or anonymize the user profile where legally allowed;
- preserve purchase, ownership, payment, and audit records;
- preserve media moderation history where needed for dispute and abuse review;
- avoid breaking public timeline ownership semantics;
- record the deletion or anonymization action in audit logs.

Physical deletion of financial or ownership records is not part of ordinary
account deletion.

## Media Object Cleanup Rules

R2 object cleanup must be state-aware:

1. Identify the database row that owns the object reference.
2. Confirm the media asset status and retention period.
3. Confirm the object is not used by approved playback, admin preview, or a
   completed media asset that still needs the original.
4. Record the object key, media asset ID, actor, reason, and timestamp.
5. Delete the object from the environment-specific bucket.
6. Preserve database audit and moderation records.

Never run broad prefix deletion against a production bucket without a reviewed
plan and rollback analysis. R2 object deletion can be irreversible.

## Manual Cleanup Before Automation

Until cleanup jobs exist, operators should run a monthly review for:

- expired purchase reservations;
- abandoned checkout sessions;
- expired or failed media upload requests;
- rejected or hidden media past the retention window;
- orphaned R2 objects;
- staging smoke-test users and media.

Every manual production cleanup must record:

- actor;
- timestamp;
- environment;
- target data class;
- IDs or object keys affected;
- reason;
- rollback limitations.

## Automation Follow-Up

Future cleanup automation should be implemented as explicit use cases with
ports for database and object storage access. Cleanup jobs must support dry-run
mode, bounded batches, safe logging, and audit records for destructive actions.

Recommended automation order:

1. Staging smoke-test cleanup.
2. Expired upload request and orphaned object cleanup.
3. Expired reservation cleanup.
4. Rejected and hidden media object cleanup.
5. Account anonymization workflow.

## Release Gate

For the MVP, the data retention policy is ready when:

- this document is accepted as the current operating policy;
- CloudWatch log retention remains 14 days;
- session and rate-limit data remain ephemeral;
- financial, ownership, payment, and audit records are not physically deleted
  through ordinary flows;
- production cleanup that touches R2, payment, ownership, audit, or backups is
  treated as a reviewed operational change.

Automation is recommended but not required for the limited MVP launch while
data volume remains low and manual cleanup is acceptable.
