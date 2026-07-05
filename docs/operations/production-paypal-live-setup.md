# Production PayPal Live Setup

## Purpose

This runbook defines the production PayPal Live setup and first-payment drill
for Time Archive. It is a launch gate for collecting real money.

This document does not contain PayPal credentials, webhook secrets, customer
data, or raw provider payloads.

## Current Status

PayPal Sandbox is ready for staging:

- checkout creation;
- browser approval return;
- server-side capture;
- verified `PAYMENT.CAPTURE.COMPLETED` webhook processing;
- ownership finalization;
- duplicate webhook idempotency through the staging resend drill.

Production PayPal Live remains blocked until this runbook is completed and the
result is recorded.

## Hard Safety Rules

- Do not reuse the staging PayPal Sandbox application for production.
- Do not reuse staging PayPal client secrets, webhook IDs, event IDs, or
  dashboard history for production.
- Do not enable production payment collection until:
  - production SSM parameters are provisioned;
  - production Cloudflare routing is verified;
  - the first low-value live payment drill passes;
  - refund and reconciliation steps are verified.
- Do not commit PayPal Live credentials, webhook IDs if treated as sensitive,
  raw webhook payloads, signatures, cookies, or customer private data.
- Do not manually mutate production payment, purchase, ownership, audit, or
  outbox records during payment drills.

## Required Production PayPal Resources

Create these resources in PayPal Developer Dashboard:

| Resource | Requirement |
| --- | --- |
| PayPal Live app | Dedicated to Time Archive production. |
| Client ID | Stored in SSM under `/time-archive/production/paypal/client-id`. |
| Client secret | Stored as SSM `SecureString` under `/time-archive/production/paypal/client-secret`. |
| Live webhook | Dedicated to Time Archive production. |
| Webhook URL | `https://time-archive.com/api/payments/paypal/webhooks`. |
| Webhook ID | Stored as SSM `SecureString` under `/time-archive/production/paypal/webhook-id`. |
| Webhook events | At minimum `PAYMENT.CAPTURE.COMPLETED`. |

If the production public hostname is not `https://time-archive.com`, replace
the URL in this runbook and update production runtime parameters consistently.

## Production Runtime Parameters

The production deployment renderer reads these PayPal parameters:

| Parameter | Type | Expected value shape |
| --- | --- | --- |
| `/time-archive/production/paypal/enabled` | `String` | `false` until the live drill is approved; `true` only during/after approved launch. |
| `/time-archive/production/paypal/api-base-url` | `String` | `https://api-m.paypal.com`. |
| `/time-archive/production/paypal/client-id` | `SecureString` | PayPal Live app client ID. |
| `/time-archive/production/paypal/client-secret` | `SecureString` | PayPal Live app client secret. |
| `/time-archive/production/paypal/return-url` | `String` | `https://time-archive.com/payments/paypal/return`. |
| `/time-archive/production/paypal/cancel-url` | `String` | `https://time-archive.com/payments/paypal/cancel`. |
| `/time-archive/production/paypal/webhook-id` | `SecureString` | PayPal Live webhook ID. |

Before the first live drill, verify without printing decrypted values:

```bash
aws ssm describe-parameters \
  --parameter-filters "Key=Name,Option=BeginsWith,Values=/time-archive/production/paypal/" \
  --query 'Parameters[].{Name:Name,Type:Type}' \
  --output table
```

Expected:

- `client-secret` and `webhook-id` are `SecureString`;
- no staging parameter path appears;
- the production parameter names match this table exactly.

## Cloudflare Routing Requirements

Production Cloudflare routing must match the staging ingress model:

```text
https://time-archive.com/api/payments/paypal/webhooks -> http://api:8080
https://time-archive.com/*                                 -> http://web:3000
```

The exact webhook route must be ordered before the general Web route.

Production routing checks:

- Cloudflare Tunnel token is production-specific.
- PayPal webhook route targets the API container directly.
- The route bypasses cache.
- Production host uses HTTPS.
- The Web origin can still serve `/payments/paypal/return` and
  `/payments/paypal/cancel`.

## Pre-Launch Checklist

Complete these before setting production PayPal `enabled=true`:

- Production PayPal Live app exists.
- Production webhook URL is registered and event subscription includes
  `PAYMENT.CAPTURE.COMPLETED`.
