# Staging Deployment

## Purpose

This runbook defines the first staging deployment path for Time Archive. The
deployment uses GitHub Actions OIDC, AWS Systems Manager Run Command, ECR
images, the existing EC2 host, and runtime values stored in SSM Parameter
Store.

The workflow is:

```text
.github/workflows/deploy-staging.yml
```

It is manually dispatched and must run from `main`.

## Required GitHub Configuration

Repository variables:

| Variable | Source |
| --- | --- |
| `AWS_ACCOUNT_ID` | Staging AWS account ID. |
| `AWS_REGION` | `ap-northeast-2`. |
| `AWS_STAGING_DEPLOY_ROLE_ARN` | CloudFormation output `GitHubStagingDeployRoleArn`. |
| `STAGING_INSTANCE_ID` | CloudFormation output `ApplicationInstanceId`. |

GitHub Environment:

```text
staging
```

The CloudFormation deploy role trust uses the GitHub OIDC subject
`repo:jamm-king/time-archive:environment:staging`, so the workflow must keep
`environment: staging`.

Before running the workflow for the first time, update the staging
CloudFormation stack from the commit that contains this workflow. The deploy
role must include `ecr:DescribeImages` on the staging API and Web repositories;
otherwise the workflow will fail during immutable image verification before it
can send the SSM command.

## Workflow Inputs

| Input | Requirement |
| --- | --- |
| `image_sha` | Full 40-character Git SHA already published to both staging ECR repositories. |
| `redis_image` | Digest-pinned Redis image reference. |
| `cloudflared_image` | Digest-pinned `cloudflare/cloudflared` image reference. |
| `public_base_url` | Optional HTTPS staging URL for post-deploy smoke checks after Tunnel routing is confirmed. |

Do not use `latest` or mutable tags. Redis and `cloudflared` must use
`@sha256:` digest references.

## Deployment Flow

The workflow:

1. Validates variables, inputs, branch, and immutable image shapes.
2. Assumes the staging deploy role with GitHub OIDC.
3. Verifies both API and Web images exist in ECR for the requested Git SHA.
4. Archives the reviewed `deploy/production` bundle.
5. Sends the bundle and image references through SSM Run Command.
6. On EC2, writes the deployment bundle under `/opt/time-archive/deploy`.
7. Runs `deploy.sh staging`.
8. Pulls API, Web, Redis, and `cloudflared` images.
9. Runs Flyway through the migration profile.
10. Starts API, Web, Redis, and `cloudflared`.
11. Runs private health checks and optional public smoke checks.

The workflow does not read application secrets. The EC2 instance role reads the
`/time-archive/staging/` runtime parameter path during deployment.

## Cloudflare Tunnel

The Tunnel is not considered connected merely because the token exists in SSM.
It becomes active when the `cloudflared` container starts successfully and
connects to Cloudflare with the stored token.

For the first deployment, omit `public_base_url` unless the Cloudflare public
hostname is already routed to the Tunnel. After the public hostname works, rerun
or verify with the selected HTTPS staging hostname.

The staging Tunnel should be exposed as a Cloudflare Published Application, not
as a Private Hostname, Private CIDR, or Workers VPC route. The public hostname
routes browser HTTPS traffic to the private Docker Compose service:

```text
https://staging.time-archive.com -> http://web:3000
```

## Failure And Rollback

`deploy.sh` stops on image pull, migration, service startup, or health-check
failure. It records the current release only after verification succeeds.

Automated rollback is not implemented. Staging rollback drills reuse the manual
`Deploy staging` workflow with the previous immutable image SHA. See
[Staging Rollback Drill](staging-rollback-drill.md).

If rollback is required:

1. Inspect `/var/lib/time-archive/deployments/previous.env` on the EC2 host.
2. Rerun deployment with the previous API and Web image references.
3. Prefer forward-fix database migrations. Point-in-time restore is reserved
   for severe data corruption.

## Current Status

The first staging deployment was completed and verified on 2026-06-29.

Verified deployment image:

```text
813c73b1f2def9f64c8e9bde0115a59db4bd210e
```

Verified runtime state:

- `api`: running and healthy.
- `web`: running and healthy.
- `redis`: running and healthy.
- `cloudflared`: running and connected to Cloudflare.
- API health endpoint returned `UP`.
- The Web root responded from inside the deployment network.
- `cloudflared` registered tunnel connections and passed DNS, UDP, TCP, and
  Cloudflare API connectivity prechecks.
- The Cloudflare Published Application route was configured and browser access
  through the staging HTTPS hostname succeeded.

The workflow can now be rerun with `public_base_url` set to the staging HTTPS
hostname when an automated public smoke check is desired.

## Public Smoke Verification

After the Cloudflare Published Application route is configured, the public
hostname can be verified without redeploying.

From a local shell:

```bash
./scripts/verify-staging-public-smoke.sh \
  --base-url https://staging.time-archive.com
```

From GitHub Actions, run:

```text
Smoke staging public
```

The workflow is manual only, runs from `main`, uses the `staging` GitHub
Environment, and checks only non-mutating public endpoints:

- `GET /`
- `GET /api/timeline?from=0&to=1`

The workflow input `public_base_url` is optional. If omitted, the workflow uses
the repository variable `STAGING_PUBLIC_BASE_URL`.

## Auth Smoke Verification

The deployed HTTPS authentication path can be verified without SSH or AWS
access.

From a local shell:

```bash
./scripts/verify-staging-auth-smoke.sh \
  --base-url https://staging.time-archive.com
```

From GitHub Actions, run:

```text
Smoke staging auth
```

The workflow is manual only, runs from `main`, uses the `staging` GitHub
Environment, and verifies:

- CSRF token retrieval.
- Rejection of a mutation without `X-XSRF-TOKEN`.
- Registration of a disposable smoke-test user.
- Session cookie attributes: `HttpOnly`, `Secure`, and `SameSite=Lax`.
- Authenticated `/api/me` lookup.
- Logout and post-logout rejection.
- Login and final `/api/me` lookup.

This check creates a disposable staging user with a
`staging-auth-smoke-...@example.com` email address. Cleanup is a data-retention
operation and is not part of the smoke workflow.

## Admin Provisioning

Staging admin users are provisioned through an operator-controlled SSM script,
not through an application bootstrap API. See
[Staging Admin Provisioning](staging-admin-provisioning.md).

After provisioning an admin user, run the manual `Smoke staging admin` workflow
to verify deployed admin authorization boundaries.

## Owned Range Grants

Staging media smoke tests can use explicit `ADMIN_GRANT` ownership records
instead of enabling fake payments. See
[Staging Owned Range Grants](staging-owned-range-grants.md).

## Media Preview Smoke Verification

After a staging account has an active owned range, the deployed media upload and
admin original preview path can be verified without SSH or AWS access.

From GitHub Actions, run:

```text
Smoke staging media preview
```

The workflow is manual only, runs from `main`, uses the `staging` GitHub
Environment, and verifies:

- Login using the configured staging admin credentials.
- Lookup of an active owned range, defaulting to `[7000, 7001)`.
- Owned media upload request creation.
- Presigned PUT upload to the configured object store.
- Upload completion and `UPLOADED` media asset creation.
- Admin moderation-list visibility.
- Short-lived admin preview URL creation and download.

This check mutates staging by uploading a smoke-test media object and creating
an `UPLOADED` media asset. It does not approve, reject, hide, publish, or clean
up the media asset.
