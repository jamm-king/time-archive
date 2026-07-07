# Add Production Public Smoke Workflows

## Objective

Add manual production public smoke checks for the first production deployment:
public Web/timeline, security headers, and authentication/session behavior.

## Scope

- Add production public smoke script and workflow.
- Add production security headers smoke script and workflow.
- Add production auth smoke script and workflow.
- Add CI policy checks for the new production smoke workflows.
- Update release readiness and first-deploy documentation.

Out of scope:

- Production R2 upload/preview smoke.
- Production PayPal Live smoke.
- Admin production smoke.
- Running the GitHub Actions workflows.

## Relevant Files Or Modules

- `scripts/verify-production-public-smoke.sh`
- `scripts/verify-production-security-headers.sh`
- `scripts/verify-production-auth-smoke.sh`
- `.github/workflows/smoke-production-public.yml`
- `.github/workflows/smoke-production-security-headers.yml`
- `.github/workflows/smoke-production-auth.yml`
- `scripts/verify-production-public-smoke-workflows.sh`
- `.github/workflows/ci.yml`
- `docs/operations/production-first-deploy-runbook.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- Production smoke workflows are manual `workflow_dispatch` only.
- They use `contents: read` only and do not request GitHub OIDC.
- They use the GitHub `production` environment for visibility and approval
  consistency, but they do not access secrets.
- The production base URL defaults to `PRODUCTION_PUBLIC_BASE_URL` and may be
  overridden by workflow input.
- Auth smoke creates a disposable production user with a
  `production-auth-smoke-` email prefix.

## Step-By-Step Execution Plan

- [x] Confirm clean `main` and create a dedicated feature branch.
- [x] Review staging public, security headers, auth smoke scripts and workflow
  policy checks.
- [x] Add production smoke scripts.
- [x] Add production smoke workflows.
- [x] Add production workflow policy checks.
- [x] Wire checks into CI.
- [x] Update operations documentation and release checklist.
- [x] Run local production smoke scripts against `https://time-archive.com`.
- [x] Run policy and syntax checks.
- [x] Record completion summary.

## Risks And Rollback Strategy

- Risk: Auth smoke creates disposable production users.
  Mitigation: use a clearly identifiable `production-auth-smoke-` email prefix
  and avoid admin or payment actions.
- Risk: Workflow accidentally reads secrets or assumes AWS roles.
  Mitigation: policy checks reject `secrets.*`, OIDC permissions, and literal
  AWS account IDs.
- Risk: Smoke checks mutate production beyond registration/login.
  Mitigation: public and security-header checks are read-only; auth smoke only
  exercises standard user registration/session endpoints.

Rollback:

- Revert this branch before workflow execution if policy checks fail.
- If a production smoke workflow fails after merge, do not proceed to R2 or
  PayPal Live drills until the failing route or auth behavior is understood.

## Verification Plan

- Run production smoke scripts locally against `https://time-archive.com`.
- Run production workflow policy scripts.
- Run shell syntax checks.
- Run `git diff --check`.

## Open Questions

- None for initial public smoke. R2 and PayPal Live smoke remain separate
  follow-up tasks.

## Progress

- 2026-07-07: Created branch `feature/production-public-smoke-workflows`.
- 2026-07-07: Reviewed staging public, security headers, auth smoke scripts,
  workflows, and workflow policy checks.
- 2026-07-07: Added production public, security headers, and auth smoke scripts.
- 2026-07-07: Added manual production public, security headers, and auth smoke
  workflows using the GitHub `production` environment and
  `PRODUCTION_PUBLIC_BASE_URL`.
- 2026-07-07: Added a combined production public smoke workflow policy checker
  and wired it into CI.
- 2026-07-07: Updated production first-deploy runbook and release readiness
  checklist.
- 2026-07-07: Ran production public smoke and auth smoke successfully against
  `https://time-archive.com`. Auth smoke created one disposable
  `production-auth-smoke-` user.
- 2026-07-07: Initial production security headers smoke failed locally because
  Windows Git Bash resolved `python3` to the WindowsApps Python stub. Updated
  the production security headers script to select only a Python executable
  that can run `python -c 'import re'`, then reran the smoke successfully.
- 2026-07-07: Ran the combined production smoke workflow policy checker using
  a temporary PyYAML virtualenv. Shell syntax checks and `git diff --check`
  passed.

## Completion Summary

Production public, security headers, and auth smoke checks are now automated as
manual GitHub Actions workflows. Each workflow is main-branch-only, uses the
GitHub `production` environment, reads only `PRODUCTION_PUBLIC_BASE_URL`, and
does not request secrets or OIDC credentials.

Local smoke execution against `https://time-archive.com` passed for Web root,
public timeline, security headers, CSRF rejection, disposable registration,
secure session cookies, logout, login, and `/api/me`.

## Files Changed

- `.github/workflows/smoke-production-public.yml`
- `.github/workflows/smoke-production-security-headers.yml`
- `.github/workflows/smoke-production-auth.yml`
- `.github/workflows/ci.yml`
- `scripts/verify-production-public-smoke.sh`
- `scripts/verify-production-security-headers.sh`
- `scripts/verify-production-auth-smoke.sh`
- `scripts/verify-production-public-smoke-workflows.sh`
- `docs/operations/production-first-deploy-runbook.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-07/add-production-public-smoke-workflows.md`

## Tests Run And Results

- `scripts/verify-production-public-smoke.sh --base-url https://time-archive.com`
  passed.
- `scripts/verify-production-security-headers.sh --base-url https://time-archive.com`
  passed after the Python detection fix.
- `scripts/verify-production-auth-smoke.sh --base-url https://time-archive.com`
  passed and created a disposable production smoke user.
- `scripts/verify-production-public-smoke-workflows.sh` passed using a
  temporary local PyYAML virtualenv.
- `bash -n scripts/verify-production-public-smoke.sh
  scripts/verify-production-security-headers.sh
  scripts/verify-production-auth-smoke.sh
  scripts/verify-production-public-smoke-workflows.sh` passed.
- `git diff --check` passed.

## Manual Verification Results

- Confirmed production Web root responds through the public HTTPS hostname.
- Confirmed production public timeline responds through the public HTTPS
  hostname.
- Confirmed production security headers are present on Web and public API proxy
  responses.
- Confirmed production auth session cookies include `HttpOnly`, `Secure`, and
  `SameSite=Lax`.

## Known Limitations

- The GitHub Actions workflows have not been run yet; local script execution
  passed against production.
- R2 upload/preview and PayPal Live drills are not covered by this task.
- Disposable production smoke users are not automatically cleaned up.

## Follow-Up Recommendations

- Add repository variable `PRODUCTION_PUBLIC_BASE_URL=https://time-archive.com`
  if it is not already present.
- After merge, run the three production smoke workflows from `main`.
- Continue with production R2 upload/preview smoke after these workflows pass
  in GitHub Actions.
