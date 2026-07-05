# Production Runtime Parameters

## Purpose

This runbook defines the production runtime parameter contract for Time Archive
on EC2. It does not contain real secret values and must not be used to record
credentials.

Production runtime parameters live under:

```text
/time-archive/production/
```

The production EC2 instance role reads this path during deployment. GitHub
deployment roles must not read decrypted application secrets.

## Safety Boundaries

- Creating or updating production SSM parameters changes external AWS state and
  requires explicit approval.
- Secret values must use SSM `SecureString`.
- Staging and production must not share R2 buckets, access keys, database
  credentials, rate-limit salts, Cloudflare Tunnel tokens, PayPal credentials,
  or SSM paths.
- Production values must not be stored in repository files, GitHub logs, shell
  history, screenshots, or issue/PR comments.
- Read-only metadata validation may check names and types, but must not decrypt
  or print values.

## Required Parameters

| Name | Type | Source |
| --- | --- | --- |
| `/time-archive/production/aws/region` | `String` | Fixed production AWS region, initially `ap-northeast-2`. |
| `/time-archive/production/cloudwatch/log-group-prefix` | `String` | `/time-archive/production`. |
| `/time-archive/production/database/url` | `String` | Production RDS endpoint, DB name, and `sslmode=require`. |
| `/time-archive/production/database/username` | `SecureString` | Production application or migration-capable DB user selected for the current deployment model. |
| `/time-archive/production/database/password` | `SecureString` | Password for the production DB user. |
| `/time-archive/production/r2/endpoint` | `String` | Cloudflare R2 S3-compatible account endpoint. |
| `/time-archive/production/r2/presigned-url-endpoint` | `String` | Same R2 S3-compatible endpoint unless a reviewed deployment requires another endpoint. |
| `/time-archive/production/r2/public-base-url` | `String` | Private canonical managed-storage base URL stored in database object references. |
| `/time-archive/production/r2/bucket` | `String` | Dedicated production R2 bucket. |
| `/time-archive/production/r2/access-key` | `SecureString` | Least-privilege production R2 access key ID. |
| `/time-archive/production/r2/secret-key` | `SecureString` | Least-privilege production R2 secret access key. |
| `/time-archive/production/rate-limit/key-salt` | `SecureString` | Random production-only HMAC salt. |
| `/time-archive/production/rate-limit/client-ip-header` | `String` | `CF-Connecting-IP` only after Cloudflare Tunnel is confirmed as the only public ingress. |
| `/time-archive/production/cloudflare/tunnel-token` | `SecureString` | Production Cloudflare Tunnel token. |
| `/time-archive/production/paypal/enabled` | `String` | `false` until production PayPal checkout is approved. |
| `/time-archive/production/paypal/api-base-url` | `String` | Approved PayPal API base URL for the live environment. |
| `/time-archive/production/paypal/client-id` | `SecureString` | Production PayPal client ID. |
| `/time-archive/production/paypal/client-secret` | `SecureString` | Production PayPal client secret. |
| `/time-archive/production/paypal/return-url` | `String` | Public HTTPS PayPal approval return URL. |
| `/time-archive/production/paypal/cancel-url` | `String` | Public HTTPS PayPal cancellation return URL. |
| `/time-archive/production/paypal/webhook-id` | `SecureString` | Production PayPal webhook ID used for provider signature verification. |

Production payment collection must not start before the PayPal webhook ID exists
and webhook signature verification passes.

The PayPal integration contract is documented in
[PayPal Integration Design](paypal-integration-design.md), and the launch
procedure is documented in
[Production PayPal Live Setup](production-paypal-live-setup.md). Do not
overload the current fake payment parameters for production.

## Value Shapes

Known production value shapes:

```text
AWS_REGION=ap-northeast-2
TIME_ARCHIVE_CLOUDWATCH_LOG_GROUP_PREFIX=/time-archive/production
TIME_ARCHIVE_DATABASE_URL=jdbc:postgresql://{production-rds-endpoint}:5432/time_archive?sslmode=require
TIME_ARCHIVE_STORAGE_S3_ENDPOINT=https://{cloudflare-account-id}.r2.cloudflarestorage.com
TIME_ARCHIVE_STORAGE_S3_PRESIGNED_URL_ENDPOINT=https://{cloudflare-account-id}.r2.cloudflarestorage.com
TIME_ARCHIVE_STORAGE_S3_REGION=auto
TIME_ARCHIVE_STORAGE_S3_PATH_STYLE_ACCESS=true
TIME_ARCHIVE_RATE_LIMIT_CLIENT_IP_HEADER=CF-Connecting-IP
TIME_ARCHIVE_PAYMENT_PAYPAL_ENABLED=false
TIME_ARCHIVE_PAYPAL_API_BASE_URL=https://api-m.paypal.com
TIME_ARCHIVE_PAYPAL_RETURN_URL=https://time-archive.com/payments/paypal/return
TIME_ARCHIVE_PAYPAL_CANCEL_URL=https://time-archive.com/payments/paypal/cancel
TIME_ARCHIVE_PAYPAL_WEBHOOK_ID=<paypal-live-webhook-id>
```

Generate a production rate-limit salt on a trusted workstation:

```bash
openssl rand -base64 48
```

Do not reuse the staging salt.

## Database User Policy

The current deployment renderer supports one database username and password.
For paid production, the preferred model is to split credentials:

- migration user: schema migration privileges for the controlled deployment
  migration step;
- runtime user: read/write application privileges only;
- administrative user: break-glass operations only.

Until the application and deployment support separate migration/runtime
credentials, using one production database identity remains a release risk and
requires explicit approval. Do not use the RDS master user as the production
application runtime identity.

## Verification

Before production deployment, verify without decrypting values:

- every required parameter exists under `/time-archive/production/`;
- every secret parameter uses `SecureString`;
- non-secret selectors use `String`;
- staging and production bucket names differ;
- staging and production Cloudflare Tunnel tokens differ;
- staging and production rate-limit salts differ;
- staging and production PayPal applications, client secrets, webhook IDs, and
  callback URLs differ;
- production database URL points to the production RDS endpoint;
- production log group prefix is `/time-archive/production`.

The deployment renderer performs value-level validation on the EC2 host by
reading the allowed path with decryption and writing a mode `0600` runtime file.

Validate the committed production fixture and renderer contract locally:

```bash
./scripts/verify-production-runtime-parameters.sh
```

After production parameters are provisioned, validate live SSM metadata without
decryption:

```bash
./scripts/verify-production-runtime-parameters.sh \
  --check-aws \
  --expected-account-id 231851555445 \
  --profile time-archive-staging-admin
```

Use the production-capable operator profile approved for the account. The
script uses `ssm describe-parameters` only; it does not decrypt or print
parameter values.

## Rollback

If a parameter value is wrong, overwrite only the affected parameter and rerun
metadata validation. If a parameter name or type is wrong, delete the incorrect
parameter only after confirming no deployment uses it.

Deleting the entire `/time-archive/production/` path prevents deployment and
requires explicit approval.

## Release Gate

Production secret injection can be marked ready only after:

- production parameters are provisioned with the expected names and types;
- metadata validation passes without value decryption;
- the EC2 instance role is scoped to `/time-archive/production/`;
- the GitHub deploy role cannot read decrypted runtime secrets;
- a production deployment or dry-run confirms runtime rendering without
  printing values.
