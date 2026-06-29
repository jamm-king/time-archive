# Staging Database User

## Purpose

This runbook defines how to create or update the first staging PostgreSQL
application/migration user for Time Archive.

The selected staging username is:

```text
timearchive_app
```

The password is stored as a SecureString at:

```text
/time-archive/staging/database/password
```

The RDS master password remains infrastructure-only at:

```text
/time-archive/bootstrap/staging/database/master-password
```

Do not print, copy into Git, or paste either password into chat, logs, PRs, or
screenshots.

## Why One User For Staging

The current application uses the primary datasource for both runtime data access
and Flyway migrations. Until the application supports separate migration and
runtime credentials, the first staging user must be able to:

- connect to the `time_archive` database;
- create and alter objects in the `public` schema;
- read and write application tables;
- use application sequences.

This is a staging-only compromise. Before production, split the database
identities into:

- a migration user with schema migration privileges;
- a runtime user with application DML privileges only;
- a break-glass administrative user.

## Execution Model

Use:

```bash
./scripts/bootstrap-staging-db-user.sh \
  --expected-account-id 231851555445 \
  --allow-temporary-master-password-read \
  --profile time-archive-staging-admin \
  --region ap-northeast-2
```

The script:

1. Verifies the authenticated AWS account.
2. Resolves the staging EC2 instance ID and RDS endpoint from CloudFormation
   outputs.
3. Temporarily attaches one inline IAM policy that allows the EC2 role to read
   only `/time-archive/bootstrap/staging/database/master-password`.
4. Sends an SSM Run Command to the staging EC2 instance.
5. On EC2, reads required passwords from SSM with decryption.
6. Runs `psql` through `postgres:18-alpine` with Docker.
7. Creates or updates `timearchive_app`.
8. Grants the required staging schema, table, sequence, and default privileges.
9. Verifies the role can log in and create a temporary table.
10. Removes the temporary inline IAM policy.

The script logs command status and high-level progress only. It does not print
password values.

## Dry Run

Run this before the real command:

```bash
./scripts/bootstrap-staging-db-user.sh \
  --dry-run \
  --expected-account-id 231851555445 \
  --profile time-archive-staging-admin \
  --region ap-northeast-2
```

Dry run verifies account and stack outputs and builds the SSM command payload,
but it does not send a command to EC2.

The temporary IAM permission is required because the normal EC2 application role
intentionally cannot read the bootstrap master password path. The permission is
scoped to one SSM parameter and is removed by the script cleanup handler.

## Rollback

If the user was created with the wrong password, update the SSM runtime
parameter and rerun the bootstrap script. If the user must be disabled:

```sql
alter role timearchive_app nologin;
```

Only drop the role after confirming no deployment uses it and no owned database
objects depend on it.

## Current Status

The staging database user bootstrap has completed. SSM command
`861881cc-8b8b-409e-a005-9f14f43b5d93` created or updated `timearchive_app`,
applied grants, verified login, and verified temporary table DDL. The temporary
inline IAM policy used to read the bootstrap master password was removed after
execution.

The next verification point is the first staging application deployment, where
Flyway migrations and runtime database access must both pass with this user.
