# Staging Runtime Parameters

## Purpose

This runbook defines how to prepare the first Time Archive staging runtime
parameters in AWS Systems Manager Parameter Store. It does not contain real
secret values and must not be used as a place to record credentials.

Runtime parameters live under:

```text
/time-archive/staging/
```

The EC2 application role can read this path. The bootstrap-only RDS master
password path remains separate:

```text
/time-archive/bootstrap/staging/database/master-password
```

Do not copy the bootstrap master password into Git, GitHub Actions, shell
history, screenshots, or logs.

## Safety Boundaries

- Parameter creation and updates change external AWS state and require explicit
  operator approval.
- Secret values use `SecureString`.
- Validation must not call `GetParameter` or `GetParametersByPath` with
  decryption unless running on the EC2 host as part of deployment.
- Repository scripts may verify parameter names and types, but they must not
  print parameter values.
- Staging and production must not share R2 buckets, access keys, Cloudflare
  Tunnel tokens, SSM paths, or database credentials.

## Required Parameters

| Name | Type | Source |
| --- | --- | --- |
| `/time-archive/staging/aws/region` | `String` | Fixed value `ap-northeast-2`. |
| `/time-archive/staging/cloudwatch/log-group-prefix` | `String` | Fixed value `/time-archive/staging`. |
| `/time-archive/staging/database/url` | `String` | Staging CloudFormation `DatabaseEndpoint`, port `5432`, DB name `time_archive`, with `sslmode=require`. |
| `/time-archive/staging/database/username` | `SecureString` | Staging database application/migration user. |
| `/time-archive/staging/database/password` | `SecureString` | Password for the staging database application/migration user. |
| `/time-archive/staging/r2/endpoint` | `String` | Cloudflare R2 S3-compatible account endpoint. |
| `/time-archive/staging/r2/presigned-url-endpoint` | `String` | Same R2 endpoint unless a reviewed deployment requires another endpoint. |
| `/time-archive/staging/r2/public-base-url` | `String` | Private canonical storage base used by the app for managed object references. |
| `/time-archive/staging/r2/bucket` | `String` | Dedicated staging R2 bucket. |
| `/time-archive/staging/r2/access-key` | `SecureString` | Least-privilege staging R2 access key ID. |
| `/time-archive/staging/r2/secret-key` | `SecureString` | Least-privilege staging R2 secret access key. |
| `/time-archive/staging/rate-limit/key-salt` | `SecureString` | Random staging-only HMAC salt. |
| `/time-archive/staging/rate-limit/client-ip-header` | `String` | Optional. Omit while empty; the renderer defaults it to empty until Cloudflare client IP propagation is implemented and verified. |
| `/time-archive/staging/cloudflare/tunnel-token` | `SecureString` | Staging Cloudflare Tunnel token. |

## Database User Policy

The current application uses the primary datasource for both runtime queries
and Flyway migrations. Therefore the first staging database user must be able
to run the existing migrations and then serve the application.

For staging only, create one database identity such as:

```text
timearchive_app
```

This user must be able to connect to `time_archive`, create and alter objects
in the target schema, and read/write application tables. Do not use the RDS
master username as the application runtime username unless explicitly approved
as a temporary emergency exception.

Before production, split database identities:

- migration user: schema migration privileges;
- runtime user: application read/write privileges only;
- administrative user: break-glass operations only.

## Value Preparation

Known staging values:

```text
AWS_REGION=ap-northeast-2
TIME_ARCHIVE_CLOUDWATCH_LOG_GROUP_PREFIX=/time-archive/staging
TIME_ARCHIVE_DATABASE_NAME=time_archive
TIME_ARCHIVE_DATABASE_PORT=5432
TIME_ARCHIVE_STORAGE_S3_REGION=auto
TIME_ARCHIVE_STORAGE_S3_PATH_STYLE_ACCESS=true
TIME_ARCHIVE_RATE_LIMIT_CLIENT_IP_HEADER=
```

Get the RDS endpoint from the stack output:

```bash
aws cloudformation describe-stacks \
  --stack-name time-archive-staging \
  --query "Stacks[0].Outputs[?OutputKey=='DatabaseEndpoint'].OutputValue" \
  --output text \
  --region ap-northeast-2
```

The JDBC URL shape is:

```text
jdbc:postgresql://{database-endpoint}:5432/time_archive?sslmode=require
```

Generate a staging rate-limit salt locally on a trusted workstation:

```bash
openssl rand -base64 48
```

Do not paste the generated value into a committed file.

## Create Or Update Parameters

Copy the committed local-input template to the ignored local path:

```bash
cp deploy/staging/runtime-parameters.local.example.json \
  deploy/staging/runtime-parameters.local.json
```

Edit only `deploy/staging/runtime-parameters.local.json` and replace every
placeholder with the real staging value. The real local file is ignored by Git.
Confirm that before entering secrets:

```bash
git check-ignore deploy/staging/runtime-parameters.local.json
```

Validate the local file without contacting AWS:

```bash
./scripts/put-staging-runtime-parameters.sh \
  --validate-only \
  --expected-account-id 231851555445
```

Then create or overwrite the AWS parameters:

```bash
./scripts/put-staging-runtime-parameters.sh \
  --expected-account-id 231851555445 \
  --profile time-archive-staging-admin \
  --region ap-northeast-2
```

The script validates the input against
`deploy/staging/ssm-parameters.example.json`, checks the AWS account, and writes
each non-empty parameter with `ssm put-parameter --overwrite`. The optional
empty `rate-limit/client-ip-header` value is intentionally omitted from SSM.
The script logs only parameter names and types, never values.

## Verification

Local contract validation:

```bash
./scripts/verify-staging-runtime-parameters.sh
```

Read-only AWS metadata validation:

```bash
./scripts/verify-staging-runtime-parameters.sh \
  --check-aws \
  --expected-account-id 231851555445 \
  --region ap-northeast-2
```

This check verifies that all required parameter names exist with the expected
types. It does not decrypt or print values.

The deployment renderer will perform the first value-level check on the EC2
host by reading the path with decryption and writing
`/run/time-archive/runtime.env` with mode `0600`.

## Rollback

If a parameter value is wrong, overwrite only the affected parameter with the
correct value and rerun metadata validation. If a parameter name or type is
wrong, delete the incorrectly named parameter only after confirming that no
deployment is using it.

Deleting the entire `/time-archive/staging/` path prevents application
deployment and requires explicit approval.

## Current Status

The required parameter contract is committed and locally validated. The staging
runtime parameters have been written to SSM in account `231851555445`, region
`ap-northeast-2`. Thirteen non-empty parameters exist under
`/time-archive/staging/`; the optional empty
`rate-limit/client-ip-header` parameter is intentionally omitted so the renderer
defaults it to empty during deployment.

Live SSM metadata validation has passed without decrypting parameter values.
The staging database application/migration user matching the stored database
username and password has been created. See
[Staging Database User](staging-database-user.md).
