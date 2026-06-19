# Domain Model

## Modeling Principles

- The domain must not import framework, adapter, database, or infrastructure code.
- External systems must be accessed through ports.
- Time range ownership must be modeled as an auditable ledger, not as a mutable field only.
- Time ranges use inclusive start and exclusive end semantics.

## Time Range Semantics

All time ranges should use:

```text
startSecond inclusive
endSecond exclusive
```

Example:

```text
startSecond = 0
endSecond = 10
duration = 10 seconds
valid seconds = 0, 1, 2, 3, 4, 5, 6, 7, 8, 9
```

Required validation:

- `startSecond >= 0`
- `endSecond > startSecond`
- `endSecond <= 86,400`

## Core Entities

### User

- `id`
- `email`
- `displayName`
- `role`
- `status`
- `createdAt`
- `updatedAt`

### ArchiveTimeline

Time Archive has one canonical 24-hour archive timeline. It is a fixed product constraint, not a user-facing collection, season, or edition.

```text
totalSeconds = 86,400
valid seconds = 0 through 86,399
```

The timeline may be represented as a domain policy or value object instead of a database table.

### OwnershipRecord

Ownership should be stored as history.

- `id`
- `startSecond`
- `endSecond`
- `ownerId`
- `status`
- `validFrom`
- `validUntil`
- `acquisitionType`
- `sourcePurchaseId`
- `sourceTransactionId`
- `createdAt`
- `updatedAt`

Suggested statuses:

- `ACTIVE`
- `TRANSFERRED`
- `REVOKED`

Suggested acquisition types:

- `PRIMARY_PURCHASE`
- `RESALE`
- `ADMIN_GRANT`

Current ownership is represented by active records where `validUntil` is null.

### PurchaseReservation

- `id`
- `buyerId`
- `startSecond`
- `endSecond`
- `amountCents`
- `currency`
- `status`
- `expiresAt`
- `createdAt`
- `updatedAt`

Reservation pricing uses integer cents:

```text
amountCents = durationSeconds * 100
currency = USD
```

Suggested statuses:

- `HELD`
- `CHECKOUT_CREATED`
- `COMPLETED`
- `EXPIRED`
- `CANCELLED`

### CheckoutRequest

Checkout requests are provider-neutral command models used to create a payment checkout session for a held reservation.

- `reservationId`
- `buyerId`
- `startSecond`
- `endSecond`
- `amountCents`
- `currency`

Checkout creation must not create ownership or mark payment as confirmed. It only prepares payment collection for a valid reservation.

### CheckoutSession

Checkout sessions are provider-neutral responses returned by `PaymentPort`.

- `provider`
- `providerReference`
- `checkoutUrl`

The checkout URL is a redirect target only. It must not be treated as payment confirmation.

### Purchase

- `id`
- `buyerId`
- `startSecond`
- `endSecond`
- `amountCents`
- `currency`
- `status`
- `reservationId`
- `paymentProvider`
- `paymentReference`
- `createdAt`
- `updatedAt`

Primary purchase records use integer cents and remain payment-provider neutral:

```text
amountCents = durationSeconds * 100
currency = USD
```

Suggested statuses:

- `PENDING_PAYMENT`
- `PAID`
- `OWNERSHIP_GRANTED`
- `FAILED`
- `EXPIRED`
- `REFUNDED`

### MediaAsset

- `id`
- `ownershipRecordId`
- `ownerId`
- `mediaType`
- `originalFileUrl`
- `approvedFileUrl`
- `thumbnailUrl`
- `externalLink`
- `moderationStatus`
- `createdAt`
- `updatedAt`

Suggested moderation statuses:

- `UPLOADED`
- `PENDING_REVIEW`
- `APPROVED`
- `REJECTED`
- `HIDDEN`
- `DELETED_BY_OWNER`

Only `APPROVED` media may be used by the public timeline player. `REJECTED`,
`HIDDEN`, `UPLOADED`, and `PENDING_REVIEW` media must stay excluded.

Current persistence stores media assets against `ownershipRecordId` and also
stores `ownerId` so owner-scoped reads and authorization checks can avoid
trusting client-provided identity. Upload request creation, upload completion,
object storage access, and moderation are implemented through separate use
cases and ports.

`MediaUploadRequest` stores short-lived S3-compatible upload preparation state before a `MediaAsset` is created. It records the owner, ownership record, media type, expected content type, expected content length, server-generated object key, storage URL, status, and expiration. Upload request creation does not prove that the object was uploaded or safe; completion and processing must verify the object before media enters moderation.

