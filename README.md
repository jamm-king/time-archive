# Time Archive

Time Archive is a minimalist web project where users can buy and own specific
seconds on a shared media timeline.

```text
1 second = 1 dollar
```

Each purchased second can display owner-submitted media after admin approval.
The public experience is a fullscreen timeline player, while purchase,
ownership management, upload, and moderation remain secondary flows.

## Current Status

This repository is a local MVP. It is suitable for local development, CI
verification, and private demos. It is not ready for a public or paid production
launch until the release blockers are resolved.

Implemented MVP areas:

- One canonical 24-hour archive with 86,400 seconds.
- Server-side session authentication.
- CSRF protection for browser mutation APIs.
- Time range reservation and local fake checkout.
- Idempotent local fake payment completion.
- Current-user owned range reads.
- Owned media upload through S3-compatible presigned PUT URLs.
- Upload completion verification against object storage metadata.
- Admin media moderation with role-based authorization.
- Admin moderation audit logging.
- Admin original media preview through short-lived presigned GET URLs.
- Public timeline reads with approved-media presigned playback URLs.
- Next.js fullscreen player and local web proxy routes.
- Docker Compose local stack.
- GitHub Actions CI and local shell verification scripts.
- OpenAPI validation.
- Redis-backed application rate limiting.
- Externalized local secrets and an isolated local R2 verification path.

Production blockers:

- PayPal integration and signature-verified webhooks.
- Production Cloudflare R2 provisioning and staging verification.
- Production SSM Parameter Store injection and IAM controls.
- RDS provisioning, backup, restore, and migration procedures.
- Cloudflare edge abuse controls and trusted client-address propagation.
- Observability, error tracking, and alerting.
- Media signature validation, malware scanning, and production media
  processing policy.

See [Release Readiness Checklist](docs/operations/release-readiness-checklist.md)
for the release gate.

## Architecture And Operations

- [System Architecture](docs/architecture/time-archive-architecture.md)
- [MVP Scope](docs/architecture/mvp-scope.md)
- [Domain Model](docs/architecture/domain-model.md)
- [Transaction Boundaries](docs/architecture/transaction-boundaries.md)
- [Security and Operations](docs/architecture/security-and-operations.md)
- [CI/CD and Testing Strategy](docs/operations/ci-cd-and-testing-strategy.md)
- [EC2 and RDS Deployment Architecture](docs/operations/ec2-rds-deployment-architecture.md)
- [Release Readiness Checklist](docs/operations/release-readiness-checklist.md)
- [Cloudflare R2 Storage Setup](docs/operations/r2-storage-setup.md)

## API Contract

The development-stage OpenAPI contract is available at:

- [OpenAPI](docs/api/openapi.yaml)

Validate it locally with:

```text
./scripts/verify-openapi.sh
```

The validation script runs Redocly CLI through Docker.

## Technology Stack

- Kotlin
- Spring Boot
- PostgreSQL
- Flyway
- Redis-backed server sessions
- Next.js
- Docker and Docker Compose
- S3-compatible object storage: MinIO locally, Cloudflare R2 selected for
  deployed environments
- GitHub Actions

Kafka and Jenkins are not used in the MVP. They should only be added if a
concrete operational need appears.

## Repository Layout

```text
apps/api  Spring Boot backend
apps/web  Next.js frontend
docs      Architecture, API, operations, and implementation documents
scripts   Local verification scripts
```

## Quick Start With Docker Compose

Prerequisites:

- Docker
- `curl`
- `python3` or `python`
- Git Bash on Windows for shell verification scripts

Create the ignored local environment file and replace every placeholder:

```text
cp .env.local.example .env.local
```

Start the full local stack:

```text
docker compose --env-file .env.local up -d --build
```

Local services:

```text
Web:      http://localhost:3000
API:      http://localhost:8080
MinIO:    http://localhost:9001
Postgres: localhost:5432
Redis:    localhost:6379
```

Health and smoke checks:

```text
curl http://localhost:8080/actuator/health
curl http://localhost:3000
curl "http://localhost:3000/api/timeline?from=0&to=1"
```

Stop the stack:

```text
docker compose --env-file .env.local down
```

Delete local volumes only when intentionally resetting local data:

```text
docker compose --env-file .env.local down -v
```

## Environment And Secret Files

Committed files:

- `.env.local.example`: local PostgreSQL, MinIO, fake payment, and
  rate-limit placeholders.
- `.env.r2.local.example`: local R2 override placeholders.

Ignored files:

- `.env.local`: real local base values.
- `.env.r2.local`: real local R2 values.

Docker Compose receives variables through explicit `--env-file`
arguments and maps only named variables into containers. Sensitive Spring
configuration has no committed runtime fallback and fails fast when missing.

