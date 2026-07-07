# Production First Deploy Runbook

## Purpose

This runbook defines the first controlled production image publication and
deployment flow for Time Archive.

It does not contain credentials, decrypted SSM values, PayPal private data, R2
secrets, or customer data.

## Current Preconditions

The first production deployment may start only after these are true:

- production CloudFormation stack is `CREATE_COMPLETE`;
- production EC2 is online in SSM;
- production RDS is available and private;
- production application database user bootstrap passed;
- production runtime SSM metadata validation passed;
- production runtime rendering on EC2 passed without printing secret values;
- production image and deployment workflows are merged into `main`;
- GitHub `production` environment approval rules are configured.

At the time this runbook was written, the candidate `main` commit was:

```text
8755e77ce8918aa2528d9c397da7884de84e20b1
```

Use the current `main` full SHA at execution time if it has advanced.

## Required GitHub Repository Variables

These repository variables must exist before running the workflows:

| Name | Expected value source |
| --- | --- |
| `AWS_ACCOUNT_ID` | Production AWS account ID. |
| `AWS_REGION` | `ap-northeast-2`. |
| `AWS_PRODUCTION_IMAGE_PUBLISH_ROLE_ARN` | Production stack output `GitHubImagePublisherRoleArn`. |
| `AWS_PRODUCTION_DEPLOY_ROLE_ARN` | Production stack output `GitHubProductionDeployRoleArn`. |
| `PRODUCTION_INSTANCE_ID` | Production stack output `ApplicationInstanceId`. |

Current known non-secret production outputs:

```text
AWS_ACCOUNT_ID=231851555445
AWS_REGION=ap-northeast-2
PRODUCTION_INSTANCE_ID=i-07b694fcc70b19da8
AWS_PRODUCTION_IMAGE_PUBLISH_ROLE_ARN=arn:aws:iam::231851555445:role/time-archive-production-GitHubImagePublisherRole-YlP7RWNeQEKp
AWS_PRODUCTION_DEPLOY_ROLE_ARN=arn:aws:iam::231851555445:role/time-archive-production-GitHubProductionDeployRole-JdvIKvyrXIt5
```

Do not store production runtime secrets as GitHub variables or secrets. Runtime
secrets are read by the EC2 instance role from SSM during deployment.

## Required Deployment Inputs

The `Deploy production` workflow requires:

| Input | Value |
| --- | --- |
| `image_sha` | Full 40-character Git SHA published by `Publish production images`. |
| `redis_image` | Digest-pinned Redis image reference. |
| `cloudflared_image` | Digest-pinned cloudflared image reference. |
| `public_base_url` | Optional HTTPS production URL for post-deploy checks. Leave empty before Cloudflare production route verification. |

Reviewed third-party image references used in prior deployment verification:

```text
redis:7.4-alpine@sha256:084f4bcb3fedf990ba43d26774f58ed4697a2c044156544ac4717934ad1d57c8
cloudflare/cloudflared:2026.6.1@sha256:d6cca03c300bebbbcfb77381fe8e4c00a8925c6d15fca57f62af5aefb1de6226
```

Re-review these references before execution if they are no longer acceptable or
if security scan results require a newer digest.

## Phase 1: Publish Production Images

Run from GitHub Actions:

1. Open the repository `Actions` tab.
2. Select `Publish production images`.
3. Choose `Run workflow`.
4. Select branch `main`.
5. Run without extra inputs.

Expected behavior:

- workflow runs only on `main`;
- assumes `AWS_PRODUCTION_IMAGE_PUBLISH_ROLE_ARN` through GitHub OIDC;
- builds API and Web for `linux/arm64`;
- pushes only full Git SHA tags to:
  - `time-archive-production-api`;
  - `time-archive-production-web`;
- emits provenance and SBOM attestations;
- verifies production ECR digests;
- fails if only one of the two immutable image tags already exists.

Record from the workflow summary:

```text
Production image publish workflow run:
Commit SHA:
API digest:
Web digest:
Result: PASS | FAIL
Notes:
```

## Phase 2: Review ECR Image State

Before deploying, verify:

- both API and Web production image tags exist for the same full Git SHA;
- ECR image digests are valid `sha256` digests;
- scan findings do not include a launch-blocking critical issue;
- the image SHA is the intended `main` commit.

Do not deploy a partial image publication.

