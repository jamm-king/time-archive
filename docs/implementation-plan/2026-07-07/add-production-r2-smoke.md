# Add Production R2 Smoke

## Objective

Prepare repeatable production R2 verification for the MVP release gate:
controlled owner range setup, private object upload through presigned PUT,
upload completion, admin preview, approval, and public timeline playback.

## Scope

- Add a production owned-range grant script and policy verifier for preparing a
  controlled smoke-test ownership range.
- Add a manual production media smoke workflow and shell script.
- Add static workflow/script policy verification to CI.
- Update production R2, first-deploy, and release readiness documentation.

Out of scope:

- Executing the production owned-range grant.
- Running production R2 smoke from this branch.
- Running PayPal Live payment drills.
- Adding media cleanup automation.

## Relevant Files Or Modules

- `scripts/grant-production-owned-range.sh`
- `scripts/verify-production-owned-range-grant.sh`
- `scripts/verify-production-media-smoke.sh`
- `scripts/verify-production-media-smoke-workflow.sh`
- `.github/workflows/smoke-production-media.yml`
- `.github/workflows/ci.yml`
- `docs/operations/production-r2-readiness.md`
- `docs/operations/production-first-deploy-runbook.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- The production media smoke workflow will not execute AWS or database grant
  commands. The controlled range must be prepared separately through an audited
  SSM runbook.
- The production grant script creates only `ADMIN_GRANT` ownership records for
  an existing user and does not touch purchase or payment tables.
- The smoke workflow uses the GitHub `production` environment and reads only
  production media owner/admin credentials from environment secrets.
- The smoke performs a real production R2 upload and admin approval, so it is a
  manual workflow and must be run only with a controlled test range and media.
- Hidden/rejected exclusion remains a separate follow-up because this smoke is
  intended to prove the happy path from private upload to approved playback.

## Step-By-Step Execution Plan

1. Add the production owned-range grant script from the staging pattern with
   production stack, SSM, and database parameter paths.
2. Add a production grant policy verifier and CI step.
3. Add the production media smoke script.
4. Add the manual production media smoke workflow.
5. Add a static workflow/script policy verifier and CI step.
6. Update production R2 readiness and release readiness documents.
7. Run shell syntax, static verifier checks where local dependencies allow, and
   whitespace checks.
8. Record completion results in this plan.

## Risks And Rollback Strategy

- Risk: a production smoke grant overlaps paid ownership.
  - Mitigation: grant script rejects any active overlapping ownership and
    requires explicit start/end seconds.
  - Rollback: run a reviewed operation to expire or remove only the controlled
    smoke ownership if it was created incorrectly.
- Risk: smoke publishes test media publicly.
  - Mitigation: require a controlled production range and clearly named test
    media. The smoke should be run before paid launch or only in an accepted
    operations window.
- Risk: credentials or presigned URLs leak.
  - Mitigation: workflow uses production environment secrets, scripts avoid
    printing password and URL variables, and policy verifiers check for obvious
    leaks.
- Risk: production DB mutation is hidden in CI.
  - Mitigation: owned-range grant is not part of the GitHub smoke workflow and
    requires explicit operator execution.

## Verification Plan

- Run shell syntax checks for new scripts.
- Run production owned-range grant policy verifier.
- Run production media smoke workflow policy verifier if local PyYAML is
  available; otherwise rely on CI and document the local limitation.
- Run `git diff --check`.
- After merge, execute the production owned-range grant with explicit approval,
  configure required production environment secrets, and run the manual smoke
  workflow.

## Open Questions

- Exact production smoke range and owner email will be selected before executing
  the grant. Prefer a short unclaimed range that is documented in the operations
  record.

## Progress

- Completed: production owned-range grant script and policy verifier added.
- Completed: production media smoke script and manual workflow added.
- Completed: CI policy checks added for production owned-range grant and media
  smoke workflow.
- Completed: production R2 readiness, first-deploy, and release readiness docs
  updated.

## Completion Summary

Production R2 verification is now prepared as a controlled two-step process:

1. Use an audited SSM script to grant a short production `ADMIN_GRANT` owned
   range to an existing smoke owner account.
2. Run the manual `Smoke production media` workflow to verify production R2
   upload, completion, admin preview, approval, and public timeline playback.

The workflow does not call AWS and does not create ownership records. It only
uses the production HTTPS application surface and production environment
secrets.

## Files Changed

- `.github/workflows/ci.yml`
- `.github/workflows/smoke-production-media.yml`
- `docs/implementation-plan/2026-07-07/add-production-r2-smoke.md`
- `docs/operations/production-first-deploy-runbook.md`
- `docs/operations/production-r2-readiness.md`
- `docs/operations/release-readiness-checklist.md`
- `scripts/grant-production-owned-range.sh`
- `scripts/verify-production-owned-range-grant.sh`
- `scripts/verify-production-media-smoke.sh`
- `scripts/verify-production-media-smoke-workflow.sh`

## Tests Run And Results

- `bash -n scripts/grant-production-owned-range.sh scripts/verify-production-owned-range-grant.sh scripts/verify-production-media-smoke.sh scripts/verify-production-media-smoke-workflow.sh`
  through Git Bash: passed.
- `./scripts/verify-production-owned-range-grant.sh` through Git Bash: passed.
- `git diff --check`: passed.
- `./scripts/verify-production-media-smoke-workflow.sh`: not run locally
  because the local Python environment does not provide `PyYAML`. CI installs
  `PyYAML==6.0.3` through `infra/cloudformation/requirements.txt` before
  running workflow policy verifiers.

## Manual Verification Results

No production owned-range grant or production media smoke workflow was executed
from this branch. Those steps must happen after merge with explicit approval for
the exact owner email and range.

## Known Limitations

- The smoke publishes controlled test media to the production public timeline
  after approval.
- Hidden/rejected timeline exclusion is documented as a separate follow-up and
  is not covered by this happy-path media smoke workflow.
- Each run creates a new uploaded and approved production media asset for the
  configured controlled range.

## Follow-Up Recommendations

- Merge this branch.
- Configure `PRODUCTION_MEDIA_OWNER_EMAIL` and
  `PRODUCTION_MEDIA_OWNER_PASSWORD` in the GitHub `production` Environment.
- Execute `grant-production-owned-range.sh` with `--dry-run`, then with explicit
  approval for the chosen owner email and range.
- Run `Smoke production media` against `https://time-archive.com`.
- After it passes, update the release readiness checklist with the SSM command
  ID and workflow result.

