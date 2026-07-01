# Cloudflare Edge Hardening

## Objective

Define and implement the first Cloudflare edge hardening baseline for staging
and production readiness: trusted client IP propagation, direct-origin
assumptions, cache bypass policy, WAF/rate-limit ownership, and public health
verification.

## Scope

- Forward reviewed Cloudflare request headers from the Web proxy to the API.
- Document the Cloudflare edge controls required for staging and production.
- Update runtime parameter guidance for `CF-Connecting-IP`.
- Add focused verification for the Web proxy header-forwarding behavior where
  practical.
- Update release readiness status without overstating manual Cloudflare
  dashboard work.

## Out Of Scope

- Creating or modifying Cloudflare dashboard resources directly.
- Production Cloudflare hostname creation.
- PayPal webhook Cloudflare validation.
- Full browser automation.

## Relevant Files Or Modules

- `apps/web/src/lib/backend-proxy.ts`
- `docs/operations/cloudflare-edge-hardening.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/staging-runtime-parameters.md`
- `docs/operations/ec2-rds-deployment-architecture.md`
- `docs/implementation-plan/2026-07-01/cloudflare-edge-hardening.md`

## Key Design Decisions

- Use `CF-Connecting-IP` as the application trusted client IP header after
  Cloudflare Tunnel is the only public application ingress. Cloudflare
  documents that this header provides the client IP address on traffic from
  Cloudflare edge to the origin.
- Do not trust `X-Forwarded-For` for application rate-limit identity because it
  can contain a chain of proxies and is less stable for this use case.
- Keep Cloudflare edge controls as complementary to Redis application rate
  limiting. Cloudflare handles coarse abuse and automated traffic; the
  application still enforces route-aware, user-aware limits.
- Keep all API and presigned URL responses out of shared Cloudflare cache.

## Step-By-Step Execution Plan

- [x] Inspect current rate-limit and runtime parameter behavior.
- [x] Create a dedicated branch from the latest `main`.
- [x] Create this implementation plan.
- [x] Forward reviewed Cloudflare headers through the Web proxy.
- [x] Add focused tests or static verification for header forwarding.
- [x] Update Cloudflare edge hardening runbook.
- [x] Update runtime parameter and release readiness documentation.
- [x] Run focused verification.
- [x] Record final completion details.

## Risks And Rollback Strategy

- Risk: Forwarding a client-supplied `CF-Connecting-IP` from a non-Cloudflare
  path could let clients influence rate-limit identity.
  - Mitigation: only set `TIME_ARCHIVE_RATE_LIMIT_CLIENT_IP_HEADER` after
    Cloudflare Tunnel is the only public ingress and host ports remain closed.
  - Rollback: unset `/time-archive/{environment}/rate-limit/client-ip-header`
    or revert Web proxy header forwarding.
- Risk: Cloudflare dashboard rules may drift from the runbook.
  - Mitigation: keep release readiness as `Needs verification` until the
    operator confirms the deployed edge rules.

## Verification Plan

- Run Web lint/build if Web code changes.
- Run backend tests if API code changes.
- Run `git diff --check`.
- Verify docs describe manual Cloudflare dashboard actions and repeat criteria.

## Open Questions

- None for this baseline. Exact Cloudflare paid-plan feature availability may
  vary by account plan and should be confirmed in the Cloudflare dashboard.

## Progress

- 2026-07-01: Created branch `feature/cloudflare-edge-hardening`.
- 2026-07-01: Confirmed API rate limiting already supports a configured trusted
  client IP header and has test coverage for `CF-Connecting-IP`.
- 2026-07-01: Confirmed the Web proxy did not forward `CF-Connecting-IP` to the
  API, so trusted client IP propagation was incomplete.
- 2026-07-01: Added Web proxy forwarding for reviewed Cloudflare headers:
  `CF-Connecting-IP`, `CF-Ray`, `CF-Visitor`, and `CF-IPCountry`.
- 2026-07-01: Added Cloudflare edge hardening runbook covering ingress boundary,
  trusted client IP, cache bypass, WAF, edge rate limiting, and public smoke
  verification.
- 2026-07-01: Added CI policy validation for the Cloudflare edge hardening
  baseline.
- 2026-07-01: `npm.cmd run lint` in `apps/web` passed.
- 2026-07-01: `npm.cmd run build` in `apps/web` passed.
- 2026-07-01: `C:\Program Files\Git\bin\bash.exe -n scripts/verify-cloudflare-edge-hardening.sh`
  passed.
- 2026-07-01: `C:\Program Files\Git\bin\bash.exe scripts/verify-cloudflare-edge-hardening.sh`
  passed.
- 2026-07-01: `git diff --check` passed.

## Completion Summary

The code and documentation baseline for Cloudflare edge hardening is complete.
The Web proxy now forwards the reviewed Cloudflare headers required for API
rate-limit identity and request correlation context. A new operations runbook
defines the Cloudflare dashboard policies that still need operator
configuration before the release readiness gates can be marked ready.

## Files Changed

- `.github/workflows/ci.yml`
- `apps/web/src/lib/backend-proxy.ts`
- `docs/implementation-plan/2026-07-01/cloudflare-edge-hardening.md`
- `docs/operations/cloudflare-edge-hardening.md`
- `docs/operations/ec2-rds-deployment-architecture.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/staging-runtime-parameters.md`
- `scripts/verify-cloudflare-edge-hardening.sh`

## Tests Run And Results

- `npm.cmd run lint` in `apps/web`: passed.
- `npm.cmd run build` in `apps/web`: passed.
- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-cloudflare-edge-hardening.sh`:
  passed.
- `C:\Program Files\Git\bin\bash.exe scripts/verify-cloudflare-edge-hardening.sh`:
  passed.
- `git diff --check`: passed.

## Manual Verification Results

Cloudflare dashboard policies were not changed by this task. The release
readiness rows remain `Needs verification` until the operator configures the
runtime `CF-Connecting-IP` parameter, cache bypass, WAF, and edge rate limiting,
then reruns the public smoke workflows.

## Known Limitations

- The repository cannot directly verify Cloudflare dashboard WAF/rate-limit/cache
  rule state without adding Cloudflare API credentials and automation.
- `TIME_ARCHIVE_RATE_LIMIT_CLIENT_IP_HEADER=CF-Connecting-IP` must be applied to
  SSM/runtime parameters before the API uses the forwarded header for anonymous
  rate-limit identity.

## Follow-Up Recommendations

- Set `/time-archive/staging/rate-limit/client-ip-header` to `CF-Connecting-IP`
  and redeploy staging.
- Configure Cloudflare cache bypass, WAF, and edge rate limiting according to
  `docs/operations/cloudflare-edge-hardening.md`.
- Run the listed staging smoke workflows after the edge changes.