## Phase 3: Deploy Production

Run from GitHub Actions:

1. Open the repository `Actions` tab.
2. Select `Deploy production`.
3. Choose `Run workflow`.
4. Select branch `main`.
5. Fill inputs:
   - `image_sha`: the full published Git SHA;
   - `redis_image`: reviewed digest-pinned Redis reference;
   - `cloudflared_image`: reviewed digest-pinned cloudflared reference;
   - `public_base_url`: leave empty until the production Cloudflare route is
     verified, or set `https://time-archive.com` after route verification.
6. Approve the GitHub `production` environment prompt.

Expected behavior:

- workflow runs only on `main`;
- assumes `AWS_PRODUCTION_DEPLOY_ROLE_ARN` through GitHub OIDC;
- verifies both immutable production image tags exist in ECR;
- sends a deployment bundle to `PRODUCTION_INSTANCE_ID` through SSM Run Command;
- runs `/opt/time-archive/deploy/deploy.sh production`;
- renders production runtime env from SSM on the EC2 host;
- authenticates Docker to ECR;
- pulls immutable API, Web, Redis, cloudflared, and migration images;
- runs Flyway through the migration profile;
- starts API, Web, Redis, and cloudflared;
- runs private deployment health checks;
- records the current release only after verification succeeds.

Record from the workflow summary:

```text
Production deploy workflow run:
Commit SHA:
SSM command ID:
Instance ID:
Result: PASS | FAIL
Notes:
```

## Phase 4: Post-Deploy Private Checks

Before public traffic verification, confirm:

- GitHub workflow result is successful;
- SSM command status is `Success`;
- CloudWatch log groups receive fresh logs:
  - `/time-archive/production/api`;
  - `/time-archive/production/web`;
  - `/time-archive/production/redis`;
  - `/time-archive/production/cloudflared`;
  - `/time-archive/production/migration`;
- API health passed in the deployment output;
- Web health passed in the deployment output;
- Redis health passed in the deployment output;
- no deployment output printed credentials, cookies, CSRF tokens, R2 keys,
  PayPal secrets, or presigned URLs.

If this phase fails, do not configure or open public production traffic.

## Phase 5: Cloudflare Production Routing

After private deployment checks pass, configure production Cloudflare routing:

```text
https://time-archive.com/api/payments/paypal/webhooks -> http://api:8080
https://time-archive.com/*                            -> http://web:3000
```

The exact PayPal webhook route must be ordered before the general Web route and
must bypass cache. The general Web route should be the only browser-facing
entrypoint.

Production Cloudflare checks:

- browser access to `https://time-archive.com` succeeds;
- HTTP redirects to HTTPS if an HTTP route exists;
- security headers are present;
- auth cookies are `Secure`, `HttpOnly`, and `SameSite=Lax`;
- API routes are reachable only through the Web proxy except for the exact
  PayPal webhook route;
- `CF-Connecting-IP` is available to the API runtime through the configured
  Web proxy and runtime header setting.

## Phase 6: Initial Public Smoke Checks

Minimum first-deploy smoke checks:

1. Public Web load:
   - `GET https://time-archive.com/` returns success.
2. Public timeline:
   - `GET https://time-archive.com/api/timeline?from=0&to=1` returns success.
3. Auth:
   - register a production operator account;
   - login;
   - call `/api/me`;
   - logout.
4. Security headers:
   - verify HSTS, frame policy, content sniffing protection, referrer policy,
     minimal CSP, and permissions policy.
5. Logs:
   - search CloudWatch by request ID;
   - confirm no sensitive keyword matches.

Do not run a live PayPal payment before this minimum smoke set passes.

The following manual GitHub Actions workflows automate this minimum set:

| Workflow | Purpose |
| --- | --- |
| `Smoke production public` | Verifies the Web root and public timeline through the production HTTPS hostname. |
| `Smoke production security headers` | Verifies security headers on the Web root and public API proxy path. |
| `Smoke production auth` | Registers a disposable `production-auth-smoke-` user and verifies CSRF rejection, secure session cookies, logout, login, and `/api/me`. |

They default to the repository variable `PRODUCTION_PUBLIC_BASE_URL`. The value
should be:

```text
https://time-archive.com
```

The same value may be supplied through each workflow's optional
`public_base_url` input.

## Phase 7: Production R2 Verification

