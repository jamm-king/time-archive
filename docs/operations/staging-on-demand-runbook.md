# Staging On-Demand Runbook

## Purpose

This runbook defines how to keep the staging CloudFormation stack available
while stopping expensive compute resources when staging is idle.

The process stops and starts only:

- the staging EC2 application instance;
- the staging RDS PostgreSQL DB instance.

It does not delete the staging stack, RDS storage, backups, EBS volumes, ECR
images, SSM parameters, IAM roles, CloudWatch log groups, alarms, Cloudflare
resources, R2 resources, or GitHub configuration.

## When To Start Staging

Start staging when you need any of the following:

- staging deployment verification;
- PayPal Sandbox webhook or checkout testing;
- staging Cloudflare route testing;
- staging smoke workflows;
- restore drills or migration rehearsals;
- investigation of staging-only issues.

## When To Stop Staging

Stop staging after:

- no staging deployment workflow is running;
- no staging smoke workflow is running;
- no PayPal Sandbox webhook test is in progress;
- no restore or migration drill is in progress;
- relevant staging logs, command IDs, and smoke results have been recorded.

## Cost Notes

Stopping staging reduces EC2 instance-hour and RDS DB instance-hour cost, but it
does not make staging free.

Costs that can remain while staging is stopped include:

- RDS provisioned storage;
- RDS backup storage and snapshots;
- EBS volumes;
- CloudWatch logs and alarms;
- ECR storage;
- R2 storage;
- NAT or data transfer costs if any related resources remain active.

Amazon RDS stopped DB instances can remain stopped for a maximum of seven
consecutive days. If the DB instance is not manually started, RDS can
automatically start it after seven days for maintenance. See the AWS RDS
documentation: <https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_StopInstance.html>.

## Start Staging

Dry run:

```bash
./scripts/start-staging-stack.sh \
  --expected-account-id 231851555445 \
  --profile time-archive-staging-admin \
  --dry-run
```

Start:

```bash
./scripts/start-staging-stack.sh \
  --expected-account-id 231851555445 \
  --profile time-archive-staging-admin
```

The script:

- requires the expected AWS account ID;
- allows only `ap-northeast-2`;
- allows only the `time-archive-staging` CloudFormation stack;
- validates the staging runtime parameter path `/time-archive/staging/`;
- starts `time-archive-staging-postgres` if it is stopped;
- waits for RDS to become `available`;
- starts the staging EC2 application instance if it is stopped;
- waits for EC2 running and status checks.

The script does not deploy images. After start, run the staging deployment
workflow explicitly if the running release must be refreshed.

## Stop Staging

Dry run:

```bash
./scripts/stop-staging-stack.sh \
  --expected-account-id 231851555445 \
  --profile time-archive-staging-admin \
  --dry-run
```

Stop:

```bash
./scripts/stop-staging-stack.sh \
  --expected-account-id 231851555445 \
  --profile time-archive-staging-admin
```

The script:

- requires the expected AWS account ID;
- allows only `ap-northeast-2`;
- allows only the `time-archive-staging` CloudFormation stack;
- validates the staging runtime parameter path `/time-archive/staging/`;
- stops the staging EC2 application instance first;
- waits for EC2 to stop;
- stops `time-archive-staging-postgres`;
- waits for RDS to stop.

The script does not create snapshots, delete resources, or touch production.

## After Starting

Recommended verification:

1. Run `Deploy staging` if a fresh release is required.
2. Run `Smoke staging public`.
3. Run any workflow specific to the change under test, such as auth, security
   headers, admin, media preview, media duration, media signature, presigned
   upload CORS, request ID, or PayPal Sandbox drills.
4. Check CloudWatch logs only for the relevant staging log groups.

## After Stopping

Expected behavior:

- `staging.time-archive.com` is unavailable.
- staging smoke workflows fail until staging is started again.
- Cloudflare Tunnel is disconnected because `cloudflared` runs on the stopped
  EC2 instance.
- RDS storage and backups remain.

## Operations Record Template

Record only repository-safe details:

```text
Date:
Operator:
Action: start | stop
AWS account:
Stack:
EC2 instance ID:
RDS DB instance identifier:
Dry-run result:
Command result:
Follow-up deploy:
Smoke workflows:
Notes:
```

Do not record credentials, session cookies, CSRF tokens, PayPal private data,
R2 keys, presigned URLs, raw webhook payloads, or private payer information.

## Rollback

If start fails:

1. Inspect EC2, RDS, and SSM status.
2. Do not modify production resources.
3. Prefer rerunning start after the AWS resource status reaches a stable state.

If stop fails:

1. Inspect which resource remained active.
2. Do not delete the stack as a shortcut.
3. Retry stop after the resource status reaches `available`, `running`,
   `stopped`, or another stable state.
