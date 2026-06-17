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

Start local infrastructure for local JVM development:

```text
docker compose up -d postgres redis
```

PostgreSQL uses a PostgreSQL 18-compatible named volume mounted at `/var/lib/postgresql`.
If an older local `postgres-data` volume exists, it is left untouched and unused.

Run tests:

```text
gradle test
```

Build the backend:

```text
gradle build
```

Build the Docker image:

```text
docker build -t time-archive-api:local .
```

Run the backend:

```text
gradle bootRun
```

Start the full local backend stack in Docker:

```text
docker compose up -d --build
```

Check the containerized API:

```text
curl http://localhost:8080/actuator/health
```

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
