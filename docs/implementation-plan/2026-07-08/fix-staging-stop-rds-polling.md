# Fix Staging Stop RDS Polling

## Objective

Fix the staging stop script so it completes successfully with the installed AWS
CLI after sending `rds stop-db-instance`.

## Scope

- Replace the unsupported `aws rds wait db-instance-stopped` call with an
  explicit polling loop based on `describe-db-instances`.
- Update the staging on-demand verifier to require the polling behavior.
- Update the staging on-demand runbook with the operational result.

Out of scope:

- Starting or stopping AWS resources.
- Changing production operations.
- Adding scheduled automation.

## Relevant Files Or Modules

- `scripts/stop-staging-stack.sh`
- `scripts/verify-staging-on-demand-operations.sh`
- `docs/operations/staging-on-demand-runbook.md`

## Key Design Decisions

- Keep EC2 waiting on the supported `ec2 wait instance-stopped` waiter.
- Use a bounded loop for RDS stopped detection to avoid depending on an AWS CLI
  waiter that is not available in the current local CLI.
- Treat `stopped` as success, `stopping` as in-progress, and any other status as
  failure after stop has been requested.
- Keep staging-only guardrails unchanged.

## Step-By-Step Execution Plan

1. Add a reusable RDS stopped polling helper to `stop-staging-stack.sh`.
2. Replace `rds wait db-instance-stopped` with the polling helper.
3. Update `verify-staging-on-demand-operations.sh`.
4. Update the runbook with the discovered waiter limitation.
5. Run shell syntax, policy verifier, and whitespace checks.
6. Record completion results in this plan.

## Risks And Rollback Strategy

- Risk: polling loop waits too long or masks unexpected states.
  - Mitigation: bounded attempts and explicit failure for non-`stopping` /
    non-`stopped` states.
- Risk: production guardrails regress.
  - Mitigation: keep existing verifier checks for staging stack, staging runtime
    path, staging DB identifier, and forbidden production strings.
- Rollback: revert this small script change and use manual RDS status polling
  as a temporary workaround.

## Verification Plan

- Run `bash -n scripts/stop-staging-stack.sh scripts/verify-staging-on-demand-operations.sh`.
- Run `./scripts/verify-staging-on-demand-operations.sh`.
- Run `git diff --check`.

## Open Questions

- None.

## Progress

- Completed: RDS stopped polling helper added.
- Completed: unsupported RDS waiter removed.
- Completed: policy verifier updated.
- Completed: runbook updated with the discovered waiter limitation.

## Completion Summary

`stop-staging-stack.sh` no longer depends on the unavailable
`aws rds wait db-instance-stopped` waiter. After issuing `stop-db-instance`, it
polls `describe-db-instances` until the staging DB reports `stopped`, fails on
unexpected states, and times out after bounded attempts.

## Files Changed

- `docs/implementation-plan/2026-07-08/fix-staging-stop-rds-polling.md`
- `docs/operations/staging-on-demand-runbook.md`
- `scripts/stop-staging-stack.sh`
- `scripts/verify-staging-on-demand-operations.sh`

## Tests Run And Results

- `bash -n scripts/stop-staging-stack.sh scripts/verify-staging-on-demand-operations.sh`
  through Git Bash: passed.
- `./scripts/verify-staging-on-demand-operations.sh` through Git Bash: passed.
- `git diff --check`: passed.

## Manual Verification Results

No AWS start or stop operation was executed from this fix branch. The issue was
observed during the prior staging stop: EC2 and RDS both reached `stopped`, but
the script exited non-zero because the AWS CLI rejected the waiter name.

## Known Limitations

- The loop can wait up to roughly 40 minutes. If RDS remains in `stopping`
  longer than that, the script fails and the operator should inspect AWS state.

## Follow-Up Recommendations

- Merge this fix before relying on `stop-staging-stack.sh` as the normal
  staging shutdown command.
