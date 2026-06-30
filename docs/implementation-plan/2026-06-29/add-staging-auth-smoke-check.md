# Add Staging Auth Smoke Check

## Objective

Add a repeatable staging authentication smoke check that verifies the deployed
HTTPS authentication flow, session cookies, CSRF enforcement, login, logout, and
current-user lookup.

## Scope

- Add a shell script for staging public authentication smoke verification.
- Add a manually triggered GitHub Actions workflow for the staging auth smoke.
- Add static validation for the workflow contract.
- Update operations documentation and release readiness status.

Out of scope:

- Admin authorization verification.
- Media upload or purchase flows.
- Password reset or email verification.
- Cleaning up disposable staging users created by the smoke check.

## Relevant Files Or Modules

- `scripts/verify-staging-auth-smoke.sh`
- `scripts/verify-staging-auth-smoke-workflow.sh`
- `.github/workflows/smoke-staging-auth.yml`
- `.github/workflows/ci.yml`
- `docs/operations/staging-deployment.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- The workflow is manual only because it mutates staging by registering a
  disposable smoke-test user.
- The smoke check runs only against HTTPS URLs.
- Test users use a unique `staging-auth-smoke-...@example.com` address unless
  an explicit email is provided.
- The script verifies that mutation without a CSRF header is rejected before it
  performs authenticated requests with the fetched CSRF token.
- Session cookie verification checks for `HttpOnly`, `Secure`, and `SameSite`
  attributes on the deployed HTTPS response.

## Step-by-step Execution Plan

1. Inspect existing local auth verification.
2. Add the staging auth smoke script.
3. Add the manual GitHub Actions workflow.
4. Add CI static validation for the workflow and script.
5. Update operations documentation.
6. Run local static validation and, if feasible, run the script against staging.

## Risks And Rollback Strategy

- Risk: accumulating disposable users in staging. Mitigation: use a clear email
  prefix and document retention cleanup as a later data operation.
- Risk: accidentally weakening CSRF verification. Mitigation: assert the missing
  CSRF mutation fails before using the token.
- Risk: external staging downtime fails a workflow unrelated to code changes.
  Mitigation: keep the workflow manual and run only static policy validation in
  CI.
- Rollback: remove the workflow and scripts without affecting application
  runtime behavior.

## Verification Plan

- Run shell syntax validation.
- Run `scripts/verify-staging-auth-smoke-workflow.sh`.
- Run `git diff --check`.
- Optionally run `scripts/verify-staging-auth-smoke.sh` against the staging
  public URL.

## Open Questions

- None.

## Progress

- Created the dedicated branch from latest `main`.
- Added the staging auth smoke shell script.
- Added the manual GitHub Actions staging auth smoke workflow.
- Added CI static validation for the workflow contract.
- Updated operations documentation with the auth smoke verification path.
- Fixed default auth smoke environment export before request payload generation.

## Completion Summary

Added a repeatable staging authentication smoke verification path. The shell
script checks HTTPS-only auth behavior, CSRF token retrieval, rejection of a
mutation without `X-XSRF-TOKEN`, disposable user registration, secure session
cookie attributes, `/api/me`, logout, post-logout rejection, login, and final
current-user lookup.

The new manual GitHub Actions workflow runs the same check from `main` using
either a workflow input or the `STAGING_PUBLIC_BASE_URL` repository variable.
CI validates the workflow policy without contacting staging.

## Files Changed

- `.github/workflows/smoke-staging-auth.yml`
- `.github/workflows/ci.yml`
- `scripts/verify-staging-auth-smoke.sh`
- `scripts/verify-staging-auth-smoke-workflow.sh`
- `docs/operations/staging-deployment.md`
- `docs/operations/release-readiness-checklist.md`

## Tests Run And Results

- `bash -n scripts/verify-staging-auth-smoke.sh`: passed.
- `bash -n scripts/verify-staging-auth-smoke-workflow.sh`: passed.
- `scripts/verify-staging-auth-smoke-workflow.sh`: passed with local
  `PYTHONPATH=temp/cfn-lint`.
- `scripts/verify-staging-auth-smoke.sh --base-url https://staging.time-archive.com`: passed.
- `git diff --check`: passed.

## Manual Verification Results

The staging HTTPS auth smoke created one disposable
`staging-auth-smoke-...@example.com` user and verified CSRF enforcement,
registration, deployed session cookie attributes, `/api/me`, logout, and login.

## Known Limitations

- The workflow intentionally creates disposable staging users and does not clean
  them up.
- Admin authorization, media upload, and payment flows remain separate staging
  verification tasks.

## Follow-up Recommendations

- Run the manual `Smoke staging auth` workflow after each staging deployment
  that changes auth, CSRF, session, or proxy behavior.
- Define a staging retention cleanup process for disposable smoke-test users.
