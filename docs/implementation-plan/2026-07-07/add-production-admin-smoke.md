# Add Production Admin Smoke

## Objective

Automate the production admin authorization smoke check now that the production
admin role has been granted and manually verified.

## Scope

- Add a manual GitHub Actions workflow for production admin authorization smoke.
- Add a shell verification script that checks unauthenticated rejection,
  non-admin rejection, and production admin moderation-list access.
- Add a static workflow policy verifier and wire it into CI.
- Update operations documentation and release readiness references.

Out of scope:

- Granting production admin roles.
- Approving, rejecting, hiding, or previewing media.
- Running production R2 upload, playback, or PayPal Live drills.

## Relevant Files Or Modules

- `.github/workflows/smoke-production-admin.yml`
- `.github/workflows/ci.yml`
- `scripts/verify-production-admin-smoke.sh`
- `scripts/verify-production-admin-smoke-workflow.sh`
- `docs/operations/production-admin-provisioning.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/production-first-deploy-runbook.md`

## Key Design Decisions

- The workflow must be manual-only and limited to `main`.
- The workflow must use the GitHub `production` environment and only read
  `PRODUCTION_ADMIN_EMAIL` and `PRODUCTION_ADMIN_PASSWORD` secrets.
- The script must not exercise media mutation endpoints such as approve, reject,
  hide, or preview URL creation.
- The check should create a disposable regular production user only to verify
  admin-route rejection for non-admin users.

## Step-By-Step Execution Plan

1. Create the production admin smoke script from the staging pattern with
   production-specific environment variables and messages.
2. Create the manual production workflow.
3. Create a static policy verifier for the workflow and script.
4. Add the verifier to CI.
5. Update operations and release readiness docs.
6. Run syntax, policy, and whitespace checks.
7. Record completion results in this plan.

## Risks And Rollback Strategy

- Risk: the smoke script mutates production media or payment state.
  - Mitigation: limit the script to auth registration/login and a read-only
    moderation-list API call.
  - Rollback: delete the workflow/script if the policy is wrong before merging.
- Risk: production admin credentials are exposed.
  - Mitigation: use GitHub production environment secrets and verify the script
    does not print password variables.
- Risk: disposable users accumulate.
  - Mitigation: use a clearly prefixed disposable email and keep cleanup under
    the documented data retention policy.

## Verification Plan

- Run shell syntax checks for the new scripts.
- Run the new workflow policy verifier locally.
- Run `git diff --check`.
- After merge, configure production environment secrets if needed and run the
  manual `Smoke production admin` workflow.

## Open Questions

- None for implementation. The production environment must contain
  `PRODUCTION_ADMIN_EMAIL` and `PRODUCTION_ADMIN_PASSWORD` before the workflow
  can run.

## Progress

- Completed: production admin smoke script added.
- Completed: manual production GitHub Actions workflow added.
- Completed: static workflow policy verifier added and wired into CI.
- Completed: production admin provisioning and release readiness docs updated.

## Completion Summary

Production admin authorization can now be verified through a manual GitHub
Actions workflow. The workflow is limited to `main`, uses the `production`
environment, reads only the production public URL variable and production admin
secrets, and checks unauthenticated rejection, non-admin rejection, and
admin-only moderation-list access.

The smoke script does not approve, reject, hide, preview, or mutate media.

## Files Changed

- `.github/workflows/ci.yml`
- `.github/workflows/smoke-production-admin.yml`
- `docs/implementation-plan/2026-07-07/add-production-admin-smoke.md`
- `docs/operations/production-admin-provisioning.md`
- `docs/operations/production-first-deploy-runbook.md`
- `docs/operations/release-readiness-checklist.md`
- `scripts/verify-production-admin-smoke.sh`
- `scripts/verify-production-admin-smoke-workflow.sh`

## Tests Run And Results

- `bash -n scripts/verify-production-admin-smoke.sh scripts/verify-production-admin-smoke-workflow.sh`
  through Git Bash: passed.
- `git diff --check`: passed.
- `./scripts/verify-production-admin-smoke-workflow.sh`: not run locally
  because the local Python environment does not provide `PyYAML`. The existing
  staging admin smoke workflow verifier has the same local limitation. CI runs
  these policy verifiers after installing the CloudFormation validation Python
  dependencies.

## Manual Verification Results

No production smoke workflow was run from this branch. The project owner
manually confirmed that `jmcylove@gmail.com` can access production admin
moderation after the production SSM admin grant.

## Known Limitations

- The production GitHub Environment must contain `PRODUCTION_ADMIN_EMAIL` and
  `PRODUCTION_ADMIN_PASSWORD` before the workflow can pass.
- The smoke creates one disposable regular production user per run to verify
  non-admin rejection.

## Follow-Up Recommendations

- Merge this branch.
- Add the production environment secrets if they are not already present.
- Run `Smoke production admin` against `https://time-archive.com`.
- After it passes, proceed to production R2 upload/admin preview/public playback
  verification.
