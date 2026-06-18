# Local Purchase Flow Verification

This document describes how to verify the local development-stage primary purchase flow.

Verification scripts are maintained as shell scripts. They run in GitHub Actions
on Ubuntu and on Windows through Git Bash.

## Prerequisites

- Docker
- Docker Compose
- A running Time Archive backend at `http://localhost:8080`
- For the shell script:
  - Bash
  - `curl`
  - `python3` or `python`

## Start the Local Stack

```bash
docker compose up -d --build
```

Verify the API is healthy:

```bash
curl http://localhost:8080/actuator/health
```

## Run the Shell Script

```bash
./scripts/verify-local-purchase-flow.sh
```

Override the tested range when the default range is already unavailable:

```bash
START_SECOND=200 END_SECOND=210 ./scripts/verify-local-purchase-flow.sh
```

When the API container starts slowly, override the health wait timeout:

```bash
HEALTH_TIMEOUT_SECONDS=180 ./scripts/verify-local-purchase-flow.sh
```

On Windows, run the shell script from Git Bash.

## Verified Steps

The scripts verify:

1. API health is `UP`.
2. A local user is registered and authenticated through a session cookie.
3. The selected range is initially available.
4. Reservation creation succeeds using server-side session identity.
5. Checkout creation succeeds.
6. Fake payment webhook completion succeeds.
7. Replaying the same fake webhook returns `alreadyProcessed = true`.
8. The selected range becomes unavailable after ownership is created.

## Common Failures

### Selected Range Is Not Available

The scripts do not reset local database state. Use another range:

```bash
START_SECOND=300 END_SECOND=310 ./scripts/verify-local-purchase-flow.sh
```

### API Is Not Running

Start the full stack:

```bash
docker compose up -d --build
```

Then check:

```bash
docker compose ps
```

### Database Was Reused

Local data persists in the Docker named volume. This is intentional. Do not delete volumes unless local data loss is acceptable.

## Production Warning

This flow uses development-stage APIs:

- fake payment webhook endpoint

Production payment confirmation must use verified provider webhooks.