After public Web and auth checks pass, create or confirm the controlled
production admin user through [Production Admin Provisioning](production-admin-provisioning.md),
then verify production R2:

- bucket is `time-archive-production`;
- bucket is not publicly listable or anonymously readable;
- CORS allows only `https://time-archive.com`;
- owner can create an upload request;
- presigned `PUT` succeeds from the production origin;
- upload completion validates object existence, content type, content length,
  signature, and duration;
- admin preview creates a short-lived presigned URL;
- approved media appears in public timeline with short-lived playback URL;
- hidden or rejected media does not appear in public timeline.

## Phase 8: PayPal Live Drill

Keep `/time-archive/production/paypal/enabled=false` until the production Web,
Cloudflare, R2, logging, and basic smoke checks pass.

When ready, follow [Production PayPal Live Setup](production-paypal-live-setup.md):

1. Set PayPal enabled to `true` in production SSM with explicit approval.
2. Redeploy production.
3. Run the one-second low-value live payment drill.
4. Verify PayPal Dashboard capture completion.
5. Verify signed webhook processing.
6. Verify `payment_events`, `purchases`, and `ownership_records`.
7. Verify refund or explicitly accept refund handling as a launch limitation.

## Failure Handling

### Image Publication Failure

- Do not deploy.
- Check whether API and Web image tags are partial.
- If partial publication exists, investigate before retrying.
- Do not delete production ECR images without explicit approval.

### Deployment Failure Before Migration

- Do not configure production public route.
- Inspect GitHub workflow logs and SSM command output.
- Inspect `/time-archive/production/migration` and service log groups.
- Fix image/runtime/deployment issue and redeploy.

### Migration Failure

- Stop public launch.
- Preserve migration logs, deployment SHA, and SSM command ID.
- Prefer forward migration fix if no customer traffic exists.
- Use database restore only after explicit high-impact approval.

### Service Health Failure

- Do not open public traffic.
- Inspect API, Web, Redis, and cloudflared logs.
- If the previous release exists, use the documented release files to decide
  whether a rollback deployment is possible.
- Otherwise apply a forward fix and redeploy.

### Public Route Failure

- Keep Cloudflare route disabled or restricted.
- Verify Tunnel token, route ordering, Web/API target mapping, and cache bypass.
- Do not change application runtime parameters unless the route issue is proven
  to be runtime configuration.

### PayPal Live Failure

- Set `/time-archive/production/paypal/enabled=false` and redeploy if checkout
  must be disabled.
- Keep webhook processing available if any live captures may still settle.
- Do not delete PayPal, purchase, ownership, audit, or payment event records.

## Pass Criteria

The first production deploy passes when:

- production image publication succeeds for API and Web;
- production deployment workflow succeeds;
- migration and private health checks pass;
- production Cloudflare route is verified;
- basic public smoke checks pass;
- production logs are searchable and do not expose sensitive values;
- production R2 verification passes or is explicitly deferred before media
  launch;
- PayPal Live remains disabled until the dedicated live drill passes.

## Repository-Safe Deployment Record

Record only repository-safe details:

```text
Date:
Operator:
Published Git SHA:
API image digest:
Web image digest:
Redis image reference:
cloudflared image reference:
Deploy workflow run:
SSM command ID:
Production instance ID:
Cloudflare route status:
Public smoke result:
R2 verification result:
PayPal enabled during first deploy: false
Outcome: PASS | FAIL
Notes:
```

Do not record credentials, raw webhook payloads, cookies, CSRF tokens, session
IDs, private payer information, R2 keys, PayPal secrets, or presigned URLs.

## First Production Public Smoke Record

Date: 2026-07-07

Repository-safe result:

```text
Production hostname: https://time-archive.com
Production deployment: completed through SSM after production image publication
Cloudflare production route: configured
Smoke production public: PASS
Smoke production security headers: PASS
Smoke production auth: PASS
Verified scope:
- Web root over HTTPS
- Public timeline over HTTPS
- Security headers on Web and public API proxy paths
- CSRF rejection for mutation without token
- Disposable production auth-smoke registration
- Secure session cookie attributes
- Logout, login, and /api/me
Known remaining gates:
- Production R2 upload, admin preview, and playback verification
- Production PayPal Live low-value payment drill
- Production restore drill
- Production observability/alert verification
```
