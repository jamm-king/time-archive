# PayPal Integration Design

## Purpose

This document defines the first production PayPal payment design for Time
Archive. It does not contain credentials and does not prove that PayPal is ready
for launch. It is the implementation contract for replacing the local fake
payment adapter with a real provider integration.

The goal is to collect real primary-purchase payments without weakening the
existing ownership boundary:

- checkout creates a provider payment opportunity only;
- browser return or cancel callbacks never grant ownership;
- ownership is granted only after a verified PayPal event is processed
  idempotently inside the existing purchase completion transaction.

## Current Baseline

The application already has provider-neutral payment boundaries:

- `PaymentPort` creates checkout sessions.
- `CreateCheckout` validates the reservation and returns a checkout URL.
- `CompletePrimaryPurchase` finalizes ownership from a verified payment command.
- `payment_events` uses `(provider, provider_event_id)` as the idempotency key.
- the fake payment adapter and fake webhook are local and CI tools only.

The current fake checkout path performs no network I/O. A PayPal adapter must not
reuse that transaction shape directly because provider calls can be slow,
retryable, or partially successful.

## Environment Model

| Environment | PayPal Resource | Purpose |
| --- | --- | --- |
| Local | Fake payment only by default | Fast local and CI verification without external provider state. |
| Staging | PayPal Sandbox | End-to-end provider validation with synthetic accounts and non-production data. |
| Production | PayPal live | Real buyers, real money, immutable ownership records. |

Staging and production must not share:

- PayPal applications;
- PayPal client secrets;
- PayPal webhook IDs;
- PayPal webhook event history;
- SSM parameter paths;
- database, R2, Cloudflare Tunnel, or Sentry resources.

## Runtime Parameters

Environment-scoped parameters live under:

```text
/time-archive/{environment}/paypal/
```

Required parameters:

| Name | Type | Notes |
| --- | --- | --- |
| `enabled` | `String` | `false` until the environment is ready for PayPal checkout. |
| `api-base-url` | `String` | Approved PayPal API base URL for the selected environment. Confirm from PayPal documentation during implementation. |
| `client-id` | `SecureString` | Treat as sensitive operational configuration even if PayPal exposes it in some client flows. |
| `client-secret` | `SecureString` | PayPal OAuth secret. |
| `return-url` | `String` | Public HTTPS return URL for approved orders. |
| `cancel-url` | `String` | Public HTTPS cancel URL for cancelled approvals. |
| `webhook-id` | `SecureString` | PayPal webhook ID used for signature verification. |

Currency remains server-computed from the reservation and is not a separate
PayPal runtime selector.

Do not overload `TIME_ARCHIVE_PAYMENT_FAKE_ENABLED` or any fake payment
configuration for PayPal. Fake payment must stay disabled in staging and
production.

## Checkout Flow

Recommended first implementation:

```text
1. Buyer selects an available range.
2. API creates a purchase reservation with server-computed amount and currency.
3. Buyer requests checkout for the reservation.
4. API creates or reuses a persisted checkout attempt.
5. API calls PayPal to create an order using a provider idempotency key.
6. API stores the PayPal order reference on the checkout attempt.
7. API returns the PayPal approval URL.
8. Browser redirects the buyer to PayPal.
```

Server-side rules:

- The server computes amount, currency, reservation ID, buyer ID, and range.
- The client must never submit amount, currency, or owned range values for
  PayPal order creation.
- The reservation ID must be included in PayPal metadata such as custom
  purchase-unit metadata or another reviewed provider-supported reference.
- Checkout creation should be idempotent for an existing active reservation and
  checkout attempt.
- A PayPal network call should not run while holding a long reservation row
  lock. Persist the intent to create checkout, call PayPal outside the lock, then
  finalize provider metadata with status checks.

## Approval Return And Capture Flow

PayPal buyer approval is not payment completion. PayPal return and cancel URLs
must point to Web pages, not API mutation endpoints:

