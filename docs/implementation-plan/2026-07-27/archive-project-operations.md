# Archive Project Operations

## Objective

Record that Time Archive infrastructure has been decommissioned and make the
repository documentation safe to read after operations have ended.

## Scope

- Update the repository status in the README.
- Add a repository-safe decommissioning record.
- Mark release and deployment runbooks as historical where their prerequisites
  no longer exist.
- Preserve architecture documents, implementation plans, and historical
  operational evidence.

## Relevant Files

- `README.md`
- `docs/operations/project-decommissioning.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/production-first-deploy-runbook.md`
- `docs/operations/staging-on-demand-runbook.md`

## Key Design Decisions

- Keep existing implementation plans and technical design documents in place.
- Do not record secret values, private customer data, or decrypted parameter
  values.
- Distinguish deleted CloudFormation resources from retained external resources
  such as RDS final snapshots, SSM parameters, Cloudflare configuration, and
  R2 buckets.
- Treat any future infrastructure recreation as a new, explicitly approved
  provisioning effort rather than a continuation of the old runbooks.

## Execution Plan

1. Record the decommissioned status and residual resources in a dedicated
   operations document.
2. Update the README to identify the repository as an archived engineering
   reference.
3. Mark the release checklist and deployment runbooks as superseded by the
   decommissioning record.
4. Verify links, terminology, and Markdown formatting.

## Risks And Rollback Strategy

- Risk: stale deployment instructions could cause an operator to target deleted
  infrastructure. Mitigation: place a clear archival notice at the top of the
  affected runbooks.
- Risk: readers could infer that all project data was deleted. Mitigation:
  identify the retained RDS final snapshots and external resources explicitly.
- Rollback: documentation-only changes can be reverted without changing
  external infrastructure.

## Verification Plan

- Run `git diff --check`.
- Review changed Markdown links and status statements.

## Open Questions

- Whether to delete the retained RDS final snapshots after the archival record
  is merged.
- Whether to separately revoke and delete external Cloudflare, R2, PayPal, and
  SSM resources.

## Progress

- [x] Inspected current README and relevant operations documents.
- [x] Added archival status and decommissioning record.
- [x] Verified documentation changes.

## Completion Summary

The repository now records Time Archive as archived after the staging and
production CloudFormation stacks were deleted. The current infrastructure state,
retained RDS final snapshots, remaining SSM namespaces, external cleanup scope,
and future reactivation boundary are documented without exposing secret values.

## Files Changed

- `README.md`
- `docs/operations/project-decommissioning.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/production-first-deploy-runbook.md`
- `docs/operations/staging-on-demand-runbook.md`
- `docs/implementation-plan/2026-07-27/archive-project-operations.md`

## Tests Run And Results

- `git diff --check`: passed.
- Markdown link and archival-status reference review: passed.

## Manual Verification Results

- Confirmed the staging and production CloudFormation stacks no longer exist.
- Confirmed the retained final RDS snapshot names and allocated storage without
  reading any secret values.
- Confirmed staging and production SSM namespaces remain and contain runtime
  configuration names only in the decommissioning record.

## Known Limitations

- The retained RDS snapshots, SSM parameters, R2 buckets, Cloudflare resources,
  PayPal configuration, and GitHub deployment configuration are not deleted by
  this documentation change.
- Archived deployment workflows and implementation scripts remain in the
  repository for historical reference and require new infrastructure before use.

## Follow-Up Recommendations

- Delete retained RDS snapshots only after explicitly accepting permanent loss
  of database recovery capability.
- Revoke or delete external credentials and remove project-specific external
  resources through separately approved cleanup work.
