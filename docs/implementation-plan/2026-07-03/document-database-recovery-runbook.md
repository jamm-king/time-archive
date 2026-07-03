# Document Database Recovery Runbook

## Objective

Define the database backup, restore, and recovery drill policy for a paid
production Time Archive launch.

## Scope

- Add a database recovery operations runbook.
- Document backup retention, PITR usage, final snapshots, restore drills,
  Flyway verification, R2 consistency boundaries, and rollback decisions.
- Update release readiness documentation.
- Link the runbook from existing EC2/RDS architecture documentation.

## Out Of Scope

- Creating or modifying AWS RDS resources.
- Running a live restore drill.
- Changing CloudFormation.
- Changing application code or database migrations.
- Implementing backup automation beyond RDS-managed backups.

## Relevant Files Or Modules

- `docs/operations/database-recovery-runbook.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/ec2-rds-deployment-architecture.md`
- `docs/implementation-plan/2026-07-03/document-database-recovery-runbook.md`

## Key Design Decisions

- Use RDS automated backups with PITR as the baseline production backup
  mechanism.
- Keep Single-AZ RDS as the cost-conscious MVP availability tradeoff, with
  recovery handled through backups and restore drills.
- Prefer forward-fix migrations for application rollback; reserve PITR for
  severe data corruption or unrecoverable operator mistakes.
- Treat RDS rows and R2 objects as a consistency boundary. Restoring only the
  database can create references to objects that were deleted after the restore
  point, and restoring only objects can leave unreferenced media.
- Require at least one staging restore drill before marking restore readiness
  as complete.

## Step-By-Step Execution Plan

1. Add this implementation plan.
2. Add the database recovery runbook.
3. Update release readiness checklist status and release gate text.
4. Update EC2/RDS architecture references.
5. Run documentation diff checks.

## Risks And Rollback Strategy

- Risk: Documentation could imply that backups are already enabled in
  production.
  - Mitigation: Keep production backup enablement and restore drill as separate
    target-environment verification gates.
- Risk: Operators may treat PITR as a routine rollback.
  - Mitigation: Document forward-fix as the normal migration rollback path and
    PITR as a high-impact recovery action.
- Rollback: Revert documentation changes and restore checklist blocker text.

## Verification Plan

- Run `git diff --check`.
- Review the checklist rows for backup and restore status accuracy.

## Open Questions

- None for this documentation step.

## Progress

- Created implementation plan.
- Added database recovery operations runbook.
- Updated release readiness checklist.
- Linked the recovery runbook from EC2/RDS architecture documentation.

## Completion Summary

The database recovery policy and restore drill procedure are now documented.
The runbook defines production RDS backup expectations, PITR usage boundaries,
final snapshot requirements, forward-fix versus PITR decision rules, pre-launch
restore drill steps, Flyway validation, R2 object consistency checks, approval
boundaries, and evidence to keep after drills or real recovery.

The release readiness checklist now reflects the split state:

- `Backups`: `Needs verification` until production RDS backup settings are
  enabled and verified.
- `Restore test`: still `Blocked for production` until a staging or isolated
  restore drill passes.

## Files Changed

- `docs/operations/database-recovery-runbook.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/ec2-rds-deployment-architecture.md`
- `docs/implementation-plan/2026-07-03/document-database-recovery-runbook.md`

## Tests Run And Results

- `git diff --check`: passed.

## Manual Verification Results

- Reviewed the release readiness checklist to ensure backup enablement and
  restore drill execution are not incorrectly marked as complete.
- Reviewed the runbook to keep PITR as a high-impact recovery action rather
  than a routine rollback path.

## Known Limitations

- No AWS RDS settings were changed.
- No live restore drill was executed.
- Production RDS backup readiness remains unverified.
- Restore readiness remains blocked until a staging or isolated restore drill
  passes.

## Follow-Up Recommendations

- Verify or update production RDS backup retention, deletion protection, and
  final snapshot settings when production RDS is provisioned.
- Execute a staging or isolated restore drill and record measured RTO/RPO.
- After the drill passes, update the release readiness checklist with the
  verification evidence.