```text
https://{public-host}/payments/paypal/return
https://{public-host}/payments/paypal/cancel
```

After PayPal redirects the browser back to Time Archive:

```text
1. Browser opens the Time Archive return page with the PayPal order token.
2. API verifies the authenticated user owns the reservation or checkout attempt.
3. API validates the checkout attempt is still payable.
4. API calls PayPal to capture the approved order.
5. API records the capture attempt result.
6. UI shows a pending confirmation state until the verified webhook finalizes
   ownership.
```

The return page calls `POST /api/payments/paypal/orders/{orderId}/capture`
through the same-origin Web proxy with the normal session and CSRF boundary.
The capture response may be useful for user feedback, but it must not create
ownership by itself. The source of truth remains the verified PayPal webhook.

Cancel return should mark the checkout attempt as cancelled or leave it
retryable according to the reservation expiration policy. It must not release
ownership because no ownership exists before webhook completion.

## Webhook Flow

PayPal webhook handling is the only real-payment path that can grant ownership:

```text
1. PayPal sends an HTTPS webhook through Cloudflare Tunnel.
2. API reads the raw body and required provider headers.
3. API verifies the event with the configured PayPal webhook ID.
4. API rejects unverified events before entering purchase completion logic.
5. API extracts provider event ID, event type, order or capture reference,
   reservation reference, amount, and currency.
6. API validates amount, currency, reservation, buyer, and provider references
   against server-side records.
7. API calls `CompletePrimaryPurchase`.
8. `CompletePrimaryPurchase` inserts or finds the payment event by
   `(provider, providerEventId)`.
9. Duplicate events return success without repeating ownership side effects.
10. Successful processing writes purchase, ownership, audit, and outbox records
    in one transaction.
```

The Cloudflare Tunnel route for the exact webhook path must target the API
container directly:

```text
/api/payments/paypal/webhooks -> http://api:8080
```

General browser and same-origin API traffic may continue to target Web first,
but PayPal webhooks should not depend on a Next.js proxy hop. This keeps the
provider delivery headers and payload as close as possible to the API
verification boundary. The Web proxy route may remain as a fallback, but it is
not the preferred staging or production ingress path for PayPal webhooks.

The first supported completed-payment event is `PAYMENT.CAPTURE.COMPLETED` for
the primary-purchase order. The API verifies the PayPal signature through
PayPal's webhook verification API before reading payload values for
finalization. Other verified events are acknowledged without side effects so
they do not trigger provider retries indefinitely.

Unverified events should return a non-success response and must not write
purchase, ownership, or processed payment records.

## Idempotency

Required idempotency controls:

| Surface | Control |
| --- | --- |
| Checkout creation | One active checkout attempt per reservation plus a stable provider request key. |
| PayPal order creation retry | Reuse the provider request key for the same reservation and checkout attempt. |
| PayPal capture retry | Reuse a stable capture request key for the same provider order. |
| Webhook delivery | Unique `(provider, providerEventId)` in `payment_events`. |
| Ownership finalization | Reservation status and active ownership overlap checks inside the completion transaction. |

Repeated webhook delivery must return success after confirming the original
event was already processed. It must not create a second purchase, ownership
record, audit record, or outbox event.

## Data Mapping

| Time Archive Field | PayPal Mapping Requirement |
| --- | --- |
| `reservationId` | Stored in provider metadata and used to find the server-side reservation. |
| `buyerId` | Server-side validation only; do not trust browser-return buyer data. |
| `amountCents` | Converted from integer cents to provider amount format by the adapter. |
| `currency` | Must match the reservation currency exactly. |
| `providerReference` | PayPal order ID for checkout; capture ID for final payment reference if available. |
| `providerEventId` | PayPal webhook event ID. |
| `payloadHash` | Hash of the raw verified webhook body for audit and duplicate investigation. |

The database should store only reconciliation references needed by the
application. Do not store card, bank, payer private details, or full provider
payloads unless a future compliance review approves it.

