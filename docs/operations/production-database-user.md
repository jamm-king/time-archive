# Production Database User

## Purpose

This runbook defines how to create or update the first production PostgreSQL
application/migration user for Time Archive.

The selected production username is:

```text
timearchive_prod_app
```

The password is stored as a SecureString at:

```text
/time-archive/production/database/password
```

The RDS master password remains infrastructure-only at:

```text
/time-archive/bootstrap/production/database/master-password
```

Do not print, copy into Git, or paste either password into chat, logs, PRs, or
screenshots.

## Current User Model

The current application uses the primary datasource for both runtime data
access and Flyway migrations. Until the application supports separate migration
and runtime credentials, the first production user must be able to:

- connect to the `time_archive` database;
- create and alter objects in the `public` schema;
- read and write application tables;
- use application sequences.

This is a limited-launch compromise. Before broader scale, split database
identities into:

- a migration user with schema migration privileges;
- a runtime user with application DML privileges only;
- a break-glass administrative user.

## Execution Model

Run a dry run first:

```bash
./scripts/bootstrap-production-db-user.sh \
  --dry-run \
  --expected-account-id 231851555445 \
  --profile <production-operator-profile> \
  --region ap-northeast-2
```

After explicit approval, run:

```bash
./scripts/bootstrap-production-db-user.sh \
  --expected-account-id 231851555445 \
  --allow-temporary-master-password-read \
  --profile <production-operator-profile> \
  --region ap-northeast-2
```

The script:

1. Verifies the authenticated AWS account.
2. Resolves the production EC2 instance ID and RDS endpoint from CloudFormation
   outputs.
3. Temporarily attaches one inline IAM policy that allows the EC2 role to read
   only `/time-archive/bootstrap/production/database/master-password`.
4. Sends an SSM Run Command to the production EC2 instance.
5. On EC2, reads required passwords from SSM with decryption.
6. Runs `psql` through `postgres:18-alpine` with Docker.
7. Creates or updates `timearchive_prod_app`.
8. Grants the required schema, table, sequence, and default privileges.
9. Verifies the role can log in and create a temporary table.
10. Removes the temporary inline IAM policy.

The script logs command status and high-level progress only. It does not print
password values.

## Rollback

If the user was created with the wrong password, update the SSM runtime
parameter and rerun the bootstrap script. If the user must be disabled:

```sql
alter role timearchive_prod_app nologin;
```

Only drop the role after confirming no deployment uses it and no owned database
objects depend on it.

## Release Gate

Production database user bootstrap is ready only after:

- production stack exists and exposes the expected outputs;
- production runtime database username and password exist in SSM;
- dry run passes;
- real bootstrap passes;
- login and temporary-table verification pass;
- the temporary master-password IAM policy is removed.
