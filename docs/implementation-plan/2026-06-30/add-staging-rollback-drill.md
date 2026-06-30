# Add Staging Rollback Drill

## Objective

Document and validate a staging rollback drill that can revert the deployed API
and Web images to the previously recorded release while preserving clear
database safety boundaries.

## Scope

- Add a staging rollback drill runbook.
- Add a read-only staging release-state inspection script.
- Add static CI validation for the inspection script and runbook contract.
- Update deployment and release-readiness documentation.

Out of scope:

- Automated production rollback.
- Database rollback automation.
- A new mutating rollback workflow.
- Running a live staging rollback during this implementation.

## Relevant Files Or Modules

- `scripts/inspect-staging-release-state.sh`
- `scripts/verify-staging-rollback-drill.sh`
- `docs/operations/staging-rollback-drill.md`
- `docs/operations/staging-deployment.md`
- `docs/operations/release-readiness-checklist.md`
- `.github/workflows/ci.yml`

## Key Design Decisions

- Reuse the existing `Deploy staging` workflow for rollback execution by
  providing the previous image SHA and the current digest-pinned Redis and
  `cloudflared` image references.
- Keep the new operational script read-only. It sends an SSM command only to
  inspect `/var/lib/time-archive/deployments/current.env` and `previous.env`.
- Treat rollback as image rollback only. Database rollback requires a separate
  point-in-time restore decision and explicit approval.
- Require smoke checks after rollback and after the forward recovery deployment.

## Step-by-step Execution Plan

1. Create the implementation plan.
2. Add the read-only staging release-state inspection script.
3. Add a CI validator for the rollback drill contract.
4. Add the staging rollback drill runbook.
5. Update deployment and release-readiness documentation.
6. Run shell syntax, policy validation, and diff checks.

## Risks And Rollback Strategy

- Risk: operators may assume image rollback can undo database migrations.
  Mitigation: document that database rollback is out of scope and requires a
  separate recovery decision.
- Risk: the inspection script could expose secrets. Mitigation: only read the
  release metadata files, which contain image references and timestamps.
- Risk: a rollback drill could leave staging on the previous release. Mitigation:
  require a forward recovery deployment and smoke checks as part of the runbook.
- Rollback: remove the runbook, scripts, and CI validation if the process is
  replaced by a dedicated rollback workflow later.

## Verification Plan

- Run shell syntax validation for the new scripts.
- Run `scripts/verify-staging-rollback-drill.sh`.
- Run `git diff --check`.
- Do not run a live staging rollback in this implementation.

## Open Questions

- None.

## Progress

- Created the dedicated branch from latest `main`.
- Added the read-only staging release-state inspection script.
- Added the rollback drill policy validator.
- Added the staging rollback drill runbook.
- Updated CI, staging deployment, and release-readiness documentation.

## Completion Summary

Added a staging rollback drill process that keeps rollback execution on the
existing `Deploy staging` workflow while adding a read-only release-state
inspection script and CI policy validation for the rollback runbook.

## Files Changed

- `.github/workflows/ci.yml`
- `scripts/inspect-staging-release-state.sh`
- `scripts/verify-staging-rollback-drill.sh`
- `docs/operations/staging-rollback-drill.md`
- `docs/operations/staging-deployment.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-06-30/add-staging-rollback-drill.md`

## Tests Run And Results

- `C:\Program Files\Git\bin\bash.exe -n scripts/inspect-staging-release-state.sh`: passed.
- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-staging-rollback-drill.sh`: passed.
- `C:\Program Files\Git\bin\bash.exe -lc './scripts/verify-staging-rollback-drill.sh'`: passed.
- `git diff --check`: passed.

## Manual Verification Results

- Live staging rollback was not executed in this implementation.
- The inspection script was not run against AWS because this change only adds
  the rollback drill procedure and static validation.

## Known Limitations

- The rollback drill is not complete until it is executed once in staging and
  followed by forward recovery.
- The process is image rollback only; database rollback remains a separate
  high-impact recovery procedure.

## Follow-up Recommendations

- Run `scripts/inspect-staging-release-state.sh` against staging.
- Execute the staging rollback drill after the next successful staging
  deployment creates a meaningful `previous.env`.
