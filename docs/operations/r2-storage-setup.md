# Cloudflare R2 Storage Setup

This document describes how to run Time Archive locally against Cloudflare R2
instead of MinIO.

Do not commit R2 credentials. Keep access keys in local environment variables,
`.env.r2.local`, or deployment secret management.

## R2 Resource Inputs

- Account ID: supplied by the project owner through local or deployment
  configuration.
- S3 API endpoint:
  `https://replace-with-account-id.r2.cloudflarestorage.com`

Bucket and custom-domain values are environment-specific and must be supplied
through `.env.r2.local` for local verification. Do not share the same bucket between
local development and deployed environments.

Recommended separation:

- Local verification bucket: for example, `time-archive-local`
- Production bucket: for example, `time-archive-prod`

Any production candidate bucket should remain separate from local verification.
Local verification should use a bucket created specifically for local testing.

## Configuration Model

The application uses one S3-compatible storage port for MinIO and R2.

For R2:

- `TIME_ARCHIVE_STORAGE_S3_ENDPOINT` points to the R2 S3 API endpoint.
- `TIME_ARCHIVE_STORAGE_S3_PRESIGNED_URL_ENDPOINT` also points to the R2 S3 API
  endpoint so presigned PUT and GET URLs are signed against the S3-compatible
  API.
- `TIME_ARCHIVE_STORAGE_S3_PUBLIC_BASE_URL` points to the custom media domain.
  This URL is stored as the managed object reference base. Public timeline
  clients do not fetch this URL directly; the API converts approved references
  into short-lived presigned playback URLs.
- `TIME_ARCHIVE_STORAGE_S3_PATH_STYLE_ACCESS` stays `true`.
- `TIME_ARCHIVE_STORAGE_S3_REGION` uses `auto`.

## Data Consistency Boundary

The configured storage base URL is part of persisted application data.

These database fields store storage-specific object references:

- `media_upload_requests.original_file_url`
- `media_assets.original_file_url`
- `media_assets.approved_file_url`
- `media_assets.thumbnail_url`

Changing `TIME_ARCHIVE_STORAGE_S3_BUCKET` or
`TIME_ARCHIVE_STORAGE_S3_PUBLIC_BASE_URL` while keeping the same database can
leave existing rows pointing at objects managed by a previous storage
environment. The current storage adapter only treats URLs under the configured
`TIME_ARCHIVE_STORAGE_S3_PUBLIC_BASE_URL` as managed references. Existing rows
from another storage base may fail admin preview, approval, or public playback
validation.

For local verification, treat storage backend changes as disposable test-data
boundaries:

- When switching from MinIO to R2, from R2 to MinIO, or between R2 buckets,
  prefer starting with a clean local database.
- If the database is not reset, expect old media rows to remain tied to the
  storage backend that created them.
- Do not interpret mixed MinIO and R2 media rows in a local database as a valid
  production migration state.

For deployed environments, storage backend or base URL changes are high-impact
operational changes. Do not change them without an explicit migration plan that
includes object copy, database updates, verification, rollback, and ownership
of any partially migrated data.

## Local Environment Files

Create the ignored base and R2 local environment files:

```text
cp .env.local.example .env.local
cp .env.r2.local.example .env.r2.local
```

Replace every placeholder in both files. The base file contains database,
MinIO, fake payment, and rate-limit local values. The R2 file overrides only
the API storage settings:

```text
TIME_ARCHIVE_STORAGE_S3_ENDPOINT=https://replace-with-account-id.r2.cloudflarestorage.com
TIME_ARCHIVE_STORAGE_S3_PRESIGNED_URL_ENDPOINT=https://replace-with-account-id.r2.cloudflarestorage.com
TIME_ARCHIVE_STORAGE_S3_PUBLIC_BASE_URL=https://replace-with-local-r2-custom-domain
TIME_ARCHIVE_STORAGE_S3_BUCKET=replace-with-local-r2-bucket
TIME_ARCHIVE_STORAGE_S3_REGION=auto
TIME_ARCHIVE_STORAGE_S3_PATH_STYLE_ACCESS=true
TIME_ARCHIVE_STORAGE_S3_ACCESS_KEY=replace-with-r2-access-key-id
TIME_ARCHIVE_STORAGE_S3_SECRET_KEY=replace-with-r2-secret-access-key
```

`.env.local` and `.env.r2.local` are ignored by Git. Their
`*.example` templates are committed without real credentials.

Existing local `.env.r2` values can be copied into
`.env.r2.local`. Do not delete the old file until the new layered
Compose command has been verified.

