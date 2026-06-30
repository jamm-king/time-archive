# Staging Admin Provisioning

## Purpose

This runbook defines how to grant the `ADMIN` role to an existing staging user
without adding an admin bootstrap API, committing secrets, using SSH, or opening
direct database access.

This process is for staging only. Production admin provisioning remains a
separate release gate and must use a stricter operator approval process.

## Recommended Flow

1. Create a normal user through the staging HTTPS application.
2. Confirm the exact email address that should receive `ADMIN`.
3. Run the grant script in dry-run mode.
4. Review the resolved AWS account, EC2 instance, and target email.
5. Run the grant script without `--dry-run` only after explicit approval.
6. Verify the user can access admin-only moderation endpoints.
7. Record the operator, email, timestamp, reason, and SSM command id in the
   project operations log.

## Script

```bash
./scripts/grant-staging-admin-role.sh \
  --expected-account-id 231851555445 \
  --profile time-archive-staging-admin \
  --email user@example.com \
  --dry-run
```

After review and approval:

```bash
./scripts/grant-staging-admin-role.sh \
  --expected-account-id 231851555445 \
  --profile time-archive-staging-admin \
  --email user@example.com
```

The script:

- Requires the expected AWS account ID.
- Allows only `ap-northeast-2`.
- Resolves the staging EC2 instance from the `time-archive-staging`
  CloudFormation stack.
- Verifies the runtime parameter path is `/time-archive/staging/`.
- Sends an `AWS-RunShellScript` command through SSM.
- Reads the staging application database username and password from SSM on EC2.
- Promotes an existing user by exact email match to `ADMIN`.
- Fails if the target user does not exist.
- Is idempotent when the user is already `ADMIN`.

The script does not accept a user password and does not print database
credentials.

## Rollback

If the wrong user is promoted, run a reviewed demotion operation that sets the
same user's role back to `USER`, then record the incident and operator action.
Do not delete the user unless a separate data deletion decision has been
approved.

## Production Boundary

Do not use automatic initial-admin bootstrap for production. For the first MVP,
production admin provisioning should be an explicit operator runbook with:

- documented approval;
- exact target identity;
- SSM or another audited operations channel;
- no web-exposed admin bootstrap endpoint;
- role-change audit logging or an external operations record.

An admin invitation UI can be considered later, after email verification, MFA,
and a broader role-management model exist.
