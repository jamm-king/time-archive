# Add Staging Public Smoke Check

## Objective

Add a repeatable public smoke check for the deployed staging HTTPS hostname so
the team can verify Cloudflare Tunnel routing and basic application responses
without rerunning a deployment.

## Scope

- Add a shell script that checks a staging public base URL.
- Add a manually triggered GitHub Actions workflow for staging public smoke.
- Add static validation for the workflow contract.
- Update operations documentation with the new verification path.

Out of scope:

- Running the public smoke workflow from this branch.
- Adding authenticated or payment/media end-to-end staging flows.
- Changing Cloudflare, AWS, or application runtime configuration.

## Relevant Files Or Modules

- `scripts/verify-staging-public-smoke.sh`
- `scripts/verify-staging-public-smoke-workflow.sh`
- `.github/workflows/smoke-staging-public.yml`
- `.github/workflows/ci.yml`
- `docs/operations/staging-deployment.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- The smoke check is manual only because it targets a live external staging
  hostname.
- The workflow uses the `staging` GitHub Environment for consistency with other
  staging operations.
- The public base URL comes from a workflow input or the repository variable
  `STAGING_PUBLIC_BASE_URL`.
- The script checks the web root and public timeline API through the browser
  hostname. It does not authenticate or mutate data.

## Step-by-step Execution Plan

1. Inspect existing deployment verification scripts and workflows.
2. Add the public smoke script.
3. Add the manual staging public smoke workflow.
4. Add CI static validation for the workflow and script.
5. Update operations documentation.
6. Run local validation.

## Risks And Rollback Strategy

- Risk: external staging downtime fails a workflow unrelated to code changes.
  Mitigation: keep the public smoke workflow manual and validate only its
  policy in CI.
- Risk: accidentally checking mutating or authenticated endpoints. Mitigation:
  restrict the script to `GET /` and `GET /api/timeline?from=0&to=1`.
- Rollback: remove the workflow and scripts without affecting deployment.

## Verification Plan

- Run `bash -n` for the new shell scripts.
- Run `scripts/verify-staging-public-smoke-workflow.sh`.
- Run `git diff --check`.
- Optionally run `scripts/verify-staging-public-smoke.sh` against the staging
  public URL after this change is reviewed.

## Open Questions

- None.

## Progress

- Created the dedicated branch from latest `main`.
- Added the public staging smoke shell script.
- Added the manual GitHub Actions public smoke workflow.
- Added CI static validation for the workflow contract.
- Updated operations documentation with the public smoke verification path.
- Fixed Python selection for Windows Git Bash by requiring an actual JSON-capable
  Python command before running response-shape validation.

## Completion Summary

Added a repeatable staging public smoke verification path. The shell script
checks the staging HTTPS web root and public timeline endpoint without
authentication or mutations. A new manual GitHub Actions workflow can run the
same check from `main` using either a workflow input or the
`STAGING_PUBLIC_BASE_URL` repository variable.

CI now validates the smoke workflow policy without contacting the public staging
environment.

## Files Changed

- `.github/workflows/smoke-staging-public.yml`
- `.github/workflows/ci.yml`
- `scripts/verify-staging-public-smoke.sh`
- `scripts/verify-staging-public-smoke-workflow.sh`
- `docs/operations/staging-deployment.md`
- `docs/operations/release-readiness-checklist.md`

## Tests Run And Results

- `bash -n scripts/verify-staging-public-smoke.sh`: passed.
- `bash -n scripts/verify-staging-public-smoke-workflow.sh`: passed.
- `scripts/verify-staging-public-smoke-workflow.sh`: passed with local
  `PYTHONPATH=temp/cfn-lint`.
- `scripts/verify-staging-public-smoke.sh --base-url https://staging.time-archive.com`: passed.
- `git diff --check`: passed.

## Manual Verification Results

The public staging hostname returned a non-empty web root response and the
public timeline endpoint returned valid JSON through the Cloudflare-routed HTTPS
hostname.

## Known Limitations

- The workflow is manual only; it is not a required PR check because it depends
  on a live staging environment.
- The smoke check covers only public read-only endpoints. Authenticated user
  flows remain a separate staging verification task.

## Follow-up Recommendations

- Configure the `STAGING_PUBLIC_BASE_URL` repository variable with the staging
  HTTPS hostname.
- Run the manual workflow after each staging deployment, or pass
  `public_base_url` to `Deploy staging` when deployment-time public smoke is
  desired.