## Security Requirements

- PayPal client secret must be an SSM `SecureString` value. The webhook ID must
  also be an SSM `SecureString` value.
- The API must verify PayPal webhook signatures independently of Cloudflare.
- Cloudflare rules may reduce abuse, but they are not a substitute for webhook
  verification.
- CSRF protection should not apply to PayPal server-to-server webhooks, but
  browser-authenticated return, cancel, and capture endpoints must keep normal
  session and CSRF expectations unless implemented as safe GET redirects that do
  not mutate state.
- Never log PayPal secrets, webhook signatures, raw webhook payloads, approval
  URLs, session cookies, CSRF tokens, or authorization headers.
- Log request IDs, provider event IDs, provider order or capture IDs, and safe
  status codes only.
- Keep fake payment routes unavailable in staging and production.

## Error Handling

| Case | Expected Behavior |
| --- | --- |
| PayPal order creation unavailable | Return a safe checkout error and keep reservation retryable until expiration. |
| PayPal approval cancelled | Mark checkout attempt cancelled or leave retryable; do not create ownership. |
| Capture unavailable after approval | Return a pending or retryable state without granting ownership. |
| Webhook signature verification fails | Reject, log safe metadata, alert after threshold, and make no state change. |
| Amount or currency mismatch | Reject processing, record safe failure metadata, alert, and make no ownership change. |
| Unknown verified event type | Acknowledge or safely ignore according to implementation policy without ownership change. |
| Duplicate completed event | Return success with already-processed result and no duplicate side effects. |

## API And OpenAPI Impact

Expected API changes for the implementation branch:

- checkout response may keep the existing provider-neutral shape;
- add a PayPal return endpoint for approved browser redirects;
- add a PayPal cancel endpoint for cancelled browser redirects;
- add a PayPal webhook endpoint that reads raw body and provider headers;
- document that return and cancel endpoints are user-flow coordination only;
- document that only verified webhook processing can complete ownership.

Any public field removal or response-shape break requires explicit approval.

## Verification Plan

Code-level verification:

- adapter mapping tests for PayPal order creation request data;
- checkout attempt idempotency tests;
- capture retry behavior tests;
- webhook signature verifier tests with mocked PayPal verification response;
- completed capture webhook happy path;
- duplicate completed capture webhook path;
- signature failure path;
- amount mismatch path;
- currency mismatch path;
- reservation expiration race path.

Environment verification:

- staging PayPal Sandbox order approval through the browser;
- staging capture request after approval;
- staging verified webhook delivery through Cloudflare Tunnel;
- duplicate webhook replay in Sandbox or a controlled test harness;
- CloudWatch request ID search for checkout, capture, and webhook events;
- sensitive log keyword search after PayPal Sandbox verification.

Production launch verification:

- production PayPal credentials and webhook ID are provisioned under production
  SSM paths only;
- fake payment remains disabled;
- production webhook endpoint receives only verified PayPal events;
- first live low-value transaction is reconciled against PayPal dashboard and
  Time Archive ownership records.

## Rollback

Before real payments are enabled, rollback is code deployment rollback plus
keeping fake payment disabled.

After real payments are enabled:

- do not delete PayPal, purchase, payment event, ownership, or audit records;
- disable checkout creation if provider behavior is unsafe;
- keep webhook processing available if payments may still settle;
- use forward fixes for database schema whenever possible;
- coordinate refunds, disputed captures, or cancelled orders through a reviewed
  operational runbook before changing ownership.

## Release Gate

The payment release gate can move from blocked only after:

- PayPal checkout, return, capture, and webhook code is implemented;
- webhook signature verification is tested;
- duplicate webhook idempotency is tested;
- amount and currency mismatch rejection is tested;
- staging PayPal Sandbox browser and webhook verification passes;
- sensitive logging checks pass after PayPal traffic;
- production SSM parameter metadata validation includes PayPal parameters;
- the project owner approves the first live payment verification procedure.
