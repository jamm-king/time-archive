# PayPal Staging Idempotency Drill

## Purpose

This runbook verifies that the deployed staging PayPal webhook path handles a
duplicate `PAYMENT.CAPTURE.COMPLETED` event idempotently. A duplicate PayPal
Sandbox webhook must return success and must not create duplicate purchases,
ownership records, audit records, or outbox events.

This drill is required before marking `Payment idempotency` ready for paid
production readiness. It does not verify PayPal Live.

## Preconditions

- Staging is deployed from the intended release candidate.
- PayPal Sandbox checkout, capture, webhook verification, and return-page UX
  have already passed for the same staging deployment.
- The PayPal Sandbox application is dedicated to Time Archive staging.
- The PayPal webhook URL points to:

```text
https://staging.time-archive.com/api/payments/paypal/webhooks
```

- Cloudflare routes the exact webhook path directly to the API container.
- The operator has AWS access to CloudWatch Logs and read-only staging database
  access through the approved operational path.

## Safety Rules

- Do not edit database rows during this drill.
- Do not resend PayPal Live events.
- Do not paste secrets, PayPal signatures, raw webhook payloads, session
  cookies, or credentials into GitHub issues, PRs, or committed documents.
- Record only safe identifiers:
  - PayPal event ID;
  - PayPal order ID;
  - PayPal capture ID;
  - Time Archive reservation ID;
  - Time Archive purchase ID;
  - Time Archive ownership record ID;
  - request ID;
  - HTTP status;
  - final local statuses.

## Drill Inputs

Fill these values before running the drill:

| Field | Value |
| --- | --- |
| Staging deployment commit SHA | `TBD` |
| PayPal Sandbox webhook event ID | `TBD` |
| PayPal Sandbox order ID | `TBD` |
| PayPal Sandbox capture ID | `TBD` |
| Time Archive reservation ID | `TBD` |
| Original webhook request ID | `TBD`, if known |

The PayPal event ID should be the same `PAYMENT.CAPTURE.COMPLETED` event that
already completed ownership successfully once.

## Step 1: Capture Baseline State

Before resending the PayPal event, confirm the local payment state is already
completed.

Run read-only SQL against the staging database:

```sql
select
  id,
  provider,
  provider_event_id,
  event_type,
  status,
  processed_at
from payment_events
where provider = 'paypal'
  and provider_event_id = '<paypal-webhook-event-id>';
```

Expected:

- exactly one row;
- `status = 'PROCESSED'`;
- `processed_at is not null`.

Then confirm there is only one purchase for the reservation:

```sql
select
  id,
  reservation_id,
  status,
  payment_provider,
  payment_reference
from purchases
where reservation_id = '<reservation-id>';
```

Expected:

- exactly one row;
- `status = 'OWNERSHIP_GRANTED'`;
- `payment_provider = 'paypal'`;
- `payment_reference = '<paypal-capture-id>'`.

Then confirm there is only one active ownership record for that purchase:

```sql
select
  id,
  source_purchase_id,
  owner_id,
  start_second,
  end_second,
  status,
  valid_until
from ownership_records
where source_purchase_id = '<purchase-id>';
```

Expected:

- exactly one row;
- `status = 'ACTIVE'`;
- `valid_until is null`.

## Step 2: Resend The PayPal Sandbox Event

In PayPal Developer Dashboard:

1. Open the Time Archive staging Sandbox application.
2. Open webhook events.
3. Find the target `PAYMENT.CAPTURE.COMPLETED` event.
4. Confirm the event belongs to the Time Archive staging webhook URL.
5. Use the Dashboard resend action for that event.

The operator must confirm that the resend targets the staging URL, not another
project or production URL.

## Step 3: Confirm PayPal Delivery Result

In PayPal Developer Dashboard, inspect the new transmission for the resent
event.

Expected:

- webhook URL is `https://staging.time-archive.com/api/payments/paypal/webhooks`;
- HTTP status is `200`;
- response body indicates the event was accepted;
- if visible, `alreadyProcessed` is `true`.

If PayPal shows `Pending`, wait briefly and refresh before treating it as a
failure. If PayPal shows a non-2xx result, stop the drill and inspect
CloudWatch before resending again.

## Step 4: Search CloudWatch Logs

Search the API log group for the PayPal event ID:

```bash
aws logs filter-log-events \
  --log-group-name /time-archive/staging/api \
  --filter-pattern '"<paypal-webhook-event-id>"' \
  --max-items 50
```

Expected:

- a safe PayPal webhook verification log for the event;
- a safe API request completion log for the webhook request;
- HTTP status `200`;
- no raw PayPal payload, signature, secrets, cookies, CSRF token, or
  authorization header in returned log messages.

If a request ID is visible, search by that request ID as well:

```bash
aws logs filter-log-events \
  --log-group-name /time-archive/staging/api \
  --filter-pattern '"<request-id>"' \
  --max-items 50
```

## Step 5: Confirm Idempotent Database State

Run the same read-only SQL checks from Step 1 after the resend.

Expected post-resend state:

- `payment_events` still has exactly one PayPal row for the event ID;
- the payment event remains `PROCESSED`;
- `purchases` still has exactly one row for the reservation;
- `ownership_records` still has exactly one active row for the purchase;
- no duplicate ownership record exists for the same second range and owner.

Optional duplicate-range check:

```sql
select
  owner_id,
  start_second,
  end_second,
  count(*) as active_count
from ownership_records
where status = 'ACTIVE'
  and valid_until is null
  and start_second = <start-second>
  and end_second = <end-second>
group by owner_id, start_second, end_second;
```

Expected:

- `active_count = 1`.

## Pass Criteria

The drill passes only if all are true:

- PayPal resend transmission returns HTTP `200`.
- CloudWatch shows safe webhook handling and request completion logs.
- `payment_events` has no duplicate event row.
- `purchases` has no duplicate purchase row for the reservation.
- `ownership_records` has no duplicate active ownership row for the purchase or
  time range.
- No sensitive PayPal or credential material appears in reviewed logs.

## Failure Handling

If the webhook returns non-2xx:

1. Do not resend repeatedly.
2. Capture the PayPal event ID, transmission timestamp, HTTP status, and Time
   Archive request ID if available.
3. Search CloudWatch for `paypalWebhookReason`.
4. Compare staging SSM PayPal `webhook-id` with the Sandbox application
   webhook ID without printing secret values.

If duplicate local records are found:

1. Stop payment launch work.
2. Do not manually delete records.
3. Preserve database state for investigation.
4. Open a blocking fix task for payment idempotency.

## Result Record Template

Copy this section into a future implementation plan or release readiness update
after the drill is complete.

```text
Date:
Operator:
Staging deployment commit SHA:
PayPal Sandbox event ID:
PayPal order ID:
PayPal capture ID:
Reservation ID:
Purchase ID:
Ownership record ID:
PayPal resend HTTP status:
Time Archive request ID:
CloudWatch event search result:
Payment event row count:
Purchase row count for reservation:
Ownership row count for purchase:
Sensitive log review result:
Outcome: PASS | FAIL
Notes:
```

## Release Readiness Impact

After a passing drill, update
`docs/operations/release-readiness-checklist.md`:

- change `Payment idempotency` from `Needs staging verification` to
  `Ready for staging`;
- record the PayPal event ID, request ID, and database row-count result in a
  repository-safe implementation plan;
- keep `Production PayPal live setup` blocked until live app, live webhook,
  first low-value payment, refund, and reconciliation drills pass.
