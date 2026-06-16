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
- `endSecond <= season.totalSeconds`

## Core Entities

### User

- `id`
- `email`
- `displayName`
- `role`
- `status`
- `createdAt`
- `updatedAt`

### Season

- `id`
- `title`
- `totalSeconds`
- `status`
- `createdAt`
- `updatedAt`

Suggested statuses:

- `DRAFT`
- `ACTIVE`
- `PAUSED`
- `ARCHIVED`

### OwnershipRecord

Ownership should be stored as history.

- `id`
- `seasonId`
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
- `seasonId`
- `startSecond`
- `endSecond`
- `amount`
- `status`
- `expiresAt`
- `createdAt`
- `updatedAt`

Suggested statuses:

- `HELD`
- `CHECKOUT_CREATED`
- `COMPLETED`
- `EXPIRED`
- `CANCELLED`

### Purchase

- `id`
- `buyerId`
- `seasonId`
- `startSecond`
- `endSecond`
- `amount`
- `currency`
- `status`
- `reservationId`
- `paymentProvider`
- `paymentReference`
- `createdAt`
- `updatedAt`

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

Only `APPROVED` media may be used by the public timeline player.

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

- `CreateSeason`
- `QueryTimeline`
- `CheckAvailability`
- `ReserveTimeRange`
- `CreateCheckout`
- `HandlePaymentWebhook`
- `CompletePrimaryPurchase`
- `UploadMediaAsset`
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

- Season total seconds must be positive.
- Time range start and end values must be valid.
- Active ownership ranges in the same season must not overlap.
- Payment provider event IDs must be unique.
- Idempotency keys must be unique.
- Offer status transitions must be controlled.
- Media ownership must be validated before update.

PostgreSQL exclusion constraints should be considered for active ownership overlap prevention. Application-level checks are not enough on their own.
