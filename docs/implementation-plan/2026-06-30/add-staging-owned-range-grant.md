# Add Staging Owned Range Grant

## Objective

Add a controlled staging operator script that grants an existing staging user an
owned time range with `ADMIN_GRANT` acquisition type, so staging media upload and
moderation smoke tests can run without enabling fake payments.

## Scope

- Add a staging-only owned range grant script.
- Add static validation for the script contract.
- Add CI coverage for the static validation.
- Document the staging owned range grant process and production boundary.

Out of scope:

- Actually granting a range in staging from this branch.
- Adding production grants.
- Adding media upload/admin approval smoke automation.
- Implementing real payment provider behavior.

## Relevant Files Or Modules

- `scripts/grant-staging-owned-range.sh`
- `scripts/verify-staging-owned-range-grant.sh`
- `.github/workflows/ci.yml`
- `docs/operations/staging-owned-range-grants.md`
- `docs/operations/staging-deployment.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- The script is staging-only and requires `--expected-account-id`.
- The target user must already exist through normal registration.
- The grant uses `acquisition_type = 'ADMIN_GRANT'`.
- The script validates the 24-hour canonical range bounds.
- The script fails if the target range overlaps any active ownership record.
- The script is idempotent only for the same user, range, active status, and
  `ADMIN_GRANT` acquisition type.
- The EC2 instance reads existing staging runtime DB credentials from SSM.
- Actual execution is a staging data mutation and must be explicitly approved
  before running without `--dry-run`.

## Step-by-step Execution Plan

1. Inspect the current ownership schema and existing SSM operator scripts.
2. Add this implementation plan.
3. Add the staging owned range grant script.
4. Add static validation for the script.
5. Add CI validation.
6. Update operations documentation.
7. Run local validation and dry-run if AWS credentials are available.

## Risks And Rollback Strategy

- Risk: granting an occupied second range. Mitigation: pre-check overlap and
  rely on the database exclusion constraint.
- Risk: granting ownership to the wrong user. Mitigation: require exact email,
  normalize to lowercase, fail when the user does not exist, and print only the
  selected ownership id/email/range/acquisition type.
- Risk: using payment-like records for test data. Mitigation: use
  `ADMIN_GRANT`, leave purchase/payment tables untouched, and document the
  staging-only purpose.
- Rollback: revoke the created ownership record by setting status to `REVOKED`
  and `valid_until` to the current timestamp in a separately reviewed operation.

## Verification Plan

- Run shell syntax validation.
- Run `scripts/verify-staging-owned-range-grant.sh`.
- Run `scripts/grant-staging-owned-range.sh --dry-run` with staging account
  inputs if AWS SSO is available.
- Run `git diff --check`.

## Open Questions

- None.

## Progress

- Created the dedicated branch from latest `main`.
- Added the staging owned range grant script.
- Added static validation and CI coverage for the script contract.
- Documented the staging owned range grant runbook and production boundary.

## Completion Summary

Added a staging-only operator script that grants an existing registered user an
active owned range using `ADMIN_GRANT`. The script validates AWS account,
region, staging stack outputs, runtime parameter path, email, and canonical
range bounds. It renders and sends an SSM Run Command only when not in dry-run
mode.

The remote operation reads the existing staging application database credentials
from SSM on EC2, requires the target user to exist, reuses an identical active
`ADMIN_GRANT` record when present, fails on active overlap, and inserts a new
ownership record only when the range is free.

## Files Changed

- `scripts/grant-staging-owned-range.sh`
- `scripts/verify-staging-owned-range-grant.sh`
- `.github/workflows/ci.yml`
- `docs/operations/staging-owned-range-grants.md`
- `docs/operations/staging-deployment.md`
- `docs/operations/release-readiness-checklist.md`

## Tests Run And Results

- `bash -n scripts/grant-staging-owned-range.sh`: passed.
- `bash -n scripts/verify-staging-owned-range-grant.sh`: passed.
- `scripts/verify-staging-owned-range-grant.sh`: passed.
- `scripts/grant-staging-owned-range.sh --expected-account-id 231851555445 --profile time-archive-staging-admin --email jmcylove@gmail.com --start-second 7000 --end-second 7001 --dry-run`: passed.
- `git diff --check`: passed.

## Manual Verification Results

The staging dry-run resolved the expected account, stack outputs, EC2 instance,
target email, and range, then rendered the SSM command payload without sending
it.

No SSM command was sent and no database changes were made.

## Known Limitations

- The script grants ownership only; revocation remains a separate reviewed
  operation.
- Actual staging owned range grants require explicit approval because they
  mutate staging data.
- Production ownership remains tied to the future real payment provider flow.

## Follow-up Recommendations

- After merge, grant a short unused staging range to a smoke-test user.
- Build staging media upload/moderation/public timeline smoke automation on top
  of the granted range.