Do not create or commit a repository `.env.prod`. Production secrets
must come from AWS Secrets Manager, SSM Parameter Store, or the deployment
platform's secret facility. If production Compose is eventually used on a
host, keep its env file outside the checkout and restrict its filesystem
permissions.

## Local JVM And Web Development

Start only local infrastructure for JVM/web development:

```text
docker compose --env-file .env.local up -d postgres redis minio minio-init
```

Run backend tests:

```text
cd apps/api
./gradlew test
```

Run the backend:

```text
set -a
source .env.local
set +a
cd apps/api
./gradlew bootRun
```

Fake payment is disabled by default. Enable it only for local development or
CI through `.env.local`. Docker Compose and Spring Boot fail fast when
required database, storage, or rate-limit secrets are missing.

Install frontend dependencies:

```text
cd apps/web
npm install
```

Run the frontend:

```text
cd apps/web
npm run dev
```

The local frontend proxies API requests to the backend. Override the backend
base URL when needed:

```text
TIME_ARCHIVE_API_BASE_URL=http://localhost:8080 npm run dev
```

Verify the frontend:

```text
cd apps/web
npm run lint
npm run build
```

## Verification Scripts

Verification scripts are maintained as shell scripts. On Windows, run them
through Git Bash.

OpenAPI:

```text
./scripts/verify-openapi.sh
```

Backend-origin flows:

```text
./scripts/verify-local-purchase-flow.sh
./scripts/verify-local-media-upload-flow.sh
./scripts/verify-local-public-timeline-flow.sh
./scripts/verify-local-admin-preview-flow.sh
```

Web-origin flows:

```text
./scripts/verify-local-auth-flow.sh
./scripts/verify-local-auth-owned-ranges-flow.sh
./scripts/verify-local-web-purchase-flow.sh
./scripts/verify-local-web-purchase-upload-flow.sh
```

Manual verification documents:

- [Local Purchase Flow Verification](docs/manual-verification/local-purchase-flow.md)
- [Local Media Upload Flow Verification](docs/manual-verification/local-media-upload-flow.md)
- [Local Public Timeline Flow Verification](docs/manual-verification/local-public-timeline-flow.md)
- [Local Admin Preview Flow Verification](docs/manual-verification/local-admin-preview-flow.md)

If a script fails because the default time range is already owned in your local
database, rerun it with another range:

```text
START_SECOND=4300 END_SECOND=4301 ./scripts/verify-local-public-timeline-flow.sh
```

## Authentication, CSRF, And Admin Users

The API uses server-side session authentication. Purchase, owned media, and
admin moderation APIs derive identity from the authenticated session.

Browser clients should fetch a CSRF token through:

```text
GET /api/csrf
```

Mutating browser requests must echo the token through:

```text
X-XSRF-TOKEN
```

The local fake payment webhook is CSRF-exempt because it models a
server-to-server provider callback. It is registered only when
`TIME_ARCHIVE_PAYMENT_FAKE_ENABLED=true`. Fake payment is disabled by default
and must not be enabled in production.

Initial admin accounts can be bootstrapped by setting normalized email
addresses before registration:

```text
TIME_ARCHIVE_INITIAL_ADMIN_EMAILS=admin@time-archive.local
```

The default Docker Compose setup uses:

```text
ADMIN_EMAIL=admin@time-archive.local
ADMIN_PASSWORD=password123
```

Production admin provisioning must be operator-controlled; local bootstrap is
not a production admin lifecycle strategy.

## Rate Limiting

The API uses Redis-backed fixed-window rate limits for authentication, public
timeline and availability reads, purchase operations, owned media mutations,
and admin moderation routes.

Rate limiting is enabled by default. Protected requests fail closed with
`RATE_LIMIT_UNAVAILABLE` when Redis cannot evaluate the counter.
Exceeded requests return `429 RATE_LIMIT_EXCEEDED`,
`Retry-After`, and rate-limit metadata headers.

Client identity uses the authenticated user ID for protected user operations
and an HMAC-SHA256 digest of the network address for unauthenticated requests.
All API instances in an environment must share a strong
`TIME_ARCHIVE_RATE_LIMIT_KEY_SALT` value supplied through secret
management. A
forwarded client IP header is trusted only when explicitly configured:

```text
TIME_ARCHIVE_RATE_LIMIT_CLIENT_IP_HEADER=CF-Connecting-IP
```

Configure this header only when direct origin access is blocked and the named
reverse proxy controls the header. The local Docker Compose stack raises the
registration limit so all verification scripts can run sequentially.

Rate-limit environment variables:

