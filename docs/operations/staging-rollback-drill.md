# Staging Rollback Drill

## Purpose

This runbook defines the staging rollback drill for Time Archive. The drill
verifies that operators can inspect the current and previous release metadata,
redeploy the previous API and Web images through the existing deployment
workflow, run smoke checks, and then perform forward recovery to the original
release.

This is an image rollback drill. Database rollback is out of scope. If a
deployment includes a non-backward-compatible migration or data corruption, use
a separately approved database recovery procedure instead of this drill.

## Preconditions

- The staging deployment has completed at least twice, so both `current.env` and
  `previous.env` exist under `/var/lib/time-archive/deployments`.
- The previous API and Web images still exist in staging ECR.
- The Redis and `cloudflared` image references are digest-pinned.
- The staging public hostname is reachable through Cloudflare Tunnel.
- The staging smoke workflows are green before starting the drill.

## Inspect Release State

From a local shell with the staging bootstrap or deploy AWS profile:

```bash
./scripts/inspect-staging-release-state.sh \
  --expected-account-id <staging-account-id> \
  --profile <staging-profile>
```

The script sends a read-only SSM command to the staging EC2 instance and prints
only release metadata from:

- `current.env`
- `previous.env`

Expected fields:

- `TIME_ARCHIVE_ENVIRONMENT`
- `TIME_ARCHIVE_API_IMAGE`
- `TIME_ARCHIVE_WEB_IMAGE`
- `TIME_ARCHIVE_REDIS_IMAGE`
- `TIME_ARCHIVE_CLOUDFLARED_IMAGE`
- `TIME_ARCHIVE_DEPLOYED_AT`

## Roll Back To Previous Images

Use the existing manual GitHub Actions workflow:

```text
Deploy staging
```

Run it from `main` with:

| Input | Value |
| --- | --- |
| `image_sha` | The 40-character Git SHA tag from the previous API and Web image references. |
| `redis_image` | The digest-pinned Redis image reference from `current.env` unless the previous release explicitly used a different approved digest. |
| `cloudflared_image` | The digest-pinned `cloudflared` image reference from `current.env` unless the previous release explicitly used a different approved digest. |
| `public_base_url` | The staging HTTPS hostname. |

The previous API and Web image references must point to the same Git SHA. If
they do not, stop the drill and investigate before deploying.

## Verify Rollback

After `Deploy staging` succeeds, run:

```text
Smoke staging public
Smoke staging auth
Smoke staging admin
Smoke staging media preview
```

Record:

- Deployment workflow run URL.
- SSM command id.
- Rolled-back image SHA.
- Smoke workflow run URLs.
- Any manual browser observations.

## Forward Recovery

After rollback verification, redeploy the original release through:

```text
Deploy staging
```

Use the original `current.env` API/Web image SHA and the same reviewed
digest-pinned Redis and `cloudflared` image references. Then rerun the same
smoke workflows.

The drill is not complete until forward recovery succeeds and staging is back on
the intended release.

## Completed Drill Log

### 2026-06-30

The first staging rollback drill completed successfully.

Release state before rollback:

- Current image SHA:
  `1fe77be2beb27c59487529aa1007eac68648b808`.
- Previous image SHA:
  `813c73b1f2def9f64c8e9bde0115a59db4bd210e`.
- Redis image remained digest-pinned.
- `cloudflared` image remained digest-pinned.

Drill result:

- Rolled back staging to
  `813c73b1f2def9f64c8e9bde0115a59db4bd210e` through `Deploy staging`.
- Ran the required staging smoke workflows after rollback.
- Forward recovered staging to
  `1fe77be2beb27c59487529aa1007eac68648b808` through `Deploy staging`.
- Ran the required staging smoke workflows after forward recovery.

Outcome:

- Staging image rollback and forward recovery are verified.
- Database rollback remains out of scope for this drill.

## Abort Conditions

Stop and escalate instead of continuing when:

- `previous.env` is missing.
- The previous API and Web image references do not share one Git SHA.
- The previous images are missing from ECR.
- A deployment failure occurs during rollback.
- Any required smoke workflow fails after rollback.
- The target release includes a migration that is not backward-compatible with
  the currently deployed database schema.

## Production Boundary

Do not apply this staging drill directly to production. Production rollback
requires explicit approval, incident context, backup or restore readiness, and a
clear decision on whether image rollback is sufficient or database recovery is
required.
