# Bootstrap Staging Database User

## Objective

Create a safe, repeatable process for bootstrapping the staging PostgreSQL
application/migration database user before the first EC2 deployment.

## Scope

- Add a script that uses SSM Run Command to bootstrap the staging DB user from
  the managed EC2 host.
- Add an operations runbook for the database user bootstrap.
- Update release readiness and staging runtime parameter documentation.
- Verify scripts locally without printing secret values.

Out of scope:

- Changing application datasource behavior.
- Splitting runtime and migration database users.
- Running application deployment.
- Modifying production database users.

## Relevant Files

- `scripts/bootstrap-staging-db-user.sh`
- `docs/operations/staging-database-user.md`
- `docs/operations/staging-runtime-parameters.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- Use AWS Systems Manager Run Command instead of SSH.
- Run `psql` through the official `postgres:18-alpine` Docker image on the
  staging EC2 host to avoid installing a PostgreSQL client on the host.
- Temporarily allow the EC2 role to read only the bootstrap master password
  parameter during the bootstrap command, then remove that inline policy.
- Read the RDS master password and application password from SSM on the EC2 host
  with decryption; never print values.
- Make the SQL idempotent: create the role if missing, update the password, and
  reapply grants safely.
- Use a single staging `timearchive_app` identity for both Flyway and runtime
  because the current application has one datasource. Splitting identities
  remains required before production.

## Step-by-step Execution Plan

1. Add this implementation plan.
2. Add the SSM Run Command bootstrap script.
3. Add the operations runbook.
4. Update release readiness and parameter documentation.
5. Run local syntax and dry-run validation.
6. After explicit execution approval, run the bootstrap against staging.
7. Record the result without secret values.

## Risks And Rollback Strategy

- Risk: granting excessive database privileges. Mitigation: grant staging-only
  schema creation and table/sequence DML privileges needed by current Flyway and
  runtime behavior; do not grant superuser or RDS master credentials to the app.
- Risk: printing passwords. Mitigation: no shell tracing, no value echoing, and
  SQL uses psql variables.
- Risk: wrong AWS account or instance. Mitigation: require the expected account
  ID and resolve the instance from CloudFormation outputs.
- Risk: temporary IAM access is left behind. Mitigation: attach one named inline
  policy immediately before execution and remove it through the script cleanup
  trap.
- Rollback: disable login or drop the role only after confirming no deployment
  is using it. For normal correction, rerun the script to rotate the password or
  reapply grants.

## Verification Plan

- Run `bash -n scripts/bootstrap-staging-db-user.sh`.
- Run `scripts/bootstrap-staging-db-user.sh --dry-run` against the staging
  account to verify account, stack outputs, and SSM command payload generation
  without sending a command.
- Run `git diff --check`.

## Open Questions

- None for staging. Production must split migration and runtime identities.

## Progress

- Created the dedicated branch from current `main`.
- Added `scripts/bootstrap-staging-db-user.sh`.
- Added the staging database user operations runbook.
- Updated release readiness and staging runtime parameter documentation.
- Dry-run verified the staging account, stack outputs, instance ID, database
  endpoint, application role ARN, and SSM command payload without changing AWS.
- Executed the approved bootstrap. The script temporarily attached the scoped
  master-password read policy, ran SSM command
  `861881cc-8b8b-409e-a005-9f14f43b5d93`, created or updated
  `timearchive_app`, verified login and temporary table DDL, and removed the
  temporary IAM policy.

## Completion Summary

The staging PostgreSQL `timearchive_app` user has been bootstrapped for the
first staging deployment. The operation used SSM Run Command and Docker-based
`psql` from the staging EC2 host. No password values were printed or committed.

## Files Changed

- `scripts/bootstrap-staging-db-user.sh`
- `docs/operations/staging-database-user.md`
- `docs/operations/staging-runtime-parameters.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-06-29/bootstrap-staging-db-user.md`

## Tests Run And Results

- `C:\Program Files\Git\bin\bash.exe -n scripts/bootstrap-staging-db-user.sh`
  - Passed.
- `scripts/bootstrap-staging-db-user.sh --dry-run` against staging account
  `231851555445`
  - Passed.
- `scripts/bootstrap-staging-db-user.sh --allow-temporary-master-password-read`
  against staging account `231851555445`
  - Passed.
- IAM `get-role-policy` for the temporary inline policy after execution
  - Returned `NoSuchEntity`, confirming cleanup.
- `git diff --check`
  - Passed.

## Manual Verification Results

The SSM command output reported PostgreSQL client image pull, role/grant
application, application user login verification, and temporary table DDL
verification. The temporary IAM policy was removed after execution.

## Known Limitations

- Staging uses one database identity for both Flyway migrations and runtime
  access. Production must split migration and runtime credentials.
- The application has not yet been deployed against this database user.

## Follow-up Recommendations

- Add the GitHub Actions staging deploy workflow using SSM Run Command.
- During first deployment, verify Flyway migrations and runtime health using
  this database identity.
