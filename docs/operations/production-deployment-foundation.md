# Production Deployment Foundation

## Purpose

This document describes the repository-side deployment foundation for the
selected EC2 and RDS architecture. It does not provision infrastructure and it
does not contain deployable credentials.

The foundation supports both staging and production. Each environment requires
isolated AWS, Cloudflare, R2, database, and SSM resources.

## Files

| File | Responsibility |
| --- | --- |
| `infra/cloudformation/production.yml` | Production AWS resource and IAM definition. |
| `infra/cloudformation/production.parameters.example.json` | Non-secret production CloudFormation placeholder inputs. |
| `scripts/verify-production-cloudformation.sh` | Production CloudFormation schema, architecture-policy, and policy self-test entry point. |
| `deploy/production/docker-compose.yml` | Production service topology and runtime security defaults. |
| `deploy/production/runtime.env.example` | Shell-compatible placeholder contract used by static validation. |
| `deploy/production/ssm-parameters.example.json` | Non-secret SSM response fixture for renderer tests. |
| `deploy/staging/runtime.env.example` | Staging shell-compatible placeholder contract used by static validation. |
| `deploy/staging/ssm-parameters.example.json` | Non-secret staging SSM response fixture for renderer tests. |
| `deploy/production/render-runtime-env.sh` | Fetches an environment SSM path and writes a mode `0600` runtime file. |
| `deploy/production/bootstrap-host.sh` | Prepares an approved Amazon Linux 2023 host. |
| `deploy/production/deploy.sh` | Pulls immutable images, runs Flyway, starts services, and verifies health. |
| `deploy/production/verify-deployment.sh` | Checks private service health and optional public endpoints. |
| `scripts/verify-production-deployment.sh` | Validates shell syntax, Compose policy, fail-fast secrets, and SSM rendering. |
| `scripts/verify-staging-deployment-runtime.sh` | Validates the staging SSM contract and Compose rendering without contacting AWS. |
| `scripts/verify-staging-runtime-parameters.sh` | Validates the staging parameter fixture and optionally verifies live SSM metadata without decryption. |

## Runtime Topology

The production host runs API, Web, Redis, and `cloudflared`. It does not run
PostgreSQL or MinIO. Compose publishes no host ports; Cloudflare Tunnel routes
traffic to Web, and Web proxies API requests over the private Compose network.
Cloudflare terminates browser-facing HTTPS. The Tunnel forwards to
`http://web:3000` on the same-host private Docker network, so the production
host does not run a public TLS listener or require an ACM or Certbot
certificate.

Redis persists append-only data to an encrypted host volume and uses
`noeviction` so session and rate-limit loss is observable instead of silent.
All container logs use the CloudWatch `awslogs` driver.

## Image Policy

- API and Web images must use full 40-character Git SHA tags from the same ECR
  registry.
- Redis and `cloudflared` must use reviewed SHA-256 digest references.
- `latest`, placeholders, and host-side production builds are rejected.
- CI builds both application Dockerfiles for `linux/arm64` because the selected
  EC2 instance is Graviton-based.

The ECR repositories must enable tag immutability when provisioned. The image
push and deployment workflow is intentionally deferred until ECR, GitHub OIDC,
and environment approval rules exist.

## SSM Parameter Contract

Parameters live under one environment-specific path:

```text
/time-archive/{staging|production}/
```

Required names and placeholder value shapes are listed in
the environment fixture files. Database usernames, database passwords, R2
credentials, the rate-limit HMAC salt, and the Cloudflare Tunnel token must be
`SecureString` parameters protected by the environment KMS key when
infrastructure is provisioned.

Staging uses the same renderer and Compose topology as production, but its
runtime contract is tracked separately under `deploy/staging/` so the
`/time-archive/staging/` path, log group prefix, R2 bucket, and Cloudflare
Tunnel values cannot silently drift into production placeholders.

The EC2 instance role should read only its environment path. The renderer uses
`GetParametersByPath` with decryption, rejects missing or duplicate required
values, rejects multiline values, writes through a temporary file, and installs
the final runtime file with mode `0600`.

## Deployment Sequence

1. Supply immutable API, Web, Redis, and `cloudflared` image references to the
   SSM Run Command invocation.
