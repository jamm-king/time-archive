# CI/CD and Testing Strategy

## Purpose

This document defines the recommended CI/CD, deployment, environment, and testing strategy for Time Archive.

Time Archive should stay operationally simple in the beginning, but the system must still protect high-integrity workflows such as payments, ownership records, media moderation, and audit trails.

## Strategy Summary

Recommended initial direction:

- Use GitHub Actions instead of Jenkins.
- Use Docker for application packaging.
- Use PostgreSQL as the primary database in every meaningful environment.
- Use Docker Compose for local infrastructure.
- Use EC2 with Docker Compose, RDS PostgreSQL, and Cloudflare R2 for the initial
  production deployment.
- Use staging auto-deploy and production manual approval.
- Use Testcontainers for database integration tests.
- Defer Kafka, Jenkins, and microservice-oriented deployment until operational needs justify them.

## Environments

The initial project should use three environments.

```text
local
staging
production
```

### Local

Purpose:

- Developer feedback loop
- Unit tests
- Integration tests
- Local manual verification

Recommended services:

- Application process from the local IDE or Gradle
- PostgreSQL through Docker Compose
- Redis through Docker Compose for server-side sessions
- Payment provider sandbox or fake adapter
- MinIO through Docker Compose for S3-compatible local media storage

Expected commands after implementation exists:

```text
docker compose --env-file .env.local up -d
set -a
source .env.local
set +a
cd apps/api
./gradlew test
./gradlew bootRun
cd ../web
npm run lint
npm run build
```

GitHub Actions sets `COMPOSE_ENV_FILES=.env.local.example` so Compose-based
verification uses committed non-production placeholder values. Local and
deployed environments must supply their own ignored or deployment-managed
values instead.

### Staging

Purpose:

- Production-like validation
- Payment sandbox webhook testing
- Media upload and moderation flow testing
- Deployment smoke tests
- Database migration rehearsal

Staging should be close to production, but it can use smaller instance sizes and sandbox credentials.

### Production

Purpose:

- Real users
- Real payments
- Real ownership records
- Real media delivery

Production must have:

- HTTPS only through Cloudflare-managed edge TLS and Cloudflare Tunnel
- Managed database backups
- Point-in-time recovery
- Strict secret management
- Monitoring and alerting
- Controlled database migrations
- Manual approval before deployment

## CI Pipeline

Pull requests should run checks before merge.

Recommended PR checks:

```text
checkout
setup JDK
validate Gradle wrapper
validate OpenAPI document
run backend tests
build backend application
build backend Docker image
setup Node
run web lint
build web application
run local Docker Compose verification scripts
run local web smoke check
```

Current required checks:

- OpenAPI validation
- Backend test
- Backend build
- Backend Docker image build
- Web lint
- Web build
- Local purchase flow verification through Docker Compose
- Local media upload flow verification through Docker Compose
- Local public timeline flow verification through Docker Compose
- Local auth flow verification through Docker Compose
- Local web purchase and upload verification through Docker Compose
- Local web smoke verification

Release candidates should also be reviewed against the
[Release Readiness Checklist](release-readiness-checklist.md) before any
non-local deployment.

## CD Pipeline

Recommended deployment flow:

```text
Pull Request
  -> CI checks

Merge to main
  -> Build Docker image
  -> Push Docker image
  -> Deploy staging
  -> Run smoke tests

Manual approval
  -> Deploy production
  -> Run production smoke tests
```

GitHub Actions environments should be used for `staging` and `production`.

Production should require manual approval.

## Deployment Targets

### Selected MVP Production Path

Use:

- One ARM64 EC2 application host with Docker Compose
- AWS RDS PostgreSQL
- Cloudflare R2
- Cloudflare Tunnel, DNS, TLS, and WAF
- AWS SSM Parameter Store
- CloudWatch and Sentry Developer

Why:

- Lower initial operating cost and complexity than an orchestrated cluster
- Good fit for the current API, Web, Redis, and Docker packaging
- Database failure isolation and managed backup support through RDS
- Short-lived deployment credentials through GitHub OIDC
- A documented path to replace the application host without moving ownership
  or media records

The complete selected topology and its limitations are defined in
[EC2 And RDS Deployment Architecture](ec2-rds-deployment-architecture.md), and
the public HTTPS boundary is defined in
[Cloudflare Tunnel HTTPS](cloudflare-tunnel-https.md).

### Deferred Scale-Out Path

Consider ECS Fargate or multiple EC2 application hosts behind a load balancer
when:

- one application host no longer meets availability requirements;
- deployments require zero-downtime instance replacement;
- application load requires horizontal scaling; or
- maintaining the EC2 host becomes more expensive than managed orchestration.

## Docker Image Strategy

Application images should follow these rules:

- Use multi-stage builds.
- Run as a non-root user.
- Use a minimal runtime image.
- Expose a health endpoint.
- Tag images with immutable Git SHAs.
- Avoid deploying `latest` to production.

Recommended tags:

```text
time-archive-api:{git-sha}
time-archive-api:main-latest
```

The backend Docker image is built from `apps/api`.
The web Docker image is built from `apps/web`.

Production deployments should use `{git-sha}`.

## Testing Strategy

Verification scripts should be maintained as shell scripts only. They must run
in GitHub Actions on Ubuntu and should also run on Windows through Git Bash.
Avoid adding parallel PowerShell variants because duplicated scripts can drift
from CI behavior.

