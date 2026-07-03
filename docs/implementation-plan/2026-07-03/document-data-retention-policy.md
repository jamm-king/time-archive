# Document Data Retention Policy

## Objective

Define the MVP data retention policy for Time Archive and align the release
readiness checklist with the documented operating rules.

## Scope

- Add a data retention operations runbook.
- Document retention targets for sessions, rate-limit keys, application logs,
  audit logs, purchase and ownership records, reservations, upload requests,
  media assets, rejected media, smoke-test data, and backups.
- Update release readiness documentation.
- Update existing security and deployment architecture documents to point to
  the new policy.

## Out Of Scope

- Production code changes.
- Database cleanup jobs.
- Object storage deletion automation.
- Account deletion implementation.
- Backup provisioning or restore drills.

## Relevant Files Or Modules

- `docs/operations/data-retention-policy.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/architecture/security-and-operations.md`
- `docs/operations/ec2-rds-deployment-architecture.md`
- `docs/implementation-plan/2026-07-03/document-data-retention-policy.md`

## Key Design Decisions

- Treat purchase, ownership, payment, and audit records as financial-grade
  records that are retained long term and not physically deleted in ordinary
  user flows.
- Keep runtime logs short-lived at the existing 14-day CloudWatch target.
- Keep session and rate-limit data ephemeral.
- Define manual cleanup expectations for rejected media, expired upload
  requests, and staging smoke-test data until cleanup automation exists.
- Keep R2 object cleanup tied to database state so object deletion does not
  break references unexpectedly.

## Step-By-Step Execution Plan

1. Add this implementation plan.
2. Add the data retention operations runbook.
3. Update release readiness checklist.
4. Update architecture and deployment documents that reference retention gaps.
5. Run documentation diff checks.

## Risks And Rollback Strategy

- Risk: Retention periods could conflict with future legal requirements.
  - Mitigation: State that legal review is required before public launch.
- Risk: Manual cleanup could be forgotten.
  - Mitigation: Track cleanup automation as follow-up work and keep the policy
    explicit about manual operational responsibility.
- Rollback: Revert the documentation changes and return the release checklist
  row to `Needs verification`.

## Verification Plan

- Run `git diff --check`.
- Review release readiness status wording.

## Open Questions

- None for this documentation step.

## Progress

- Created implementation plan.
- Added data retention operations runbook.
- Updated release readiness checklist.
- Updated security and deployment architecture references.

## Completion Summary

The MVP data retention policy is now documented. It defines retention targets
for sessions, rate-limit keys, runtime logs, users, purchases, ownership
records, payment events, audit logs, reservations, checkout sessions, media
upload requests, media objects, staging smoke-test data, and backups.

The release readiness checklist now marks the data retention policy as
`Ready for MVP` while explicitly noting that automated cleanup jobs do not
exist yet.

## Files Changed

- `docs/operations/data-retention-policy.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/architecture/security-and-operations.md`
- `docs/operations/ec2-rds-deployment-architecture.md`
- `docs/implementation-plan/2026-07-03/document-data-retention-policy.md`

## Tests Run And Results

- `git diff --check`: passed.

## Manual Verification Results

- Reviewed the release readiness checklist row for `Data retention policy`.
- Confirmed the policy does not claim cleanup automation exists.
- Confirmed the policy preserves long-term purchase, ownership, payment, and
  audit records.

## Known Limitations

- No automated cleanup jobs exist yet.
- Account deletion and anonymization are not implemented.
- Production backup provisioning and restore drills remain separate release
  blockers.
- Final legal and privacy review is still required before public launch.

## Follow-Up Recommendations

- Implement cleanup automation after production data volume justifies it,
  starting with staging smoke-test cleanup and expired upload request cleanup.
- Keep backup and restore drill work separate from this retention policy.
