# Add Local Auth Verification Scripts

## Objective

Add shell verification scripts for local authentication behavior and the current
user owned-ranges read path.

## Scope

- Add `scripts/verify-local-auth-flow.sh`.
- Add `scripts/verify-local-auth-owned-ranges-flow.sh`.
- Keep scripts compatible with GitHub Actions Ubuntu and Windows Git Bash.
- Add a GitHub Actions job that runs both scripts against the full local Docker
  Compose stack.
- Document the scripts in `README.md`.

Out of scope:

- Adding Playwright or browser automation.
- Adding purchase UI or upload UI verification.

## Relevant Files Or Modules

- `scripts/verify-local-auth-flow.sh`
- `scripts/verify-local-auth-owned-ranges-flow.sh`
- `.github/workflows/ci.yml`
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
- CI runs the two auth scripts in a single `local-auth-flows` job to avoid
  paying the Docker Compose startup cost twice.
- The CI job starts the full stack, not only the API service, because both
  scripts intentionally validate the web-origin proxy path.

## Step-By-Step Execution Plan

- [x] Inspect existing verification scripts and CI conventions.
- [x] Add this implementation plan.
- [x] Add auth flow verification script.
- [x] Add auth-owned-ranges verification script.
- [x] Update README usage instructions.
- [x] Run shell syntax checks.
- [x] Run scripts against a local Docker Compose stack if practical.
- [x] Record completion details.
- [x] Add both auth scripts to GitHub Actions.
- [x] Validate the workflow YAML shape.
- [x] Record CI wiring completion details.

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
- Validate `.github/workflows/ci.yml` after editing.
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
- 2026-06-18: Expanded the task scope to wire both scripts into GitHub Actions
  on the same branch.
- 2026-06-18: Added a combined `local-auth-flows` GitHub Actions job that
  starts the full Docker Compose stack and runs both auth verification scripts.
- 2026-06-18: Re-ran Git Bash syntax checks and `git diff --check`. Local YAML
  parser tools were unavailable, so workflow validation was limited to manual
  diff review against existing job structure.

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
- `.github/workflows/ci.yml`
- `docs/implementation-plan/2026-06-18/add-local-auth-verification-scripts.md`

## Tests Run And Results

- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-local-auth-flow.sh`:
  passed.
- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-local-auth-owned-ranges-flow.sh`:
  passed.
- `git diff --check`: passed.
- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-local-auth-flow.sh`
  after CI wiring: passed.
- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-local-auth-owned-ranges-flow.sh`
  after CI wiring: passed.
- `.github/workflows/ci.yml`: manually reviewed against existing CI job shape.

The plain `bash` command was not usable on this Windows environment because it
resolved to WSL without a default distro. Git Bash was used explicitly instead.
Ruby and Python PyYAML were not available locally, so workflow YAML parser
validation could not be run in this environment.

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
- The scripts are wired into GitHub Actions after the follow-up CI update in
  this branch.
- The new GitHub Actions job has not yet run remotely in this branch at the time
  of this local update.

## Follow-Up Recommendations

- Monitor first GitHub Actions runs to decide whether the combined auth job
  should remain separate from `local-web-smoke` or be folded into it later.
