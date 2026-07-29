# Time Archive Project Retrospective

## Overview

Time Archive is a media-first web product built around one fixed, shared
24-hour timeline. Every second is a scarce asset: a user can purchase an
unowned contiguous range, attach media to the owned range, and have that media
appear on the public timeline only after moderation.

The core product rule remained intentionally narrow throughout the MVP:

```text
1 second = 1 dollar
```

The project was not treated as a simple media-upload application. Ownership,
payment confirmation, moderation, and public delivery all change the meaning
of a second on the canonical timeline. The engineering focus was therefore on
integrity, explicit boundaries, and operational verification rather than broad
feature expansion.

The AWS staging and production stacks were decommissioned on 2026-07-27. This
document records the engineering work performed before that decision. See
[Project Decommissioning](operations/project-decommissioning.md) for the
current infrastructure state.

## Product And Domain Decisions

### One Canonical Archive

The product uses one immutable 24-hour archive containing 86,400 seconds. An
earlier idea to introduce repeating seasons was deliberately rejected. Adding
new archives after the original one sold out would weaken the intended scarcity
of each second and introduce product concepts that were not needed for the
MVP.

### Range-Based Purchase Instead Of Single-Second Checkout

The public timeline presents an available second as the start of a possible
purchase. The buyer can choose a contiguous duration, constrained by the next
owned or reserved second. This supports the `BUY THIS SECOND` product language
while allowing a buyer to acquire a meaningful contiguous interval.

The backend, not the browser, determines the maximum purchasable duration and
enforces the overlap rule. This prevents a client from extending a purchase
into an owned range.

### Moderation Before Publication

Ownership does not make uploaded media public. Uploads remain private until an
administrator approves them. Rejected or hidden media is excluded from public
timeline delivery. This separates a durable ownership record from a reversible
publication decision.

## Architecture

### Modular Backend Boundaries

The backend uses Kotlin and Spring Boot with a hexagonal architecture. Domain
and application logic define ports for persistence, payment, object storage,
and other external systems. REST, PostgreSQL, Redis, PayPal, and S3-compatible
storage remain adapter concerns.

This was useful in practice, not only as a diagram. The payment flow could use
a local fake adapter for CI, a PayPal adapter for Sandbox verification, and a
disabled adapter in environments where payment collection was intentionally
off. MinIO in local development and Cloudflare R2 in deployed verification
shared an S3-compatible storage boundary.

The frontend is a Next.js application. It uses a CSR-first fullscreen player
and same-origin proxy routes so browser session cookies and CSRF handling do
not depend on exposing the API directly to the browser.

Detailed design references:

- [System Architecture](architecture/time-archive-architecture.md)
- [Domain Model](architecture/domain-model.md)
- [Transaction Boundaries](architecture/transaction-boundaries.md)
- [Security and Operations](architecture/security-and-operations.md)

### Source Of Truth And Coordination

PostgreSQL is the source of truth for ownership, reservations, purchases,
media assets, moderation state, payment events, and audit records. Redis is
used for server-side sessions and shared rate-limit counters, not for final
ownership decisions.

This distinction matters when a second is contested. A fast cache can assist
with coordination, but only database transactions, locks, and constraints can
make ownership durable and auditable.

## Reliability And Integrity Work

### Reservation And Ownership Concurrency

The purchase flow uses explicit reservation and purchase states. Within the
transaction boundary, the application validates the requested range, expires
overdue reservations, checks active ownership and reservation overlap, and
records the result with an audit trail.

Ownership finalization re-checks overlap under a database lock before creating
the active ownership record. This protects the primary race condition: two
buyers trying to acquire the same seconds at nearly the same time.

### Payment Finalization And Idempotency

Browser return URLs are not treated as proof of payment. Payment finalization
is based on a verified provider event. Provider event IDs, state-transition
checks, and persisted payment events prevent a duplicated webhook from granting
ownership twice.

The project implemented and exercised:

- local fake-payment completion for fast deterministic CI verification;
- PayPal Sandbox checkout and capture;
- PayPal webhook signature verification in the staging path;
- duplicate-event idempotency verification through a staging resend drill;
- rejection paths for capture, order, amount, and currency mismatches.

The [PayPal Staging Idempotency Drill](operations/paypal-staging-idempotency-drill.md)
and [Transaction Boundaries](architecture/transaction-boundaries.md) document
the detailed controls.

### Media Upload Integrity

Media uploads use a server-created upload request and a short-lived presigned
PUT URL. The completion API does not accept a browser claim that an upload
succeeded. It verifies the object key, ownership, request expiration, expected
content length, expected content type, file signature, and supported MP4
duration before creating a media asset.