2. Run `deploy.sh staging` or `deploy.sh production` on the managed EC2 host.
3. Render runtime configuration from the environment SSM path.
4. Authenticate Docker to the shared ECR registry.
5. Pull all immutable images.
6. Run the API image as the one-shot `migration` profile with Flyway enabled.
7. Stop immediately if migration fails.
8. Start API, Web, Redis, and `cloudflared` with Flyway disabled in API.
9. Run private health checks and optional public smoke checks.
10. Record the current release only after verification succeeds.

The current script records the previous release image references but does not
automatically roll back a failed deployment. Automated rollback is deferred
until staging has verified the migration and health-check behavior. Database
rollback remains a forward-fix or point-in-time restore decision.

## Local And CI Verification

From Git Bash or Linux:

```bash
./scripts/verify-production-deployment.sh
./scripts/verify-staging-deployment-runtime.sh
./scripts/verify-staging-runtime-parameters.sh
```

This check does not contact AWS, Cloudflare, R2, or ECR. Windows Git Bash cannot
represent POSIX file modes on NTFS, so the exact `0600` assertion runs only on
Linux CI. The renderer still requests mode `0600` on every platform.

CI additionally builds:

```bash
docker buildx build --platform linux/arm64 -f apps/api/Dockerfile apps/api
docker buildx build --platform linux/arm64 -f apps/web/Dockerfile apps/web
```

## Production CloudFormation Foundation

The production CloudFormation foundation mirrors the reviewed staging topology:

- one VPC and Internet Gateway;
- one public outbound-only application subnet;
- two private RDS subnets in distinct Availability Zones;
- one ARM64 Amazon Linux 2023 EC2 host without inbound security-group rules;
- one private Single-AZ PostgreSQL RDS instance;
- immutable ECR repositories for API and Web images;
- CloudWatch log groups, host metrics, and basic alarms;
- an EC2 instance role, a GitHub image-publisher role, and a GitHub production
  deployment role;
- an SNS alert topic with optional email subscription.

Production uses isolated names, CIDR ranges, SSM paths, IAM policy names,
GitHub environment trust, log groups, and RDS resources. It must not share the
staging VPC, RDS instance, runtime SSM path, Cloudflare Tunnel token, R2 bucket,
or PayPal credentials.

Production RDS intentionally strengthens the staging defaults:

- backup retention is at least 7 days;
- deletion protection is enabled;
- automated backups are retained on deletion;
- CloudFormation deletion and replacement policies create snapshots.

The production database master password is read from this prerequisite
bootstrap-only SSM SecureString:

```text
/time-archive/bootstrap/production/database/master-password
```

That bootstrap path is outside the application runtime
`/time-archive/production/*` read boundary. Do not inject the RDS master
credential into the application container.

## Provisioning Boundary

Do not run production deployment until a separately approved infrastructure
change has created and reviewed:

- VPC, subnets, security groups, EC2, RDS, EBS, and KMS resources.
- ECR repositories with immutable tags.
- Environment-scoped IAM instance and GitHub OIDC roles.
- SSM parameters and rotation procedures.
- CloudWatch log groups, retention, metrics, and alarms.
- Cloudflare Tunnel, DNS, TLS, and edge controls.
- Isolated staging and production R2 buckets and access keys.

Creating these resources can incur cost and change external state. The first
production infrastructure operation must create a CloudFormation change set for
review, not execute a stack directly. Review the exact resources, replacements,
IAM policies, public IPv4 assignment, RDS settings, estimated cost, deletion
protection, and snapshot behavior before execution.

Executing the production change set requires explicit project-owner approval.
The same approval boundary applies to production stack deletion because
deletion can create final snapshots and remove ECR repositories, log groups,
alarms, IAM roles, and the production host.

The selected HTTPS boundary and staging verification requirements are defined
in [Cloudflare Tunnel HTTPS](cloudflare-tunnel-https.md).
The AWS staging resource template and change-set approval boundary are defined
in [Staging CloudFormation Foundation](staging-cloudformation-foundation.md).
Staging runtime parameter creation and metadata validation are defined in
[Staging Runtime Parameters](staging-runtime-parameters.md).
The manual SSM Run Command deployment workflow is defined in
[Staging Deployment](staging-deployment.md).
Production runtime parameter requirements are defined in
[Production Runtime Parameters](production-runtime-parameters.md).
Production R2 requirements are defined in
[Production R2 Readiness](production-r2-readiness.md). Storage backend, bucket,
endpoint, and object-reference base URL changes are governed by
[Storage Backend Change Procedure](storage-backend-change-procedure.md).
