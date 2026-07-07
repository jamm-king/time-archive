# Add Production Admin Bootstrap

## Objective

Prepare a production-safe operator process for granting the `ADMIN` role to an
existing production user without adding a public bootstrap endpoint, committing
secrets, or opening direct database access.

## Scope

- Add a production admin role grant script modeled after the verified staging
  SSM flow.
- Add a policy verification script and CI check for the production grant script.
- Document the production admin provisioning runbook.
- Update release readiness and first-deploy documentation to point to the new
  process.

Out of scope:

- Executing the production role grant.
- Creating a production admin user.
- Adding an admin invitation UI.
- Adding a direct database connection workflow.

## Relevant Files Or Modules

- `scripts/grant-production-admin-role.sh`
- `scripts/verify-production-admin-role-grant.sh`
- `.github/workflows/ci.yml`
- `docs/operations/production-admin-provisioning.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/production-first-deploy-runbook.md`

## Key Design Decisions

- Production admin provisioning must target an existing user created through the
  normal registration flow.
- The grant must run through AWS SSM Run Command against the production EC2
  instance so it is auditable and does not require SSH or public database
  access.
- The script must validate the AWS account, region, CloudFormation stack
  outputs, runtime parameter path, and expected application database username.
- The script must read database credentials only on the EC2 host from production
  SSM parameters and must not print secrets.
- Actual production execution remains a high-impact operation and requires
  explicit operator approval.

## Step-By-Step Execution Plan

1. Inspect the existing staging admin role grant script and operations runbook.
2. Add the production grant script using production stack, SSM, and database
   parameter paths.
3. Add a production policy verification script.
4. Add the verifier to CI.
5. Document the production admin provisioning runbook.
6. Update release readiness and first-deploy references.
7. Run shell syntax and verifier checks.
8. Record completion status and verification results in this plan.

## Risks And Rollback Strategy

- Risk: granting `ADMIN` to the wrong production user.
  - Mitigation: require exact normalized email input, explicit account ID, dry
    run, operator approval, and operations record.
  - Rollback: run a reviewed demotion operation that sets the same user back to
    `USER`; do not delete the user as a shortcut.
- Risk: script accidentally targets staging or the wrong AWS account.
  - Mitigation: validate AWS account ID, region, production stack name, runtime
    parameter path, and production app database username.
- Risk: leaking database credentials in logs.
  - Mitigation: credentials are read on EC2 and only passed through environment
    variables to the short-lived PostgreSQL client container; verifier checks
    for password printing patterns.

## Verification Plan

- Run `bash -n` on both production admin scripts.
- Run `./scripts/verify-production-admin-role-grant.sh`.
- Run `git diff --check`.
- Optionally run production dry-run against AWS only after operator approval and
  with no role mutation.

## Open Questions

- None for the repository change. Actual production grant target email must be
  selected separately before executing the runbook.

## Progress

- Completed: initial production grant and verifier scripts were drafted from the
  staging pattern.
- Completed: CI now runs the production admin role grant policy verifier.
- Completed: production admin provisioning runbook was added.
- Completed: release readiness and first-deploy documentation now reference the
  production process.
- Completed: local shell syntax, policy verifier, and diff whitespace checks
  passed.

## Completion Summary

Production admin provisioning is now prepared as an explicit operator runbook
and script. The script grants `ADMIN` only to an existing production user,
targets the production CloudFormation stack and SSM runtime path, validates the
expected database application user, and avoids printing secrets. CI now includes
a production policy verifier for the script.

## Files Changed

- `.github/workflows/ci.yml`
- `docs/implementation-plan/2026-07-07/add-production-admin-bootstrap.md`
- `docs/operations/production-admin-provisioning.md`
- `docs/operations/production-first-deploy-runbook.md`
- `docs/operations/release-readiness-checklist.md`
- `scripts/grant-production-admin-role.sh`
- `scripts/verify-production-admin-role-grant.sh`

## Tests Run And Results

- `bash -n scripts/grant-production-admin-role.sh scripts/verify-production-admin-role-grant.sh`
  through Git Bash: passed.
- `./scripts/verify-production-admin-role-grant.sh` through Git Bash: passed.
- `git diff --check`: passed.

## Manual Verification Results

No production role grant was executed. AWS dry-run was intentionally left as a
separate operator-approved step because it contacts the production AWS account,
even though it does not mutate the database.

## Known Limitations

- The production admin user still must be granted and verified before the
  release checklist item can be marked `Ready`.
- The current process is operator-driven. There is no admin invitation UI, MFA,
  or broader role management workflow yet.

## Follow-Up Recommendations

- Execute the production admin grant with explicit approval for the exact target
  email.
- Record the SSM command ID and result in the operations record.
- Add or run a production admin authorization smoke check before using the admin
  account for production R2 verification.
