# Document Production Runtime Configuration

## Objective

Define the production runtime configuration pack needed before paid production
deployment, covering SSM parameters, production R2 readiness, storage backend
changes, and secret injection boundaries.

## Scope

- Add a production runtime parameter runbook.
- Add a production R2 readiness runbook.
- Add a storage backend change procedure.
- Update release readiness checklist status and links.
- Link the new runbooks from the production deployment foundation.

## Out Of Scope

- Creating AWS, Cloudflare, R2, or SSM resources.
- Writing real secrets or production parameter values.
- Changing deployment scripts.
- Implementing PayPal.
- Running production deployment.

## Relevant Files Or Modules

- `docs/operations/production-runtime-parameters.md`
- `docs/operations/production-r2-readiness.md`
- `docs/operations/storage-backend-change-procedure.md`
- `docs/operations/production-deployment-foundation.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-03/document-production-runtime-configuration.md`

## Key Design Decisions

- Production runtime values live under `/time-archive/production/` and must not
  share staging values.
- Secret values use SSM `SecureString`; non-secret runtime selectors use
  `String`.
- Production R2 must use a dedicated private bucket and least-privilege access
  keys.
- `TIME_ARCHIVE_STORAGE_S3_BUCKET` and
  `TIME_ARCHIVE_STORAGE_S3_PUBLIC_BASE_URL` are data compatibility boundaries.
- Storage backend changes are high-impact operational changes that require
  explicit migration, verification, and rollback plans.

## Step-By-Step Execution Plan

1. Add this implementation plan.
2. Add the production runtime parameter runbook.
3. Add the production R2 readiness runbook.
4. Add the storage backend change procedure.
5. Update release readiness checklist.
6. Update production deployment foundation references.
7. Run documentation diff checks.

## Risks And Rollback Strategy

- Risk: Documentation could imply production values are already provisioned.
  - Mitigation: Keep release readiness rows at `Needs verification` until live
    resources are created and verified.
- Risk: Operators could treat R2 custom domains as public object access.
  - Mitigation: Document that original uploads remain private and public
    playback uses presigned URLs.
- Rollback: Revert documentation changes and restore checklist blocker text.

## Verification Plan

- Run `git diff --check`.
- Review release readiness status wording.

## Open Questions

- None for this documentation step.

## Progress

- Created implementation plan.
- Added production runtime parameter runbook.
- Added production R2 readiness runbook.
- Added storage backend change procedure.
- Updated release readiness checklist.
- Linked production runtime and storage runbooks from the production deployment
  foundation.

## Completion Summary

The production runtime configuration pack is now documented. The new runbooks
define the production SSM parameter contract, R2 production readiness
requirements, and the required procedure for any future storage backend,
bucket, endpoint, or managed object-reference base URL change.

The release readiness checklist now reflects the split state:

- `Production Cloudflare R2`: `Needs verification` until production bucket,
  credentials, CORS, upload, preview, and playback checks pass.
- `Production secret injection`: `Needs verification` until production SSM
  parameters, IAM boundaries, and runtime rendering are verified.
- `Storage backend changes`: `Ready for MVP` because a high-impact change
  procedure is now documented.

## Files Changed

- `docs/operations/production-runtime-parameters.md`
- `docs/operations/production-r2-readiness.md`
- `docs/operations/storage-backend-change-procedure.md`
- `docs/operations/production-deployment-foundation.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-03/document-production-runtime-configuration.md`

## Tests Run And Results

- `git diff --check`: passed.

## Manual Verification Results

- Reviewed release readiness rows to avoid marking production resources as
  ready before live provisioning and verification.
- Confirmed the storage backend change procedure treats bucket, endpoint, and
  object-reference base URL changes as high-impact operational changes.

## Known Limitations

- No AWS, Cloudflare, R2, SSM, or GitHub environment state was changed.
- No production runtime parameters were created.
- No production R2 bucket or credentials were provisioned.
- No production deployment or runtime rendering was executed.

## Follow-Up Recommendations

- Add production runtime parameter metadata validation once production SSM
  values are ready.
- Provision production R2 and verify CORS, private access, upload, admin
  preview, and public playback.
- Proceed next with the observability minimum pack or PayPal integration design
  depending on release sequencing.
