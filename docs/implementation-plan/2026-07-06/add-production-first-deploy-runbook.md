# Add Production First Deploy Runbook

## Objective

Document the first production image publication and deployment execution plan so
the operator can run the manual GitHub Actions workflows with reviewed inputs,
clear rollback boundaries, and post-deploy verification steps.

## Scope

- Add a production first-deploy runbook.
- Document required GitHub repository variables.
- Document production image publication workflow execution.
- Document production deployment workflow execution.
- Document post-deploy checks and failure handling.
- Update release readiness references.

Out of scope:

- Running production image publication.
- Running production deployment.
- Changing AWS, Cloudflare, R2, PayPal, or GitHub repository settings.
- Enabling paid production traffic.

## Relevant Files Or Modules

- `docs/operations/production-first-deploy-runbook.md`
- `docs/operations/production-deployment-foundation.md`
- `docs/operations/release-readiness-checklist.md`
- `.github/workflows/publish-production-images.yml`
- `.github/workflows/deploy-production.yml`

## Key Design Decisions

- The runbook is execution-oriented and contains no secrets.
- The first deploy is split into image publication, deployment, Cloudflare
  routing, smoke verification, and readiness updates.
- Production deployment remains approval-gated through the GitHub `production`
  environment and explicit operator decision.
- PayPal Live paid traffic remains disabled until the dedicated PayPal live
  drill passes.

## Step-By-Step Execution Plan

- [x] Create a dedicated documentation branch.
- [x] Review production deployment foundation, runtime parameter, R2, PayPal,
  and workflow documents.
- [x] Add production first-deploy runbook.
- [x] Update related operations references.
- [x] Run documentation/static checks.
- [x] Record completion summary.

## Risks And Rollback Strategy

- Risk: Operator runs deployment with unpublished image SHA.
  Mitigation: workflow verifies production ECR image tags before deploying.
- Risk: Operator uses mutable Redis or cloudflared references.
  Mitigation: runbook requires digest-pinned image references; workflow rejects
  non-digest values.
- Risk: Production route or PayPal webhook route is missing.
  Mitigation: keep `public_base_url` optional for the first private deploy, then
  verify Cloudflare routing before public smoke and PayPal Live drills.
- Risk: Deployment fails after migration.
  Mitigation: keep first deploy controlled; use CloudWatch/SSM output, keep
  previous release metadata, and prefer forward recovery unless a data restore
  decision is explicitly approved.

## Verification Plan

- Run `git diff --check`.
- Confirm docs contain no credentials or secret values.
- Confirm the runbook references manual workflow execution only.

## Open Questions

- Exact production Cloudflare route timing depends on when the operator is
  ready to expose `https://time-archive.com`.
- PayPal Live enablement remains a later approval-gated step.

## Progress

- 2026-07-06: Created branch `docs/production-first-deploy-runbook`.
- 2026-07-06: Reviewed production deployment foundation, runtime parameters,
  R2 readiness, PayPal Live setup, and production workflow inputs.
- 2026-07-06: Added production first-deploy runbook with preconditions,
  repository variables, workflow inputs, image publication, deployment,
  Cloudflare routing, smoke checks, R2 verification, PayPal Live drill boundary,
  failure handling, and repository-safe deployment record template.
- 2026-07-06: Linked the runbook from production deployment foundation and
  release readiness checklist.

## Completion Summary

Production first-deploy execution is now documented without storing secrets.
The runbook gives the operator an ordered path from production image
publication through deployment, Cloudflare route verification, public smoke,
R2 checks, and later PayPal Live drill. It also records failure handling and
repository-safe deployment notes.

## Files Changed

- `docs/operations/production-first-deploy-runbook.md`
- `docs/operations/production-deployment-foundation.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-06/add-production-first-deploy-runbook.md`

## Tests Run And Results

- `git diff --check` passed.
- Searched the new runbook for obvious secret placeholders and confirmed it
  contains only repository-safe identifiers, ARNs, instance IDs, image
  references, and runbook instructions.

## Manual Verification Results

- Confirmed the runbook references manual workflow execution only.
- Confirmed PayPal Live remains disabled until the dedicated live drill.
- Confirmed public routing and R2 verification are ordered after private
  deployment success.

## Known Limitations

- The runbook has not been executed yet.
- Production image publication, deployment, Cloudflare routing, R2 verification,
  and PayPal Live drill remain explicit operator actions.

## Follow-Up Recommendations

- Commit and merge this documentation branch.
- Confirm GitHub production repository variables are set.
- Run `Publish production images` from `main`.
- Run `Deploy production` after image publication succeeds.