- Production SSM PayPal parameters exist with correct names and types.
- Production R2 bucket, database, Cloudflare Tunnel, and runtime parameters are
  production-specific and not shared with staging.
- Production deploy renders runtime env without printing secret values.
- Fake payment remains disabled.
- Production Cloudflare webhook route is configured before the general Web
  route.
- Production logs are flowing to `/time-archive/production/api`.
- A rollback decision is documented:
  - if checkout is unsafe, set PayPal `enabled=false` and redeploy;
  - if webhooks may still arrive for captured payments, keep webhook processing
    available until pending captures settle.

## First Low-Value Live Payment Drill

The first live drill should use the smallest acceptable purchase amount for the
product model. Time Archive sells one second for one USD, so the first live
drill should buy a one-second range unless the project owner explicitly
approves another range.

Steps:

1. Deploy production with PayPal Live parameters and `enabled=true`.
2. Register or sign in with a production test operator account.
3. Select an available one-second range.
4. Start checkout and approve the live PayPal payment.
5. Confirm the browser returns to Time Archive.
6. Confirm the return page reaches payment confirmation after webhook
   processing.
7. Confirm PayPal Dashboard shows a completed live capture.
8. Confirm CloudWatch API logs show:
   - PayPal webhook verification `SUCCESS`;
   - `POST /api/payments/paypal/webhooks status=200`;
   - no raw payloads, signatures, cookies, CSRF tokens, authorization headers,
     or credentials.
9. Confirm read-only production DB state:
   - one `payment_events` row for the PayPal event ID;
   - `processing_status = 'PROCESSED'`;
   - one `purchases` row for the reservation;
   - `status = 'OWNERSHIP_GRANTED'`;
   - one active `ownership_records` row for the purchase.

Do not proceed to public traffic if any step fails.

## Refund Drill

After the first live payment drill passes, run a controlled refund in PayPal
Dashboard unless the project owner explicitly accepts skipping it for launch.

Minimum refund verification:

- Refund is initiated in PayPal Dashboard.
- Refund reference is recorded in the launch notes.
- Time Archive ownership behavior is explicitly decided:
  - for MVP, do not automatically revoke ownership from a PayPal refund event
    unless a reviewed refund/revocation feature exists;
  - handle ownership correction manually only through an approved high-impact
    data operation.
- Support notes explain how the refunded purchase should be treated.

The current application does not implement automatic refund webhook processing.
Do not subscribe to refund events as ownership-changing events without a
separate design and implementation review.

## Dashboard Reconciliation

For the first live payment, record a repository-safe reconciliation summary:

```text
Date:
Operator:
Production deployment commit SHA:
PayPal Live order ID:
PayPal Live capture ID:
PayPal Live event ID:
Time Archive reservation ID:
Time Archive purchase ID:
Time Archive ownership record ID:
Gross amount:
Currency:
PayPal Dashboard capture status:
Time Archive payment event status:
Time Archive purchase status:
Time Archive ownership status:
CloudWatch request ID:
Refund drill result:
Outcome: PASS | FAIL
Notes:
```

Do not record payer private information, PayPal account emails, raw payloads, or
credential values in repository documents.

## Rollback And Disablement

If production checkout must be disabled:

1. Set `/time-archive/production/paypal/enabled` to `false`.
2. Redeploy production so checkout creation no longer uses PayPal.
3. Keep the PayPal webhook endpoint deployed if live captures may still settle.
4. Verify public purchase UI no longer starts PayPal checkout.
5. Record the disablement reason and deployment SHA.

Do not delete PayPal events, purchases, ownership records, audit logs, outbox
events, R2 objects, or database snapshots as a rollback shortcut.

## Pass Criteria

Production PayPal Live setup passes only when:

- a production-only PayPal Live app and webhook are configured;
- production SSM PayPal parameters are provisioned with correct names and
  types;
- production Cloudflare routes PayPal webhooks directly to the API;
- the first low-value live payment completes;
- verified live webhook processing grants exactly one ownership record;
- logs are safe and searchable by request ID;
- PayPal Dashboard, `payment_events`, `purchases`, and `ownership_records`
  reconcile;
- refund handling is verified or explicitly accepted as a launch limitation.

Only after this pass result is recorded should the release readiness checklist
move `Production PayPal live setup` out of `Blocked for production`.
