# Local Public Timeline Flow Verification

This document describes the local development-stage public timeline verification
flow.

Verification scripts are maintained as shell scripts. They run in GitHub Actions
on Ubuntu and on Windows through Git Bash.

The script verifies:

- API health
- purchase reservation and fake payment completion
- ownership record creation
- media upload request creation
- presigned object upload to local MinIO
- upload completion verification
- `UPLOADED` media stays hidden from the public timeline
- admin approval through the development-stage moderation API
- approved media appears in `GET /api/timeline`
- public timeline segments do not expose owner identity, original upload URLs,
  or moderation metadata

## Prerequisites

- Docker
- `curl`
- `python3` or `python`
- Local stack started with API, PostgreSQL, and MinIO

## Start Local Stack

```text
docker compose up -d --build
```

## Run Verification

```text
./scripts/verify-local-public-timeline-flow.sh
```

The script defaults to:

```text
BASE_URL=http://localhost:8080
BUYER_ID=00000000-0000-0000-0000-000000000003
ADMIN_ID=00000000-0000-0000-0000-000000000099
START_SECOND=3000
END_SECOND=3001
UPLOAD_CONTENT_TYPE=image/png
```

If the default range is already owned in your local database, choose another
range:

```text
START_SECOND=3100 END_SECOND=3101 ./scripts/verify-local-public-timeline-flow.sh
```

## Expected Result

The final output should include:

```text
[verify-timeline] Local public timeline flow verification passed
```

## Development-Stage Behavior

The script uses `X-Admin-Id` as a temporary admin identity and approves the
uploaded local object URL as the public media URL. Production behavior must use
authenticated admin identity and a media processing pipeline that publishes
approved derived media.

## Cleanup

```text
docker compose down
```

Use `-v` only when you intentionally want to delete local PostgreSQL and MinIO
volumes:

```text
docker compose down -v
```
