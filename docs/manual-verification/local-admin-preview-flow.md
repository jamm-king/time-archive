# Local Admin Preview Flow Verification

This document describes the local development-stage admin original media
preview verification flow.

Verification scripts are maintained as shell scripts. They run in GitHub
Actions on Ubuntu and on Windows through Git Bash.

The script verifies:

- API health
- CSRF token bootstrap
- purchase reservation and fake payment completion
- ownership record creation
- media upload request creation
- presigned object upload to local MinIO
- upload completion verification
- admin authentication through the configured initial admin email
- admin preview URL creation for the uploaded original media
- preview URL download returns the same bytes that were uploaded

## Prerequisites

- Docker
- `curl`
- `cmp`
- `python3` or `python`
- Local stack started with API, PostgreSQL, Redis, and MinIO

## Start Local Stack

```text
docker compose up -d --build
```

## Run Verification

```text
./scripts/verify-local-admin-preview-flow.sh
```

The script defaults to:

```text
BASE_URL=http://localhost:8080
ADMIN_EMAIL=admin@time-archive.local
ADMIN_PASSWORD=password123
START_SECOND=6000
END_SECOND=6001
UPLOAD_CONTENT_TYPE=image/png
```

The default Docker Compose API service configures `ADMIN_EMAIL` as an initial
admin email through `TIME_ARCHIVE_INITIAL_ADMIN_EMAILS`.

If the default range is already owned in your local database, choose another
range:

```text
START_SECOND=6100 END_SECOND=6101 ./scripts/verify-local-admin-preview-flow.sh
```

## Expected Result

The final output should include:

```text
[verify-admin-preview] Local admin preview flow verification passed
```

## Development-Stage Behavior

The script does not make the original object public. It requests an admin-only
short-lived preview URL and verifies that the URL can download the private
uploaded object bytes from local MinIO.

## Cleanup

```text
docker compose down
```

Use `-v` only when you intentionally want to delete local PostgreSQL, Redis, and
MinIO volumes:

```text
docker compose down -v
```
