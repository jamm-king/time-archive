# Add Local Purchase Flow Verification

## Objective

Add repeatable local purchase flow verification scripts and documentation.

The backend can now run through Docker Compose and exposes the full local fake purchase flow through HTTP. This task adds a canonical shell script for CI-friendly verification, a PowerShell script for Windows local convenience, and a manual verification document.

## Scope

- Add `scripts/verify-local-purchase-flow.sh`.
- Add `scripts/verify-local-purchase-flow.ps1`.
- Add `docs/manual-verification/local-purchase-flow.md`.
- Update README with verification instructions.
- Verify the scripts against the local Docker Compose stack.

## Out of Scope

- Adding a GitHub Actions job.
- Resetting or truncating the local database automatically.
- Adding real payment provider verification.
- Adding frontend verification.
- Adding media upload or moderation verification.

## Relevant Files

- `scripts/verify-local-purchase-flow.sh`
- `scripts/verify-local-purchase-flow.ps1`
- `docs/manual-verification/local-purchase-flow.md`
- `README.md`
- `docs/implementation-plan/2026-06-17/add-local-purchase-flow-verification.md`

## Key Design Decisions

- Treat the shell script as the canonical verification script for future CI use.
- Use Python 3 for JSON parsing in the shell script to avoid requiring `jq`.
- Fall back to `python` when `python3` is unavailable or points to a non-functional Windows Store alias.
- Keep the PowerShell script behavior aligned with the shell script.
- Make `BASE_URL`, `BUYER_ID`, `START_SECOND`, and `END_SECOND` configurable.
- Do not clean the database automatically. The script should fail clearly if the selected range is already unavailable.
- Verify idempotency by submitting the same fake payment event twice.

## Verification Flow

1. Check `/actuator/health`.
2. Check availability for the configured range.
3. Create reservation.
4. Create checkout.
5. Submit fake payment webhook.
6. Submit the same fake payment webhook again.
7. Confirm the duplicate event returns `alreadyProcessed = true`.
8. Confirm availability for the same range is now false.

## Verification Plan

- Run the full backend stack with `docker compose up -d --build`.
- Run the PowerShell script locally.
- Run the shell script locally with Git Bash.
- Run `.\gradlew.bat test`.
- Run `.\gradlew.bat build`.

## Risks and Rollback Strategy

- Risk: The default range can already be owned in a reused local database.
  - Mitigation: Allow overriding `START_SECOND` and `END_SECOND`.
- Risk: Maintaining two scripts can drift.
  - Mitigation: Keep both scripts small and document shell as canonical.
- Risk: Shell script dependencies differ between Windows and CI.
  - Mitigation: Use `curl` and `python3`, which are available on GitHub-hosted Ubuntu runners.
- Rollback: Remove the scripts and documentation.

## Progress

- [x] Latest `main` pulled.
- [x] Implementation branch created.
- [x] Implementation plan created.
- [x] Shell verification script added.
- [x] PowerShell verification script added.
- [x] Manual verification document added.
- [x] README updated.
- [x] Scripts verified locally.
- [x] Gradle verification commands run.
- [x] Completion details recorded.

## Implementation Notes

- Added `scripts/verify-local-purchase-flow.sh` as the canonical CI-friendly verification script.
- Added `scripts/verify-local-purchase-flow.ps1` for Windows local convenience.
- Added `docs/manual-verification/local-purchase-flow.md`.
- Updated README with verification commands.
- The shell script uses `curl` and Python JSON parsing instead of `jq`.
- The shell script supports both `python3` and `python`.
- Both scripts allow overriding the selected range.

## Verification Results

- `powershell -ExecutionPolicy Bypass -File .\scripts\verify-local-purchase-flow.ps1 -StartSecond 100 -EndSecond 110`: passed.
- `C:\Program Files\Git\bin\bash.exe -lc "cd /d/develop/time-archive && START_SECOND=110 END_SECOND=120 ./scripts/verify-local-purchase-flow.sh"`: passed.
- `.\gradlew.bat test`: passed.
- `.\gradlew.bat build`: passed.

## Completion Summary

Local purchase flow verification scripts and documentation were added. The scripts verify health, availability, reservation creation, checkout creation, fake payment completion, duplicate webhook idempotency, and final range unavailability.

## Files Changed

- `README.md`
- `docs/implementation-plan/2026-06-17/add-local-purchase-flow-verification.md`
- `docs/manual-verification/local-purchase-flow.md`
- `scripts/verify-local-purchase-flow.sh`
- `scripts/verify-local-purchase-flow.ps1`

## Known Limitations

- Scripts do not reset local database state.
- The selected range must be available before running.
- The shell script is intended for CI and Unix-like shells; on Windows, Git Bash works, while WSL requires a configured Linux distribution.

## Follow-Up Recommendations

- Add a GitHub Actions job that starts Docker Compose and runs `scripts/verify-local-purchase-flow.sh`.
- Add optional database cleanup only if a dedicated verification database is introduced.
