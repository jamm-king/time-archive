# Add Staging On-Demand Operations

## Objective

Make the staging environment operable on demand so the project can reduce AWS
compute cost when staging is not actively used, without deleting the staging
CloudFormation stack or losing operational metadata.

## Scope

- Add runbooks for starting and stopping staging.
- Add scripts that start and stop only the staging EC2 instance and staging RDS
  DB instance.
- Add static policy verification for those scripts in CI.
- Update release readiness and staging deployment documentation.

Out of scope:

- Executing the start or stop operation.
- Deleting the staging stack or any staging data.
- Changing production operations.
- Adding scheduled automation.

## Relevant Files Or Modules

- `scripts/start-staging-stack.sh`
- `scripts/stop-staging-stack.sh`
- `scripts/verify-staging-on-demand-operations.sh`
- `.github/workflows/ci.yml`
- `docs/operations/staging-on-demand-runbook.md`
- `docs/operations/staging-deployment.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- Keep the staging CloudFormation stack in place. Stop only EC2 and RDS when
  staging is idle.
- Resolve the EC2 instance from the `time-archive-staging` CloudFormation stack
  output `ApplicationInstanceId`.
- Use the fixed staging RDS identifier `time-archive-staging-postgres`.
- Require the expected AWS account ID and `ap-northeast-2` region.
- Validate the staging runtime parameter path `/time-archive/staging/` before
  acting.
- Never allow the scripts to target production resources.
- Do not run deployment automatically after start. Deployment and smoke checks
  remain explicit follow-up actions.

## Step-By-Step Execution Plan

1. Add a staging start script.
2. Add a staging stop script.
3. Add a policy verifier that checks staging-only boundaries and forbidden
   production behavior.
4. Wire the verifier into CI.
5. Add a staging on-demand runbook.
6. Update staging deployment and release readiness documentation.
7. Run shell syntax, policy verifier, and whitespace checks.
8. Record completion results in this plan.

## Risks And Rollback Strategy

- Risk: accidentally stopping production.
  - Mitigation: scripts hard-code staging stack, staging DB identifier, staging
    runtime path, and forbidden production checks in CI.
- Risk: staging RDS automatically restarts after seven consecutive stopped days.
  - Mitigation: document the AWS RDS behavior and require periodic review if
    staging should stay idle.
- Risk: staging is started but old images/runtime are stale.
  - Mitigation: run deployment and smoke workflows explicitly after start.
- Risk: stopping staging interrupts a running smoke or deployment.
  - Mitigation: run stop only after confirming no staging verification or
    deployment workflow is in progress.

## Verification Plan

- Run `bash -n` on the new scripts.
- Run `./scripts/verify-staging-on-demand-operations.sh`.
- Run `git diff --check`.
- Do not run AWS start or stop operations in this branch.

## Open Questions

- None for repository changes. Actual start/stop execution requires explicit
  operator approval.

## Progress

- Completed: staging start script added.
- Completed: staging stop script added.
- Completed: staging on-demand policy verifier added.
- Completed: CI now validates staging on-demand operation scripts.
- Completed: staging on-demand runbook added.
- Completed: staging deployment and release readiness docs updated.

## Completion Summary

Staging can now be operated on demand by stopping and starting only the staging
EC2 application instance and staging RDS DB instance. The scripts validate the
expected AWS account, region, staging stack, staging runtime path, and staging
RDS identifier before acting. They do not deploy images, delete resources, or
target production.

## Files Changed

- `.github/workflows/ci.yml`
- `docs/implementation-plan/2026-07-08/add-staging-on-demand-operations.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/staging-deployment.md`
- `docs/operations/staging-on-demand-runbook.md`
- `scripts/start-staging-stack.sh`
- `scripts/stop-staging-stack.sh`
- `scripts/verify-staging-on-demand-operations.sh`

## Tests Run And Results

- `bash -n scripts/start-staging-stack.sh scripts/stop-staging-stack.sh scripts/verify-staging-on-demand-operations.sh`
  through Git Bash: passed.
- `./scripts/verify-staging-on-demand-operations.sh` through Git Bash: passed.
- `git diff --check`: passed.

## Manual Verification Results

No AWS start or stop operation was executed from this branch.

## Known Limitations

- Stopped RDS storage and backup costs remain.
- RDS can automatically restart after seven consecutive stopped days.
- Starting staging does not deploy images; deployment remains an explicit
  workflow step.

## Follow-Up Recommendations

- Merge the runbook and scripts.
- Run `start-staging-stack.sh --dry-run` and `stop-staging-stack.sh --dry-run`
  with explicit operator approval.
- Stop staging when no staging workflows are active.
- Start staging before future staging deploy or smoke workflows.
