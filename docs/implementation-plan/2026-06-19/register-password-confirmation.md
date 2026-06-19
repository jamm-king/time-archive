# Register Password Confirmation

## Objective

Add password confirmation to the frontend registration form so users cannot
submit a new account with an accidental password typo.

## Scope

- Add a `Confirm password` field only in register mode.
- Keep login mode unchanged.
- Disable register submit when password and confirmation do not match.
- Show a clear mismatch message.
- Keep the backend API contract unchanged; only submit `password` to the
  existing auth API.

Out of scope:

- Password reset.
- Email verification.
- Backend request DTO changes.
- New auth API endpoints.

## Relevant Files Or Modules

- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `docs/implementation-plan/2026-06-19/register-password-confirmation.md`

## Key Design Decisions

- Password confirmation is a frontend-only validation concern for this MVP.
- The mismatch message is shown only in register mode and only after the
  confirmation field has a value.
- Submit remains disabled while submitting or while register passwords do not
  match.

## Step-By-Step Execution Plan

- [x] Create a dedicated feature branch from latest `main`.
- [x] Add this implementation plan.
- [x] Add confirm password state and register-only input.
- [x] Disable register submit on mismatch.
- [x] Run frontend lint/build checks.
- [x] Run relevant auth verification script if practical.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: Login mode may be affected by register-only validation.
  - Mitigation: Gate mismatch logic by `mode === "register"`.
- Risk: Mismatch state can linger when switching modes.
  - Mitigation: Keep the mismatch check mode-scoped and reset confirmation when
    switching to login.

Rollback:

- Revert the component change and this implementation plan. No backend or data
  changes are involved.

## Verification Plan

- `npm.cmd run lint` in `apps/web`.
- `npm.cmd run build` in `apps/web`.
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-local-auth-flow.sh`
  against the local Docker Compose stack if practical.

## Open Questions

- None.

## Progress Log

- 2026-06-19: Confirmed `main` is up to date.
- 2026-06-19: Created `feature/register-password-confirmation`.
- 2026-06-19: Added register-only confirm password input, mismatch message,
  submit disabling, and submit-time guard.
- 2026-06-19: `npm.cmd run lint`, `npm.cmd run build`, `git diff --check`,
  Docker Compose full-stack build, and `verify-local-auth-flow.sh` passed.

## Completion Summary

Added a register-only `Confirm password` field to the frontend auth panel. The
register submit button is disabled until the password and confirmation match,
and submit handling also guards against mismatched values. Login mode is
unchanged, and the backend auth API contract remains unchanged.

## Files Changed

- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `docs/implementation-plan/2026-06-19/register-password-confirmation.md`

## Tests Run And Results

- `npm.cmd run lint`
  - Passed.
- `npm.cmd run build`
  - Passed.
- `git diff --check`
  - Passed.
- `docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d --build`
  - Passed.
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-local-auth-flow.sh`
  - Passed.
- `docker compose down`
  - Passed.

## Manual Verification Results

- The full-stack Docker build confirmed the changed web application builds in
  the same container path used by CI.
- The web-origin auth verification script confirmed registration, current-user
  lookup, logout, post-logout rejection, login, and final current-user lookup.

## Known Limitations

- This change does not add password reset or email verification.
- Browser-level click verification was not added.

## Follow-Up Recommendations

- Add email-based password reset in a later MVP phase.
- Consider extracting auth panel components if the auth UI grows further.