Original media stays private. Administrators receive short-lived presigned
preview URLs after authorization, and the public timeline receives short-lived
playback URLs only for approved media. This avoids treating object storage URLs
as permanent public application state.

## Security Work

- Server-side sessions with `HttpOnly`, `Secure`, and `SameSite=Lax` cookie
  attributes in deployed environments.
- CSRF protection for browser-authenticated mutation requests.
- Server-derived identity for purchase and owned-media APIs, rather than
  client-supplied buyer or owner IDs.
- `USER` and `ADMIN` roles with admin moderation authorization.
- Moderation audit records for approval, rejection, and hiding actions.
- Redis-backed, fail-closed rate limiting for authentication, public reads,
  purchase, media, and admin routes.
- Trusted Cloudflare client-address propagation only after the private origin
  boundary was verified.
- Private originals, presigned upload and download URLs, file signature
  validation, and duration validation for supported videos.

Some media-safety controls were intentionally not implemented for the MVP:
malware scanning, transcoding, and thumbnail generation. These remain required
work before a broader public launch.

## Delivery And Operations

### Local Development And Verification

Docker Compose provided PostgreSQL, Redis, MinIO, API, and Web services for
local workflows. The repository used portable shell scripts as the primary
verification interface, which made the same checks usable in GitHub Actions
and local Git Bash environments.

The verification suite covered local purchase, authenticated owned-range,
media upload, admin preview, public timeline, and web purchase/upload flows.
The OpenAPI contract was validated with Redocly through Docker.

### CI/CD And Deployment Practice

GitHub Actions was selected over Jenkins because the project needed a managed,
repository-native CI/CD surface rather than another server to operate. The
pipeline included Gradle and Node dependency caching, API tests, web builds,
Docker Compose flows, OpenAPI validation, and focused shell smoke scripts.

For deployed environments, the project used GitHub OIDC, ECR images tagged by
Git SHA, digest-pinned supporting images, EC2 with Docker Compose, RDS,
Parameter Store, CloudWatch, Cloudflare Tunnel, and Cloudflare R2. Staging and
production smoke workflows exercised public availability, authentication,
admin authorization, media upload and moderation, media preview, request IDs,
security headers, and storage CORS.

Staging rollback and forward recovery were also practiced as an image rollback
drill. Database recovery was treated separately because it has a different
data-safety boundary.

Detailed references:

- [CI/CD and Testing Strategy](operations/ci-cd-and-testing-strategy.md)
- [Staging Rollback Drill](operations/staging-rollback-drill.md)
- [Database Recovery Runbook](operations/database-recovery-runbook.md)
- [Cloudflare R2 Storage Setup](operations/r2-storage-setup.md)

## Outcome And Limitations

The project reached a verified MVP boundary for the primary application flows:
authentication, reservation, local payment completion, owned-range reads,
media upload completion, moderation, private preview, and approved public
timeline delivery. Staging verification covered PayPal Sandbox checkout,
capture, verified webhook handling, and duplicate webhook processing.

The project did not launch paid production traffic. A PayPal Live low-value
payment and refund drill was intentionally not completed because a suitable
overseas payment test method was unavailable. That was treated as a release
blocker rather than bypassed. The payment provider was disabled before the
infrastructure was decommissioned.

The final decision was to decommission the AWS stacks rather than continue to
pay for inactive staging and production infrastructure. This was an operational
decision, not evidence that the architecture or verification work was
discarded. The repository, implementation plans, and operational records remain
available for review.

## What I Would Do Differently On Reactivation

1. Decide the payment strategy before provisioning long-lived production
   infrastructure. For a global product, keep USD as the canonical price and
   validate a real Live payment path before public launch. Add a domestic
   provider only when its contract and cost fit the product's target market.
2. Model provider-specific charged amounts and exchange-rate quotes separately
   from the canonical USD price before introducing KRW or another local
   currency.
3. Add malware scanning, media processing, and explicit retention/deletion
   automation before opening uploads to a larger public audience.
4. Recreate environments from fresh infrastructure and rotated credentials,
   then repeat the deployment, rollback, backup, security, and payment drills.
5. Keep the narrow product boundary until real user behavior justifies resale,
   seasons, Kafka, or additional operational systems.

## Engineering Takeaways

The central lesson from Time Archive was that a small product surface can still
need strong systems engineering. A timeline with 86,400 scarce units requires
clear transaction ownership; user media requires a publication boundary; and a
payment redirect requires a verified server-side event before it can change
ownership.

The most valuable outcome was not simply deploying a web application. It was
building a repeatable path from local verification to staging and production
smoke checks, testing rollback and recovery assumptions, and making an explicit
cost and risk decision when the remaining Live-payment gate could not be
verified responsibly.