Upload completion verifies object storage metadata before creating a `MediaAsset`. A completed upload request stores the created `mediaAssetId`, which makes repeated completion requests idempotent.

Admin moderation can transition uploaded media to `APPROVED` or `REJECTED`, and
can transition approved media to `HIDDEN`. Approval requires an explicit
`approvedFileUrl` so original upload URLs remain distinct from approved media
object references.
Admins can request short-lived preview URLs for private original uploads through
the storage port without making the object storage bucket public.

### PublicTimelineSegment

Public timeline reads use a player-safe read model derived from active
ownership records and approved media assets.

Fields:

- `startSecond`
- `endSecond`
- `mediaAssetId`
- `mediaType`
- `mediaUrl`
- `thumbnailUrl`
- `externalLink`

Public timeline segments MUST NOT expose owner identifiers, original upload
URLs, stored approved object references, moderation status, or internal storage
verification details. `mediaUrl` and `thumbnailUrl` are short-lived presigned
playback URLs generated for the public timeline response.

The initial implementation returns approved occupied segments for a requested
window. It does not return placeholder segments for unowned or empty seconds.

### Offer

MVP 2 entity.

- `id`
- `ownershipRecordId`
- `buyerId`
- `sellerId`
- `offerAmount`
- `currency`
- `status`
- `expiresAt`
- `createdAt`
- `updatedAt`

Suggested statuses:

- `SUBMITTED`
- `ACCEPTED_PENDING_PAYMENT`
- `REJECTED`
- `CANCELLED`
- `EXPIRED`
- `COMPLETED`

### Transaction

MVP 2 entity.

- `id`
- `buyerId`
- `sellerId`
- `ownershipRecordId`
- `amount`
- `platformFee`
- `sellerProceeds`
- `currency`
- `status`
- `paymentReference`
- `createdAt`
- `updatedAt`

Suggested statuses:

- `PENDING_PAYMENT`
- `PAYMENT_CONFIRMED`
- `OWNERSHIP_TRANSFERRED`
- `FAILED`
- `REFUNDED`

### PaymentEvent

- `id`
- `provider`
- `providerEventId`
- `eventType`
- `payloadHash`
- `receivedAt`
- `processedAt`
- `processingStatus`

`providerEventId` must be unique per provider.

The pair `(provider, providerEventId)` is the primary idempotency key for payment webhook processing.

### AuditLog

- `id`
- `actorUserId`
- `actorType`
- `action`
- `resourceType`
- `resourceId`
- `beforeState`
- `afterState`
- `requestId`
- `ipAddress`
- `userAgent`
- `createdAt`

Audit logs should be append-only.

### OutboxEvent

- `id`
- `eventType`
- `aggregateType`
- `aggregateId`
- `payload`
- `status`
- `createdAt`
- `processedAt`
- `retryCount`
- `lastError`

Outbox events should be inserted in the same database transaction as the state change they describe.

## Domain Services

Potential domain services:

- `TimeRangePolicy`
- `OwnershipPolicy`
- `PurchasePricingPolicy`
- `OfferPolicy`
- `MediaPublicationPolicy`
- `PlatformFeePolicy`

## Use Cases

MVP 1:

- `QueryTimeline`
- `CheckAvailability`
- `ReserveTimeRange`
- `CreateCheckout`
- `HandlePaymentWebhook`
- `CompletePrimaryPurchase`
- `UploadMediaAsset`
- `CreateOwnedRangeMediaUploadRequest`
- `CompleteOwnedRangeMediaUpload`
- `CreateAdminMediaPreviewUrl`
- `ApproveMediaAsset`
- `RejectMediaAsset`
- `HideMediaAsset`

MVP 2:

- `SubmitOffer`
- `AcceptOffer`
- `RejectOffer`
- `CompleteResalePayment`
- `TransferOwnership`

## Database Constraints

Important constraints:

- Time range start and end values must be valid.
- Time ranges must stay within the canonical 86,400-second archive.
- Active ownership ranges must not overlap.
- Active purchase reservation ranges must not overlap while status is `HELD` or `CHECKOUT_CREATED`.
- Payment provider event IDs must be unique.
- Idempotency keys must be unique.
- Offer status transitions must be controlled.
- Media ownership must be validated before update.

PostgreSQL exclusion constraints should be considered for active ownership overlap prevention. Application-level checks are not enough on their own.