Use access keys scoped to the local verification bucket. Production access keys
must be managed separately through deployment secret management.

## Cloudflare Dashboard Requirements

Confirm these settings before verification:

- The local verification bucket exists and is not shared with production.
- The access key has permission to read, write, and head objects in the bucket.
- The local media custom domain is connected to the local verification bucket.
- Bucket CORS allows browser uploads from the local web origin if browser UI
  upload is tested:
  - Origin: `http://localhost:3000`
  - Methods: `PUT`, `GET`, `HEAD`
  - Headers: `Content-Type`, `Content-Length`, `x-amz-*`

For shell verification scripts, CORS is not required because they use `curl`
directly against presigned URLs.

Example local browser-upload CORS policy:

```json
[
  {
    "AllowedOrigins": [
      "http://localhost:3000"
    ],
    "AllowedMethods": [
      "PUT",
      "GET",
      "HEAD"
    ],
    "AllowedHeaders": [
      "Content-Type",
      "Content-Length",
      "x-amz-*"
    ],
    "ExposeHeaders": [
      "ETag"
    ],
    "MaxAgeSeconds": 3000
  }
]
```

Add the deployed web origin to `AllowedOrigins` when production or preview
deployments are introduced. Do not use a wildcard origin for authenticated
application flows unless the deployment model is intentionally public and the
risk has been reviewed.

## Start Local Stack With R2

Run:

```text
docker compose \
  --env-file .env.local \
  --env-file .env.r2.local \
  -f docker-compose.yml \
  -f docker-compose.r2.yml \
  up -d --build
```

The default Compose file still starts MinIO, but the API service uses R2 because
the override replaces the storage environment variables.

Check the effective Compose configuration:

```text
docker compose \
  --env-file .env.local \
  --env-file .env.r2.local \
  -f docker-compose.yml \
  -f docker-compose.r2.yml \
  config
```

## Verification

Use non-overlapping second ranges if your local database already owns the
defaults.

Backend-origin media upload:

```text
START_SECOND=7000 END_SECOND=7001 ./scripts/verify-local-media-upload-flow.sh
```

Admin original preview:

```text
START_SECOND=7100 END_SECOND=7101 ./scripts/verify-local-admin-preview-flow.sh
```

Public timeline approved playback:

```text
START_SECOND=7200 END_SECOND=7201 ./scripts/verify-local-public-timeline-flow.sh
```

Web-origin purchase and upload:

```text
START_SECOND=7300 END_SECOND=7301 ./scripts/verify-local-web-purchase-upload-flow.sh
```

## Expected Behavior

- Upload request creation returns a presigned R2 PUT URL.
- Upload completion verifies the object through R2 metadata.
- Completed media stores an original file URL under the configured local
  `TIME_ARCHIVE_STORAGE_S3_PUBLIC_BASE_URL`.
- Admin preview returns a short-lived presigned R2 GET URL.
- Admin approval accepts the stored custom-domain reference because it matches
  the configured `TIME_ARCHIVE_STORAGE_S3_PUBLIC_BASE_URL`.
- Public timeline returns a short-lived presigned R2 playback URL, not the
  stored custom-domain reference.

## Troubleshooting

`SignatureDoesNotMatch`:

- Confirm access key and secret belong to the same Cloudflare account.
- Confirm endpoint is
  `https://replace-with-account-id.r2.cloudflarestorage.com`.
- Confirm region is `auto`.
- Confirm path-style access is enabled.

`AccessDenied`:

- Confirm the access key has bucket read/write permissions.
- Confirm the bucket name matches `TIME_ARCHIVE_STORAGE_S3_BUCKET`.

Upload works in scripts but not in browser:

- Confirm R2 bucket CORS allows `http://localhost:3000`.
- Confirm `PUT` and required headers are allowed.
- If the browser upload request remains `REQUESTED` in the database and R2
  returns `CORS not configured for this bucket` to an `OPTIONS` preflight,
  configure the bucket CORS policy before retrying the browser upload.

Approval fails with `INVALID_MEDIA_STORAGE_REFERENCE`:

- Confirm `TIME_ARCHIVE_STORAGE_S3_PUBLIC_BASE_URL` matches the bucket custom
  domain used by the current environment.
- Confirm stored media URLs start with that exact base URL.

Public timeline download fails:

- Confirm the object exists in R2.
- Confirm the API is using the R2 override.
- Confirm the returned `mediaUrl` is a presigned R2 URL and has not expired.
