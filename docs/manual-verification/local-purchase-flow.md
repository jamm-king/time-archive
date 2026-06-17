# Local Purchase Flow Verification

This document describes how to verify the local development-stage primary purchase flow.

The shell script is the canonical script for future CI integration. The PowerShell script is provided for Windows local convenience.

## Prerequisites

- Docker
- Docker Compose
- A running Time Archive backend at `http://localhost:8080`
- For the shell script:
  - Bash
  - `curl`
  - `python3` or `python`
- For the PowerShell script:
  - PowerShell 7 or Windows PowerShell

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

## Run the PowerShell Script

```powershell
.\scripts\verify-local-purchase-flow.ps1
```

If PowerShell script execution is blocked by local execution policy:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-local-purchase-flow.ps1
```

Override the tested range:

```powershell
.\scripts\verify-local-purchase-flow.ps1 -StartSecond 200 -EndSecond 210
```

## Verified Steps

The scripts verify:

1. API health is `UP`.
2. The selected range is initially available.
3. Reservation creation succeeds.
4. Checkout creation succeeds.
5. Fake payment webhook completion succeeds.
6. Replaying the same fake webhook returns `alreadyProcessed = true`.
7. The selected range becomes unavailable after ownership is created.

## Common Failures

### Selected Range Is Not Available

The scripts do not reset local database state. Use another range:

```bash
START_SECOND=300 END_SECOND=310 ./scripts/verify-local-purchase-flow.sh
```

```powershell
.\scripts\verify-local-purchase-flow.ps1 -StartSecond 300 -EndSecond 310
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

- request-body `buyerId`
- fake payment webhook endpoint

Production payment confirmation must use verified provider webhooks, and buyer identity must come from authenticated server-side identity.
