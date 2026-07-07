# Production Admin Provisioning

## Purpose

This runbook defines how to grant the `ADMIN` role to an existing production
user without adding an admin bootstrap API, committing secrets, using SSH, or
opening direct database access.

Production admin provisioning is a high-impact operation. Execute it only after
explicit approval for the exact target email and reason.

## Preconditions

- Production deployment is healthy.
- The target user already exists through normal production registration.
- The target email has been checked exactly and normalized to lowercase.
- The operator is authenticated to the expected AWS account.
- Production runtime parameters and the application database user are already
  provisioned.
- The operation has an approved reason and rollback plan.

## Recommended Flow

1. Create or confirm the normal production user account.
2. Confirm the exact email address that should receive `ADMIN`.
3. Run the grant script in dry-run mode.
4. Review the resolved AWS account, EC2 instance, stack, runtime path, and
   target email.
5. Run the grant script without `--dry-run` only after explicit approval.
6. Verify the user can access admin-only moderation endpoints.
7. Record the operator, email, timestamp, reason, SSM command ID, and result in
   the project operations log.

## Script

Dry run:

```bash
./scripts/grant-production-admin-role.sh \
  --expected-account-id 231851555445 \
  --profile time-archive-staging-admin \
  --email user@example.com \
  --dry-run
```

After review and approval:

```bash
./scripts/grant-production-admin-role.sh \
  --expected-account-id 231851555445 \
  --profile time-archive-staging-admin \
  --email user@example.com
```

Use the approved production-capable AWS CLI profile for the operator account.
The profile name above reflects the current local SSO profile naming and is not
part of the production safety boundary.

The script:

- requires the expected AWS account ID;
- allows only `ap-northeast-2`;
- resolves the production EC2 instance from the `time-archive-production`
  CloudFormation stack;
- verifies the runtime parameter path is `/time-archive/production/`;
- sends an `AWS-RunShellScript` command through SSM;
- reads the production application database username and password from SSM on
  EC2;
- verifies the database username is `timearchive_prod_app`;
- promotes an existing user by normalized email match to `ADMIN`;
- fails if the target user does not exist;
- is idempotent when the user is already `ADMIN`.

The script does not accept a user password and does not print database
credentials.

## Rollback

If the wrong user is promoted, run a reviewed demotion operation that sets the
same user's role back to `USER`, then record the incident and operator action.
Do not delete the user unless a separate data deletion decision has been
approved.

Rollback is also a high-impact production operation and requires the same
approval discipline as the grant.

## Operations Record Template

Record only repository-safe details:

```text
Date:
Operator:
Target email:
Reason:
Approval:
AWS account:
Production stack:
SSM command ID:
Result: PASS | FAIL
Admin smoke result:
Rollback needed: yes | no
Notes:
```

Do not record passwords, session cookies, CSRF tokens, PayPal private data, R2
keys, presigned URLs, raw webhook payloads, or private payer information.

## Follow-Up Verification

After a production admin user exists:

- verify admin-only API access with the production account;
- verify non-admin users still receive `403` for admin-only APIs;
- avoid approval, rejection, hiding, or previewing real user media unless the
  action is part of an approved operations drill;
- use the admin account for the production R2 media verification only when the
  target test media and owner account are explicitly controlled.

The manual GitHub Actions workflow:

```text
Smoke production admin
```

uses the `production` GitHub Environment and requires these environment
secrets:

- `PRODUCTION_ADMIN_EMAIL`
- `PRODUCTION_ADMIN_PASSWORD`

It verifies:

- unauthenticated admin API requests return `401`;
- disposable non-admin users receive `403`;
- the configured production admin user can read the moderation list.

The workflow does not approve, reject, hide, preview, or otherwise mutate media.

An admin invitation UI can be considered later, after email verification, MFA,
and broader role management exist.
