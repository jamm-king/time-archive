# Local Media Upload Flow Verification

This document describes the local development-stage media upload verification
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
- `UPLOADED` `MediaAsset` creation
- idempotent duplicate completion
- owned range media listing

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
./scripts/verify-local-media-upload-flow.sh
```

The script defaults to:

```text
BASE_URL=http://localhost:8080
BUYER_ID=00000000-0000-0000-0000-000000000002
START_SECOND=2000
END_SECOND=2001
UPLOAD_CONTENT_TYPE=image/png
```

If the default range is already owned in your local database, choose another
range:

```text
START_SECOND=2100 END_SECOND=2101 ./scripts/verify-local-media-upload-flow.sh
```

## Expected Result

The final output should include:

```text
[verify-media] Local media upload flow verification passed
```

## Cleanup

```text
docker compose down
```

Use `-v` only when you intentionally want to delete local PostgreSQL and MinIO
volumes:

```text
docker compose down -v
```