## Follow-Up Fix: Production Media Smoke Environment Export

Date: 2026-07-07

The first production media smoke run reached upload, completion, admin preview,
and approval, then failed during public timeline assertion because the embedded
Python block expected `START_SECOND` and `END_SECOND` in the environment.

Fix:

- Export `START_SECOND` and `END_SECOND` after CLI/env input validation.
- Require those exports in the production media smoke workflow policy verifier.

Verification:

- `bash -n scripts/verify-production-media-smoke.sh scripts/verify-production-media-smoke-workflow.sh`
  through Git Bash: passed.
- `git diff --check`: passed.
- `./scripts/verify-production-media-smoke-workflow.sh`: not run locally
  because the local Python environment does not provide `PyYAML`.
- Re-run `Smoke production media` after this fix is merged.

## Follow-Up Fix: Production Media Smoke Rerun Safety

Date: 2026-07-07

The second production media smoke run reached upload, completion, admin preview,
and approval, then failed before approval because the script expected the whole
timeline range to be empty. The previous failed smoke run had already approved a
test media asset in the same controlled range, so the empty-range assumption was
not rerunnable.

Fix:

- Replace the pre-approval empty-timeline assertion with a targeted assertion
  that the current run's new `mediaAssetId` is absent before approval.
- Keep the post-approval assertion that the current run's `mediaAssetId` appears
  in the public timeline and downloads byte-for-byte through a presigned
  playback URL.
- Require the targeted pre-approval assertion in the workflow policy verifier.

Verification:

- `bash -n scripts/verify-production-media-smoke.sh scripts/verify-production-media-smoke-workflow.sh`
  through Git Bash: passed.
- `git diff --check`: passed.
- Re-run `Smoke production media` after this fix is merged.
