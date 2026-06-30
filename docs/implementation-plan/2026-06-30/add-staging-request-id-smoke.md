# Add Staging Request ID Smoke

## Objective

Add a staging smoke check that verifies `X-Request-Id` propagation through the
public HTTPS Web proxy to the API and back to the client, including API error
responses.

## Scope

- Preserve `X-Request-Id` in Web API proxy requests to the backend.
- Return backend `X-Request-Id` response headers from Web API proxy responses.
- Add a manual staging request ID smoke workflow.
- Add static CI validation for the smoke workflow and script.
- Update operations documentation and release readiness notes.

Out of scope:

- CloudWatch log searching automation.
- Request completion access logs.
- AWS permissions or live CloudWatch queries.

## Relevant Files Or Modules

- `apps/web/src/lib/backend-proxy.ts`
- `apps/web/src/app/api/timeline/route.ts`
- `scripts/verify-staging-request-id-smoke.sh`
- `scripts/verify-staging-request-id-smoke-workflow.sh`
- `.github/workflows/smoke-staging-request-id.yml`
- `.github/workflows/ci.yml`
- `docs/operations/staging-deployment.md`
- `docs/operations/logging-policy.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- Use only the public staging HTTPS hostname, without AWS credentials.
- Send a known request ID to a successful public endpoint and verify the same
  response header.
- Send a known request ID to an invalid public endpoint request and verify both
  response header and JSON `requestId`.
- Keep the workflow manual only because it verifies deployed staging behavior.

## Step-by-step Execution Plan

1. Add this implementation plan.
2. Update Web proxy request/response header forwarding for `X-Request-Id`.
3. Add the staging request ID smoke script and manual workflow.
4. Add static workflow validation to CI.
5. Update operations documentation and release readiness.
6. Run relevant web tests/build or lint where practical, shell validation, and
   diff checks.

## Risks And Rollback Strategy

- Risk: forwarding arbitrary request IDs into logs. Mitigation: the API already
  validates the header and replaces invalid values.
- Risk: Web proxy routes diverge. Mitigation: update the shared proxy helper and
  the custom timeline route.
- Rollback: revert proxy header propagation, workflow, script, and docs.

## Verification Plan

- Run shell syntax validation for new scripts.
- Run static workflow validation.
- Run Web lint/build if available and practical.
- Run `git diff --check`.
- After merge and deploy, run `Smoke staging request ID`.

## Open Questions

- None.

## Progress

- Created `feature/staging-request-id-smoke` from latest `main`.
- Updated Web proxy request and response `X-Request-Id` propagation.
- Added the staging request ID smoke script and manual workflow.
- Added static workflow validation to CI.
- Updated staging deployment, logging policy, and release readiness
  documentation.

## Completion Summary

- Added Web proxy `X-Request-Id` request forwarding and upstream response header
  propagation.
- Added a manual staging request ID smoke workflow and a local script that
  verifies success and error response propagation.
- Added CI policy validation for the new workflow and script.
- Updated staging deployment, logging policy, and release readiness
  documentation.

## Files Changed

- `.github/workflows/ci.yml`
- `.github/workflows/smoke-staging-request-id.yml`
- `apps/web/src/app/api/timeline/route.ts`
- `apps/web/src/lib/backend-proxy.ts`
- `docs/implementation-plan/2026-06-30/add-staging-request-id-smoke.md`
- `docs/operations/logging-policy.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/staging-deployment.md`
- `scripts/verify-staging-request-id-smoke-workflow.sh`
- `scripts/verify-staging-request-id-smoke.sh`

## Tests Run And Results

- `bash -n scripts/verify-staging-request-id-smoke.sh` passed.
- `bash -n scripts/verify-staging-request-id-smoke-workflow.sh` passed.
- `PYTHONPATH=temp/cfn-lint ./scripts/verify-staging-request-id-smoke-workflow.sh`
  passed.
- `npm.cmd run lint` in `apps/web` passed.
- `npm.cmd run build` in `apps/web` passed.
- `git diff --check` passed.

## Manual Verification Results

- A live check against the currently deployed `https://staging.time-archive.com`
  was attempted before this branch was deployed. The deployed Web proxy did not
  return `X-Request-Id` and the invalid timeline response did not include
  `requestId`, which confirms this branch must be merged and deployed before
  live smoke and CloudWatch search verification can pass.
- Live staging workflow execution should be run after merge and staging
  deployment with the public HTTPS base URL.

## Known Limitations

- This change does not automate CloudWatch log searching.
- The smoke confirms request ID propagation through HTTP responses, not log
  ingestion.

## Follow-up Recommendations

- After merge and deployment, run the `Smoke staging request ID` workflow.
- Use the request ID from that workflow for a live CloudWatch log search
  verification.