```text
TIME_ARCHIVE_RATE_LIMIT_ENABLED
TIME_ARCHIVE_RATE_LIMIT_CLIENT_IP_HEADER
TIME_ARCHIVE_RATE_LIMIT_KEY_SALT
TIME_ARCHIVE_RATE_LIMIT_REGISTRATION_LIMIT
TIME_ARCHIVE_RATE_LIMIT_REGISTRATION_WINDOW
TIME_ARCHIVE_RATE_LIMIT_LOGIN_LIMIT
TIME_ARCHIVE_RATE_LIMIT_LOGIN_WINDOW
TIME_ARCHIVE_RATE_LIMIT_PUBLIC_READ_LIMIT
TIME_ARCHIVE_RATE_LIMIT_PUBLIC_READ_WINDOW
TIME_ARCHIVE_RATE_LIMIT_PURCHASE_LIMIT
TIME_ARCHIVE_RATE_LIMIT_PURCHASE_WINDOW
TIME_ARCHIVE_RATE_LIMIT_MEDIA_MUTATION_LIMIT
TIME_ARCHIVE_RATE_LIMIT_MEDIA_MUTATION_WINDOW
TIME_ARCHIVE_RATE_LIMIT_ADMIN_LIMIT
TIME_ARCHIVE_RATE_LIMIT_ADMIN_WINDOW
```

## Object Storage

Local development uses MinIO as S3-compatible object storage:

```text
S3 API:  http://localhost:9000
Console: http://localhost:9001
Bucket:  time-archive-media
```

Media policy:

- Upload requests issue short-lived presigned PUT URLs.
- Upload completion verifies object existence, expected content length,
  expected content type, ownership, and expiration.
- Admin original preview uses short-lived presigned GET URLs.
- Admin approval only accepts storage references that belong to the configured
  storage base URL.
- Public timeline responses return short-lived presigned playback URLs and
  `Cache-Control: no-store`.

Storage environment variables:

```text
TIME_ARCHIVE_STORAGE_S3_ENDPOINT
TIME_ARCHIVE_STORAGE_S3_PRESIGNED_URL_ENDPOINT
TIME_ARCHIVE_STORAGE_S3_PUBLIC_BASE_URL
TIME_ARCHIVE_STORAGE_S3_BUCKET
TIME_ARCHIVE_STORAGE_S3_REGION
TIME_ARCHIVE_STORAGE_S3_ACCESS_KEY
TIME_ARCHIVE_STORAGE_S3_SECRET_KEY
TIME_ARCHIVE_STORAGE_S3_PATH_STYLE_ACCESS
TIME_ARCHIVE_STORAGE_S3_UPLOAD_URL_EXPIRATION_SECONDS
TIME_ARCHIVE_STORAGE_S3_PREVIEW_URL_EXPIRATION_SECONDS
TIME_ARCHIVE_STORAGE_S3_PLAYBACK_URL_EXPIRATION_SECONDS
```

Cloudflare R2 should be connected after local MinIO upload, preview,
moderation, and public playback flows remain stable. See the R2 checklist in
[Release Readiness Checklist](docs/operations/release-readiness-checklist.md).
Local R2 verification and deployed environments must use separate buckets and
separate access keys. The local R2 verification path is documented in
[Cloudflare R2 Storage Setup](docs/operations/r2-storage-setup.md).

## Database

Local PostgreSQL defaults:

```text
jdbc:postgresql://localhost:5432/time_archive
```

Database environment variables:

```text
TIME_ARCHIVE_DATABASE_URL
TIME_ARCHIVE_DATABASE_USERNAME
TIME_ARCHIVE_DATABASE_PASSWORD
```

PostgreSQL uses a PostgreSQL 18-compatible named volume mounted at
`/var/lib/postgresql`. Older local `postgres-data` volumes are left untouched
and unused.

Production requires automated backups, point-in-time recovery, and a tested
restore procedure before public launch.

## Project Owner Tasks

The following tasks require project-owner decisions, external accounts, or
production credentials:

- Create PayPal Sandbox and production applications and provide their
  environment-specific credentials through secret management.
- Decide payment capture/refund/dispute policy.
- Create isolated staging and production Cloudflare R2 buckets, access keys,
  CORS rules, and environment values.
- Provide the AWS account, domain names, alert destinations, and approved
  maintenance windows required to provision the selected EC2, RDS, SSM, and
  CloudWatch architecture.
- Configure domain, DNS, TLS, and Cloudflare security settings.
- Create the Sentry organization and project for the selected Developer plan.
- Decide media safety policy: file signature validation, malware scanning,
  transcoding, and thumbnail generation.
- Decide admin provisioning policy beyond local bootstrap.
- Decide backup retention and restore requirements.

These items should be handled before a paid or public launch.

## Development Principles

- Follow Hexagonal Architecture.
- Keep domain logic independent from frameworks and infrastructure.
- Treat ownership and payment records as high-integrity data.
- Use explicit transaction boundaries.
- Make payment and ownership operations idempotent.
- Require moderation before user-uploaded media becomes public.
- Prefer clarity over cleverness.

## License

Time Archive is open source under the [MIT License](LICENSE).
