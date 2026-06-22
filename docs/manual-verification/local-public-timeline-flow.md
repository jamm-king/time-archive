# Local Public Timeline Flow Verification

This document describes the local development-stage public timeline verification
flow.

Verification scripts are maintained as shell scripts. They run in GitHub Actions
on Ubuntu and on Windows through Git Bash.

The script verifies:

- API health
- CSRF token bootstrap
- purchase reservation and fake payment completion
- ownership record creation
- media upload request creation
- presigned object upload to local MinIO
- upload completion verification
- `UPLOADED` media stays hidden from the public timeline
- admin approval through an authenticated admin session
- approved media appears in `GET /api/timeline` through a short-lived
  presigned playback URL
- the presigned playback URL downloads the uploaded object bytes from local
  MinIO
- public timeline segments do not expose owner identity, original upload URLs,
  stored approved object references, or moderation metadata

## Prerequisites

- Docker
- `curl`
- `python3` or `python`
- Local stack started with API, PostgreSQL, and MinIO

## Start Local Stack

Create `.env.local` from `.env.local.example` and replace
its placeholders.

```text
docker compose --env-file .env.local up -d --build
```

## Run Verification

```text
./scripts/verify-local-public-timeline-flow.sh
```

The script defaults to:

```text
BASE_URL=http://localhost:8080
ADMIN_EMAIL=admin@time-archive.local
ADMIN_PASSWORD=password123
START_SECOND=3000
END_SECOND=3001
UPLOAD_CONTENT_TYPE=image/png
```

The default Docker Compose API service configures `ADMIN_EMAIL` as an initial
admin email through `TIME_ARCHIVE_INITIAL_ADMIN_EMAILS`.

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

The script registers or logs in an admin user through the session authentication
API and approves the uploaded local object URL as the stored approved media
reference. The public timeline response must not return that stored reference
directly; it returns a short-lived presigned playback URL instead. Production
behavior still needs a media processing pipeline that publishes approved
derived media.

## Cleanup

```text
docker compose --env-file .env.local down
```

Use `-v` only when you intentionally want to delete local PostgreSQL and MinIO
volumes:

```text
docker compose --env-file .env.local down -v
```
