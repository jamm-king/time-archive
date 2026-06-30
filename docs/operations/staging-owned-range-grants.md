# Staging Owned Range Grants

## Purpose

This runbook defines how to grant a staging user an owned time range for media
upload and moderation smoke tests without enabling fake payments in staging.

This process is for staging only. Production must rely on real payment provider
flows or separately approved operator remediation procedures.

## Script

Dry-run first:

```bash
./scripts/grant-staging-owned-range.sh \
  --expected-account-id 231851555445 \
  --profile time-archive-staging-admin \
  --email user@example.com \
  --start-second 7000 \
  --end-second 7001 \
  --dry-run
```

After review and approval:

```bash
./scripts/grant-staging-owned-range.sh \
  --expected-account-id 231851555445 \
  --profile time-archive-staging-admin \
  --email user@example.com \
  --start-second 7000 \
  --end-second 7001
```

The script:

- requires the expected AWS account ID;
- allows only `ap-northeast-2`;
- resolves the staging EC2 instance from the `time-archive-staging`
  CloudFormation stack;
- verifies the runtime parameter path is `/time-archive/staging/`;
- sends an `AWS-RunShellScript` command through SSM;
- reads the staging application database username and password from SSM on EC2;
- requires the target user to already exist;
- validates the canonical 24-hour range bounds;
- fails when the requested range overlaps active ownership;
- inserts or reuses an active `ADMIN_GRANT` ownership record for the same user
  and exact range.

The script does not touch purchase or payment tables and does not print
database credentials.

## Operational Notes

Use short, isolated ranges for smoke tests. Record the operator, target email,
range, timestamp, reason, and SSM command id.

The staging media preview smoke workflow expects the authenticated staging admin
account to own the configured range. The current smoke-test reservation is
`[7000, 7001)` for the configured staging admin account; if that account or
range changes, grant a replacement range first and pass the matching workflow
inputs.

If a grant was created incorrectly, revoke the ownership record in a separately
reviewed operation by setting `status = 'REVOKED'` and `valid_until` to the
current timestamp.

## Production Boundary

Do not use staging grants as a production purchase substitute. Production
ownership should normally originate from the real payment provider flow. Any
manual production ownership correction must be treated as a high-impact data
operation with explicit approval, audit trail, and rollback plan.
