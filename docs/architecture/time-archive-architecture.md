# Time Archive Architecture

## Purpose

Time Archive is a minimalist web product where users can buy and own specific seconds on a shared media timeline. The public experience should feel almost UI-less: a fullscreen timeline player is the primary interface, while purchase, ownership management, moderation, and resale flows remain secondary.

Although the product appears simple, the backend must behave like a high-integrity ownership and transaction system. Time ranges are scarce assets, payments may be retried or duplicated, user media must be moderated, and ownership history must remain auditable.

## Architectural Priorities

1. Ownership integrity
2. Payment idempotency
3. Moderation safety
4. Timeline read performance
5. Minimal user experience
6. Admin auditability
7. Resale marketplace support

## Recommended Initial Stack

### Backend

- Kotlin
- Spring Boot
- Spring Web
- Spring Security
- Spring Validation
- Spring Data JDBC or JPA
- Flyway

Spring Data JDBC is a good initial fit when explicit aggregate persistence is preferred. JPA is acceptable if the team is already comfortable with its lifecycle behavior. jOOQ can be considered later if PostgreSQL-specific range queries, locks, and reporting queries become central.

### Database

- PostgreSQL as the primary source of truth

PostgreSQL is preferred over MySQL because Time Archive benefits from strong transaction behavior, advanced constraints, range modeling, partial indexes, JSONB, row-level locking, and mature operational tooling.

### Cache and Coordination

- Redis is used for server-side web sessions in the MVP.
- Redis may also be used later for cache, rate limiting, short-lived reservation helpers, and lightweight distributed coordination.
- Do not use Redis as the final source of truth for ownership integrity.

### Storage and CDN

- AWS S3 or Cloudflare R2 for object storage
- Cloudflare or CloudFront for CDN delivery
- Private original uploads
- Public or CDN-controlled approved media delivery

### Frontend

- Next.js
- React
- TypeScript
- Tailwind CSS
- Fullscreen media-first UI
- CDN-cached static assets and timeline manifests where possible

The frontend lives under `apps/web`. The initial rendering strategy is
CSR-first for the fullscreen player, while Next.js still allows future static or
server-rendered routes for share, legal, and informational pages.

During local development, the web app can proxy public timeline reads through a
same-origin Next.js route handler to avoid browser CORS coupling between the web
dev server and the backend API.

### CI/CD and Infrastructure

- GitHub Actions for CI/CD
- Docker for packaging
- AWS ECS/Fargate or EC2 for initial deployment
- AWS RDS PostgreSQL
- Cloudflare for DNS, WAF, CDN, and bot protection

Jenkins is not recommended for the initial MVP unless there is a specific operational requirement such as on-premise builds, complex approval workflows, or an existing Jenkins platform.

### Eventing

- Do not introduce Kafka for the MVP.
- Use the transactional outbox pattern first.
- Kafka can be introduced later if multiple independent consumers, high-volume event processing, replay, or data pipeline needs justify it.

## High-Level System Shape

```text
Browser
  - Fullscreen timeline player
  - Purchase and owner flows
  - Admin moderation UI

Cloudflare
  - DNS
  - CDN
  - WAF
  - Bot protection

Backend API
  - Kotlin / Spring Boot
  - Hexagonal Architecture
  - REST APIs
  - Authentication and authorization

PostgreSQL
  - Ownership records
  - Purchases
  - Reservations
  - Media assets
  - Offers
  - Transactions
  - Audit logs
  - Outbox events

Object Storage
  - Original uploads
  - Approved media
  - Thumbnails

Background Workers
  - Media processing
  - Outbox event dispatch
  - Manifest invalidation
  - Reservation expiration

Payment Provider
  - Checkout
  - Webhooks
  - Payment event verification
```

The current MVP implementation contains a fake outbound payment adapter for local checkout foundation work. It preserves the `PaymentPort` boundary and must be replaced by a real provider adapter before production payment collection.

## Hexagonal Architecture

The domain layer must not depend on Spring, databases, object storage, payment providers, or other adapters. Domain rules should be expressed through entities, value objects, domain services, and ports.

### Layers

```text
domain
  - Entities
  - Value objects
  - Domain services
  - Ports
  - Domain exceptions

application
  - Use cases
  - Transaction orchestration
  - Idempotency orchestration
  - DTO mapping for inbound and outbound boundaries

adapter-in
  - REST controllers
  - Admin controllers
  - Payment webhook controllers

adapter-out
  - PostgreSQL repositories
  - Payment provider adapter
  - Object storage adapter
  - Email or notification adapter
  - Media processing adapter
  - Audit log adapter

configuration
  - Spring configuration
  - Security configuration
  - Bean wiring
```

### Core Ports

- `TimeSlotRepository`
- `OwnershipRepository`
- `PurchaseRepository`
- `ReservationRepository`
- `OfferRepository`
- `TransactionRepository`
- `PaymentPort`
- `MediaStoragePort`
- `MediaModerationPort`
- `AuditLogPort`
- `OutboxPort`
- `ClockPort`
- `IdempotencyPort`

## Read Model Strategy

The player is read-heavy and should not query transactional tables every second.

Recommended player flow:

```text
GET /api/archive
GET /api/timeline?from=0&to=300
```

The response contains approved occupied timeline segments for a window of
seconds. Public segment responses expose approved playback URLs only and do not
include owner identifiers, original upload URLs, or moderation metadata.

The initial implementation may query PostgreSQL through a dedicated read-model
port. Later, this can evolve into CDN-cacheable manifest chunks:

```text
/archive/manifest/chunk-0.json
/archive/manifest/chunk-1.json
```

Each chunk can cover 300 or 600 seconds. Ownership, media approval, or hide actions should invalidate or regenerate affected chunks.

## Media Playback Policy

Recommended initial policy:

- Images display for the entire owned range.
- Videos must be shorter than or equal to the owned range duration.
- Short videos may loop within the owned range.
- Videos should autoplay muted to comply with browser autoplay restrictions.
- Unowned seconds display a default placeholder.
- Only approved media can appear in the public player.

## Technology Decisions to Defer

The following technologies should be deferred until concrete operational pressure appears:

- Kafka
- Jenkins
- Dedicated search engine
- Dedicated analytics warehouse
- Microservice decomposition
- Complex resale negotiation workflows

The initial system should remain a modular monolith with strict internal boundaries.
