# Add Local Auth Verification Scripts

## Objective

Add shell verification scripts for local authentication behavior and the current
user owned-ranges read path.

## Scope

- Add `scripts/verify-local-auth-flow.sh`.
- Add `scripts/verify-local-auth-owned-ranges-flow.sh`.
- Keep scripts compatible with GitHub Actions Ubuntu and Windows Git Bash.
- Document the scripts in `README.md`.

Out of scope:

- Adding the scripts to GitHub Actions required checks in this change.
- Adding Playwright or browser automation.
- Adding purchase UI or upload UI verification.

## Relevant Files Or Modules

- `scripts/verify-local-auth-flow.sh`
- `scripts/verify-local-auth-owned-ranges-flow.sh`
- `README.md`
- Existing local verification scripts under `scripts/`

## Key Design Decisions

- Scripts target the web origin by default through `BASE_URL=http://localhost:3000`
  so they verify the Next.js proxy, CSRF forwarding, session cookies, and API
  behavior together.
- Scripts use `curl` and Python JSON parsing to stay consistent with existing
  local verification scripts.
- `verify-local-auth-flow.sh` covers register, current user lookup, logout,
  post-logout 401, login, and current user lookup.
- `verify-local-auth-owned-ranges-flow.sh` covers register, current user lookup,
  and current user's owned range list. It validates the empty-list case for a
  newly registered user.

## Step-By-Step Execution Plan

- [x] Inspect existing verification scripts and CI conventions.
- [x] Add this implementation plan.
- [x] Add auth flow verification script.
- [x] Add auth-owned-ranges verification script.
- [x] Update README usage instructions.
- [x] Run shell syntax checks.
- [x] Run scripts against a local Docker Compose stack if practical.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: Scripts may be too web-origin-specific for API-only stacks.
  - Mitigation: Default to web origin intentionally and expose `BASE_URL` for
    overrides.
- Risk: Empty owned-ranges verification may not catch non-empty rendering issues.
  - Mitigation: Keep it as a session/proxy/API smoke and expand after purchase
    or upload UI can create owned ranges through the web origin.

Rollback:

- Remove the two scripts and README entries. No application code or database
  state changes are required.

## Verification Plan

- `bash -n scripts/verify-local-auth-flow.sh`
- `bash -n scripts/verify-local-auth-owned-ranges-flow.sh`
- Run both scripts against `docker compose up -d --build`.
- Run `git diff --check`.

## Open Questions

- None.

## Progress Log

- 2026-06-18: Confirmed `main` is up to date and already contains
  `GET /api/me/owned-ranges`.
- 2026-06-18: Created `feature/local-auth-verification-scripts` from `main`.
- 2026-06-18: Added both shell scripts and README usage entries.
- 2026-06-18: Verified both scripts with Git Bash syntax checks and against a
  local Docker Compose stack through `BASE_URL=http://localhost:3000`.

## Completion Summary

Added two local shell verification scripts:

- `verify-local-auth-flow.sh` verifies CSRF retrieval, registration, `/api/me`,
  logout, post-logout unauthorized behavior, login, and final `/api/me`.
- `verify-local-auth-owned-ranges-flow.sh` verifies CSRF retrieval,
  registration, `/api/me`, and an empty current-user owned range list for a new
  user.

Both scripts default to the web origin so they cover the Next.js proxy, session
cookie handling, CSRF forwarding, and backend API behavior together.

## Files Changed

- `scripts/verify-local-auth-flow.sh`
- `scripts/verify-local-auth-owned-ranges-flow.sh`
- `README.md`
- `docs/implementation-plan/2026-06-18/add-local-auth-verification-scripts.md`

## Tests Run And Results

- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-local-auth-flow.sh`:
  passed.
- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-local-auth-owned-ranges-flow.sh`:
  passed.
- `git diff --check`: passed.

The plain `bash` command was not usable on this Windows environment because it
resolved to WSL without a default distro. Git Bash was used explicitly instead.

## Manual Verification Results

- `docker compose up -d --build`: passed.
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-local-auth-flow.sh`:
  passed.
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-local-auth-owned-ranges-flow.sh`:
  passed.
- `docker compose down`: completed after verification.

## Known Limitations

- `verify-local-auth-owned-ranges-flow.sh` currently verifies the empty owned
  range list for a new user. Non-empty ownership through the web origin should
  be added after purchase UI or a web purchase proxy exists.
- These scripts are documented but not yet wired into GitHub Actions.

## Follow-Up Recommendations

- Add both scripts to GitHub Actions after the current PR is merged and the
  additional runtime cost is acceptable.