Time Archive needs multiple test layers because ownership, payments, and moderation cannot be validated with unit tests alone.

### Unit Tests

Focus:

- Domain rules
- Time range validation
- Pricing
- Status transitions
- Platform fee calculation
- Media publication policy

Examples:

- A range with `endSecond <= startSecond` is rejected.
- A primary purchase amount equals duration times unit price.
- A rejected media asset cannot be public.

### Application Tests

Focus:

- Use case behavior
- Idempotency
- Ownership creation
- Reservation expiration behavior
- Media approval flow
- Offer accept and reject flow

Examples:

- Completing the same purchase twice does not create duplicate ownership.
- A payment confirmation for an expired reservation follows the defined business rule.
- A user cannot upload media to another user's ownership record.

### Integration Tests

Use Testcontainers with PostgreSQL.

Focus:

- Database constraints
- Transaction boundaries
- Overlapping range prevention
- Row locking behavior
- Repository queries
- Flyway migrations

H2 should not be used for critical database behavior because it cannot reliably represent PostgreSQL-specific locking, range constraints, or exclusion constraints.

### Contract-Like Tests

Focus:

- Payment webhook payload handling
- Stable API error response shape
- Storage port behavior through fake adapters
- Media moderation adapter boundaries

### End-to-End and Smoke Tests

Initial smoke tests should verify:

- Health endpoint responds.
- Availability endpoint responds.
- Reservation creation succeeds.
- Checkout creation succeeds.
- Fake payment webhook completion succeeds in local verification.
- Duplicate fake payment webhook is idempotent.
- Media upload completion succeeds in local verification.
- Admin-approved media appears in the public timeline in local verification.

Staging E2E tests should verify:

- Purchase flow through payment sandbox.
- Media upload and admin approval.
- Approved media appears in the timeline.
- Hidden media disappears from the timeline.

## High-Risk Scenarios to Test

Required high-value tests:

- Two users attempt to buy the same time range concurrently, and only one succeeds.
- The same payment webhook is delivered twice, and ownership is created once.
- Unapproved media is never returned by public timeline APIs.
- Hidden media is removed from the public timeline.
- Expired reservations cannot be completed incorrectly.
- An already resolved offer cannot be accepted again.
- Ownership transfer closes the previous ownership record and creates a new one atomically.

## Database Migration Strategy

Use Flyway for schema migrations.

Rules:

- Migrations must be reviewed before production deployment.
- Destructive migrations require explicit approval.
- Large table changes should be staged across multiple deployments.
- Production should prefer a dedicated migration job before application deployment.
- Rollback should usually be handled through forward-fix migrations.

Environment guidance:

- Local: migrations can run at application startup.
- Staging: migrations can run automatically before deployment.
- Production: migrations should run as a controlled deployment step.

## Secrets Strategy

Do not store production secrets in the repository.

Recommended setup:

- Use GitHub Actions OIDC to assume an AWS role.
- Store production configuration and secrets in the selected SSM Parameter
  Store hierarchy. Reconsider Secrets Manager only when automatic rotation is
  required.
- Store only minimal deployment configuration in GitHub Actions secrets.
- Keep payment provider secrets, webhook signing secrets, DB passwords, and storage credentials out of Git.

## Observability

Initial observability should include:

- Structured JSON logs
- Request correlation IDs
- HTTP request count
- HTTP latency p95 and p99
- HTTP 5xx rate
- Database connection pool usage
- Payment webhook failure count
- Media processing failure count
- Reservation expiration count
- Deployment success and failure events

Initial tooling can use CloudWatch. The application should remain compatible with OpenTelemetry instrumentation so observability can evolve without rewriting business logic.

## Alerting

Recommended alerts:

- 5xx error spike
- Payment webhook failure spike
- Database connection pool exhaustion
- Failed deployment
- Failed database migration
- Media processing failure spike
- Storage access failure
- Unusual reservation or checkout failure rate

## Rollback Strategy

Application rollback:

- Deploy the previous Docker image by Git SHA.
- Keep the previous EC2 deployment manifest and immutable image SHAs available.
- Use SSM Run Command to restore the previous immutable image SHAs on EC2.

Database rollback:

- Avoid destructive migrations.
- Prefer forward-fix migrations.
- Restore from backup only for severe data corruption or unrecoverable operational mistakes.

Media rollback:

- Keep original uploads private.
- Keep approved media objects immutable where possible.
- Hide problematic media through moderation state instead of deleting objects.

## Deferred Tools

### Jenkins

Jenkins is not recommended initially because it adds server maintenance, plugin management, credentials management, and patching overhead.

Consider Jenkins only if the project later needs:

- Existing organizational Jenkins infrastructure
- Complex internal approval workflows
- On-premise build runners
- Specialized build environments

### Kafka

Kafka is not recommended for the MVP.

Use the transactional outbox pattern first.

Consider Kafka later if the project needs:

- Multiple independent event consumers
- High-volume event processing
- Event replay
- Analytics or data pipeline fan-out
- Stronger event-streaming operational semantics

## Initial Implementation Recommendation

Start with:

```text
GitHub Actions
Docker Compose for local infrastructure
Kotlin + Spring Boot + Gradle
PostgreSQL + Flyway
JUnit 5 + AssertJ + MockK
Testcontainers PostgreSQL
Docker image build in CI
Staging deployment before production
Manual production approval
```

This keeps the first system simple while preserving a clear path toward higher operational maturity.
