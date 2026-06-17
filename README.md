# Time Archive

Time Archive is a minimalist web project where users can buy and own specific seconds on a shared media timeline.

The core idea is simple:

```text
1 second = 1 dollar
```

Each purchased second can display owner-submitted media, such as an image or short video. The public experience should feel almost UI-less: visitors enter a fullscreen timeline player, and the media experience becomes the main interface.

## Status

This project is currently in early MVP backend development.

The initial focus is to design a simple but high-integrity system for:

- Timeline playback
- Time range ownership
- Primary purchases
- Payment idempotency
- Media upload and moderation
- Auditability
- Future resale support

## Architecture

Architecture documents are available in:

- [System Architecture](docs/architecture/time-archive-architecture.md)
- [MVP Scope](docs/architecture/mvp-scope.md)
- [Domain Model](docs/architecture/domain-model.md)
- [Transaction Boundaries](docs/architecture/transaction-boundaries.md)
- [Security and Operations](docs/architecture/security-and-operations.md)

## API Contract

The development-stage OpenAPI contract is available at:

- [OpenAPI](docs/api/openapi.yaml)

Current purchase APIs are not production authentication boundaries yet. Request-body `buyerId` is temporary development-stage behavior and must be replaced by authenticated server-side identity before production.

The fake payment webhook API is also development-stage only. Production payment confirmation must use verified provider webhooks.

## Planned Technology Stack

- Kotlin
- Spring Boot
- PostgreSQL
- Flyway
- Docker
- AWS or Cloudflare-backed object storage
- Cloudflare CDN and WAF
- GitHub Actions

Redis, Kafka, and Jenkins may be considered later if concrete operational needs justify them.

## Local Development

Prerequisites:

- JDK 21
- Docker
- Gradle, or the Gradle wrapper after it is generated

Repository layout:

```text
apps/api  Spring Boot backend
apps/web  Next.js frontend
docs      Architecture, API, operation, and implementation documents
scripts   Local verification scripts
```

Start local infrastructure for local JVM development:

```text
docker compose up -d postgres redis minio minio-init
```

PostgreSQL uses a PostgreSQL 18-compatible named volume mounted at `/var/lib/postgresql`.
If an older local `postgres-data` volume exists, it is left untouched and unused.

MinIO provides local S3-compatible object storage:

```text
S3 API: http://localhost:9000
Console: http://localhost:9001
Bucket: time-archive-media
```

Run tests:

```text
cd apps/api
./gradlew test
```

Build the backend:

```text
cd apps/api
./gradlew build
```

Build the Docker image:

```text
docker build -t time-archive-api:local apps/api
```

Run the backend:

```text
cd apps/api
./gradlew bootRun
```

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

The local frontend proxies timeline API reads to the backend. Override the
backend API base URL when needed:

```text
TIME_ARCHIVE_API_BASE_URL=http://localhost:8080 npm run dev
```

Verify the frontend:

```text
cd apps/web
npm run lint
npm run build
```

Start the full local backend stack in Docker:

```text
docker compose up -d --build
```

Check the containerized API:

```text
curl http://localhost:8080/actuator/health
```

Verify the local development-stage purchase flow:

```text
./scripts/verify-local-purchase-flow.sh
```

See [Local Purchase Flow Verification](docs/manual-verification/local-purchase-flow.md) for details.

Verification scripts are maintained as shell scripts. On Windows, run them
through Git Bash.

Verify the local development-stage media upload flow:

```text
./scripts/verify-local-media-upload-flow.sh
```

See [Local Media Upload Flow Verification](docs/manual-verification/local-media-upload-flow.md) for details.

Verify the local development-stage public timeline flow:

```text
./scripts/verify-local-public-timeline-flow.sh
```

See [Local Public Timeline Flow Verification](docs/manual-verification/local-public-timeline-flow.md) for details.

The local application expects PostgreSQL at:

```text
jdbc:postgresql://localhost:5432/time_archive
```

Environment variables can override the default database settings:

```text
TIME_ARCHIVE_DATABASE_URL
TIME_ARCHIVE_DATABASE_USERNAME
TIME_ARCHIVE_DATABASE_PASSWORD
```

Environment variables can override local S3-compatible storage settings:

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
```

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
