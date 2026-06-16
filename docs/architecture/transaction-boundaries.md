# Transaction Boundaries

## Principles

- Ownership integrity must be enforced by database transactions and constraints.
- External network calls must not be performed inside long database transactions.
- Payment webhooks must be idempotent.
- State transitions must be explicit and validated.
- Every money or ownership operation must create an audit trail.

## Primary Purchase Flow

Recommended flow:

```text
1. User selects an available range.
2. Backend creates a purchase reservation.
3. Database transaction checks overlap and stores the reservation.
4. User is redirected to checkout.
5. Payment provider sends a webhook.
6. Backend verifies the webhook signature.
7. Backend processes the payment event idempotently.
8. Database transaction marks purchase as paid.
9. Database transaction creates ownership records.
10. Audit logs and outbox events are written.
```

The browser redirect after payment is not the source of truth. Final ownership must be granted only from a verified payment event.

## Reservation Transaction

Within one database transaction:

- Validate season status.
- Validate range boundaries.
- Check active ownership overlap.
- Check active reservation overlap if the product policy requires temporary holds.
- Insert reservation with `expiresAt`.
- Insert audit log.

Reservation expiration should be handled by a scheduled worker or background job.

## Payment Webhook Transaction

Webhook handling must be idempotent.

Within one database transaction:

- Insert or find the payment event by provider event ID.
- If already processed, return success without repeating side effects.
- Load the purchase or reservation with a lock.
- Validate current status.
- Mark payment as confirmed.
- Create active ownership records.
- Mark purchase as `OWNERSHIP_GRANTED`.
- Mark reservation as `COMPLETED`.
- Insert audit logs.
- Insert outbox events for media manifest invalidation and notifications.

## Resale Transaction

Resale should be introduced after primary purchase is stable.

Recommended flow:

```text
1. Buyer submits an offer.
2. Owner accepts the offer.
3. Offer becomes ACCEPTED_PENDING_PAYMENT.
4. Buyer completes payment.
5. Payment webhook confirms payment.
6. Database transaction closes seller ownership.
7. Database transaction creates buyer ownership.
8. Platform fee and seller proceeds are recorded.
9. Audit logs and outbox events are written.
```

The ownership transfer should happen only after payment confirmation.

## Idempotency

Idempotency is required for:

- Checkout creation
- Payment webhook processing
- Ownership finalization
- Offer acceptance
- Media approval actions

Recommended controls:

- Unique idempotency key per client command where appropriate
- Unique payment provider event ID
- Strict status transition checks
- No-op behavior when a command has already reached the desired final state

Example state transition:

```text
PENDING_PAYMENT -> PAID -> OWNERSHIP_GRANTED
PENDING_PAYMENT -> FAILED
PENDING_PAYMENT -> EXPIRED
```

If a purchase is already `OWNERSHIP_GRANTED`, a repeated webhook must not create duplicate ownership.

## Concurrency Risks

Important race conditions:

- Two users attempt to buy the same seconds.
- A reservation expires while payment confirmation arrives.
- A payment webhook is delivered more than once.
- An offer is accepted twice.
- An owned range is transferred while the owner changes media.
- An admin hides media while the owner uploads a replacement.
- Timeline manifest generation reads partially updated state.

## Concurrency Controls

Recommended controls:

- PostgreSQL transactions
- Row-level locks for purchase, reservation, offer, and ownership records
- Optimistic locking with a `version` column for editable resources
- Unique constraints
- Exclusion constraints for active ownership ranges
- Idempotency keys
- Transactional outbox events

Redis locks may be used as a short-lived coordination aid, but they must not replace database constraints.

## Outbox Pattern

Any operation that changes ownership, media publication, or transaction state should insert an outbox event in the same database transaction.

Potential outbox events:

- `OwnershipCreated`
- `OwnershipTransferred`
- `MediaApproved`
- `MediaHidden`
- `PurchaseCompleted`
- `OfferAccepted`
- `TimelineManifestInvalidated`

Background workers can process outbox events to:

- Regenerate timeline manifest chunks
- Send notifications
- Trigger media processing
- Update search or analytics systems in the future

Kafka can later consume these events if the outbox dispatcher is replaced or extended.

## Error Handling

Domain errors should be stable and explicit:

- `INVALID_TIME_RANGE`
- `TIME_RANGE_ALREADY_OWNED`
- `RESERVATION_EXPIRED`
- `PURCHASE_ALREADY_COMPLETED`
- `OFFER_ALREADY_RESOLVED`
- `MEDIA_NOT_OWNED_BY_USER`
- `OWNERSHIP_TRANSFER_NOT_ALLOWED`

System errors should not leak internal details:

- `PAYMENT_PROVIDER_UNAVAILABLE`
- `STORAGE_UPLOAD_FAILED`
- `MEDIA_PROCESSING_FAILED`
- `UNEXPECTED_ERROR`

API responses should use stable error codes and safe user-facing messages.
