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
| `deploy/production/docker-compose.yml` | Production service topology and runtime security defaults. |
| `deploy/production/runtime.env.example` | Shell-compatible placeholder contract used by static validation. |
| `deploy/production/ssm-parameters.example.json` | Non-secret SSM response fixture for renderer tests. |
| `deploy/production/render-runtime-env.sh` | Fetches an environment SSM path and writes a mode `0600` runtime file. |
| `deploy/production/bootstrap-host.sh` | Prepares an approved Amazon Linux 2023 host. |
| `deploy/production/deploy.sh` | Pulls immutable images, runs Flyway, starts services, and verifies health. |
| `deploy/production/verify-deployment.sh` | Checks private service health and optional public endpoints. |
| `scripts/verify-production-deployment.sh` | Validates shell syntax, Compose policy, fail-fast secrets, and SSM rendering. |

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
`ssm-parameters.example.json`. Database passwords, R2 credentials, the
rate-limit HMAC salt, and the Cloudflare Tunnel token must be `SecureString`
parameters protected by the environment KMS key when infrastructure is
provisioned.

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
```

This check does not contact AWS, Cloudflare, R2, or ECR. Windows Git Bash cannot
represent POSIX file modes on NTFS, so the exact `0600` assertion runs only on
Linux CI. The renderer still requests mode `0600` on every platform.

CI additionally builds:

```bash
docker buildx build --platform linux/arm64 -f apps/api/Dockerfile apps/api
docker buildx build --platform linux/arm64 -f apps/web/Dockerfile apps/web
```

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

Creating these resources can incur cost and change external state, so it is
outside this repository-only foundation task.

The selected HTTPS boundary and staging verification requirements are defined
in [Cloudflare Tunnel HTTPS](cloudflare-tunnel-https.md).
The AWS staging resource template and change-set approval boundary are defined
in [Staging CloudFormation Foundation](staging-cloudformation-foundation.md).
