# Time Archive

Time Archive is a minimalist web project where users can buy and own specific seconds on a shared media timeline.

The core idea is simple:

```text
1 second = 1 dollar
```

Each purchased second can display owner-submitted media, such as an image or short video. The public experience should feel almost UI-less: visitors enter a fullscreen timeline player, and the media experience becomes the main interface.

## Status

This project is currently in the architecture and planning phase.

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
