# Staging Security Verification

## Objective

Batch the remaining staging security verification work so the project can avoid
repeated CI, image publishing, and staging deployment cycles for small
release-readiness checks.

## Scope

- Add application-level browser security headers for the public Web entrypoint.
- Add a manual staging smoke workflow for public security headers.
- Add CI policy validation for the new manual smoke workflow.
- Reuse existing staging auth and admin smoke workflows for session, CSRF, and
  admin authorization evidence.
- Update release-readiness and staging deployment documentation.

## Out Of Scope

- Cloudflare WAF or edge rate-limit configuration.
- Payment provider integration.
- Production secret provisioning or rotation.
- Error tracking product integration.

## Relevant Files Or Modules

- `apps/web/next.config.ts`
- `.github/workflows/ci.yml`
- `.github/workflows/smoke-staging-security-headers.yml`
- `scripts/verify-staging-security-headers.sh`
- `scripts/verify-staging-security-headers-workflow.sh`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/staging-deployment.md`

## Key Design Decisions

- Security headers are applied in the Next.js Web layer because Cloudflare Tunnel
  routes public browser traffic to the Web service, and API calls are exposed
  through the same-origin Web proxy.
- The Content Security Policy is intentionally narrow and low-risk for the MVP:
  `frame-ancestors`, `object-src`, and `base-uri` are enforced without script
  restrictions that could break Next.js runtime behavior.
- The new smoke workflow is manual-only, runs only from `main`, uses the
  `staging` environment, and does not request secrets or OIDC permissions.
- Existing auth and admin smoke workflows remain the source of truth for session
  cookie attributes, CSRF rejection, login/logout, and admin authorization.

## Step-By-Step Execution Plan

- [x] Inspect current staging readiness checklist and existing smoke coverage.
- [x] Create a dedicated branch from the latest `main`.
- [x] Create this implementation plan.
- [x] Add Web security headers.
- [x] Add staging security header smoke script.
- [x] Add manual GitHub Actions workflow for the smoke script.
- [x] Add CI static workflow policy validation.
- [x] Update staging deployment docs and release-readiness checklist.
- [x] Run focused local verification.
- [x] Record final completion details.

## Risks And Rollback Strategy

- Risk: overly strict CSP can break Next.js runtime behavior.
  - Mitigation: limit CSP to frame/object/base restrictions only.
  - Rollback: remove the `headers()` block from `apps/web/next.config.ts`.
- Risk: the smoke workflow could accidentally run automatically or gain broader
  permissions.
  - Mitigation: CI policy validation checks trigger, permissions, environment,
    pinned checkout action, and script behavior.
- Risk: security headers may differ if Cloudflare adds, removes, or overrides
  headers.
  - Mitigation: verify through the public HTTPS hostname after deployment.

## Verification Plan

- Run `./scripts/verify-staging-security-headers-workflow.sh`.
- Run `npm run lint` and `npm run build` in `apps/web`.
- If network access is available, run:

```bash
./scripts/verify-staging-security-headers.sh \
  --base-url https://staging.time-archive.com
```

- After merge and deployment, run these manual workflows from `main`:
  - `Smoke staging auth`
  - `Smoke staging admin`
  - `Smoke staging security headers`

## Open Questions

- None for this batch. Cloudflare edge abuse controls remain a separate
  environment configuration task.

## Progress

- 2026-07-01: Created branch `feature/staging-security-verification`.
- 2026-07-01: Confirmed existing staging auth smoke checks CSRF rejection,
  session cookie attributes, registration, login, logout, and `/api/me`.
- 2026-07-01: Confirmed existing staging admin smoke checks unauthenticated
  rejection, regular-user rejection, and admin moderation-list access.
- 2026-07-01: Added Web-layer security headers in `apps/web/next.config.ts`.
- 2026-07-01: Added manual staging security headers smoke script and workflow.
- 2026-07-01: Added CI policy validation for the new manual workflow.
- 2026-07-01: `npm.cmd run lint` in `apps/web` passed.
- 2026-07-01: `npm.cmd run build` in `apps/web` passed.
- 2026-07-01: `git diff --check` passed.
- 2026-07-01: Local shell syntax checks passed when run through Git Bash.
- 2026-07-01: Local execution of the workflow policy verifier was skipped
  because the local Python installation does not include PyYAML. CI installs
  PyYAML through the existing CloudFormation validator requirements before
  running workflow policy checks.
- 2026-07-01: After the change was merged, staging images were republished,
  staging was redeployed, and the manual `Smoke staging security headers`
  workflow passed against the public HTTPS hostname.

## Completion Summary

The staging security verification batch is implemented. The public Web layer now
emits security headers for all routes, including same-origin API proxy routes.
A new manual `Smoke staging security headers` workflow verifies the deployed
HTTPS Web root and public timeline proxy headers without using secrets or AWS
permissions. CI now validates the workflow policy and script coverage.

## Files Changed

- `.github/workflows/ci.yml`
- `.github/workflows/smoke-staging-security-headers.yml`
- `apps/web/next.config.ts`
- `docs/implementation-plan/2026-07-01/staging-security-verification.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/staging-deployment.md`
- `scripts/verify-staging-security-headers.sh`
- `scripts/verify-staging-security-headers-workflow.sh`

## Tests Run And Results

- `npm.cmd run lint` in `apps/web`: passed.
- `npm.cmd run build` in `apps/web`: passed.
- `git diff --check`: passed.
- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-staging-security-headers.sh`:
  passed.
- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-staging-security-headers-workflow.sh`:
  passed.

## Manual Verification Results

After the change was merged and staging was redeployed, the manual
`Smoke staging security headers` workflow passed against the public HTTPS
hostname. This confirms that the Web root and public timeline proxy responses
include the expected HSTS, frame policy, content type sniffing protection,
referrer policy, minimal CSP, and browser permission headers.

## Known Limitations

- Cloudflare edge abuse controls and direct-origin restriction remain separate
  environment tasks.

## Follow-Up Recommendations

- Repeat `Smoke staging security headers` after Web routing, Cloudflare, or
  header-policy changes.
