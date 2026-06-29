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

## Failure And Rollback

`deploy.sh` stops on image pull, migration, service startup, or health-check
failure. It records the current release only after verification succeeds.

Automated rollback is not implemented. If rollback is required:

1. Inspect `/var/lib/time-archive/deployments/previous.env` on the EC2 host.
2. Rerun deployment with the previous API and Web image references.
3. Prefer forward-fix database migrations. Point-in-time restore is reserved
   for severe data corruption.

## Current Status

The workflow and static validation are implemented. The first staging
deployment has not yet been run by this repository change. The staging
CloudFormation stack must be updated before the first run so the deploy role
has the reviewed ECR image-verification permission.
