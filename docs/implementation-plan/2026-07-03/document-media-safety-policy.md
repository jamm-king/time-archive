# Document Media Safety Policy

## Objective

Define the MVP media safety policy for uploaded media, especially the malware
scanning path, and align release readiness documentation with the current
implemented controls.

## Scope

- Add an operations runbook for media safety and malware scanning.
- Update release readiness status for the media safety blocker.
- Update existing security and deployment architecture documents that still
  describe file signature validation as missing.

## Out Of Scope

- Production code changes.
- Database schema changes.
- Automatic malware scanner integration.
- New CI or staging smoke workflows.
- R2, Cloudflare, PayPal, backup, or alert provisioning.

## Relevant Files Or Modules

- `docs/operations/media-safety-policy.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/architecture/security-and-operations.md`
- `docs/operations/ec2-rds-deployment-architecture.md`
- `docs/implementation-plan/2026-07-03/document-media-safety-policy.md`

## Key Design Decisions

- Keep original uploads private and unpublished until upload completion,
  signature validation, duration validation where applicable, and admin
  approval all pass.
- Treat admin review as a security boundary for MVP because there is no
  automatic scanner yet.
- Define a limited-launch manual scanning path as the first production path,
  with automatic scanning deferred until higher upload volume or broader public
  self-service access justifies the operational cost.
- Require automatic scanning before open-ended public scale, reduced admin
  review, or any derived-media automation that processes arbitrary uploads.
- Do not introduce GPL or AGPL scanning dependencies without explicit approval.

## Step-By-Step Execution Plan

1. Add this implementation plan.
2. Add the media safety operations runbook.
3. Update the release readiness checklist.
4. Update existing architecture text to match current signature and duration
   validation readiness.
5. Run documentation diff checks.

## Risks And Rollback Strategy

- Risk: Documentation could imply that manual review is equivalent to automatic
  malware scanning.
  - Mitigation: Explicitly separate MVP limited-launch policy from the future
    automatic scanning requirement.
- Risk: Operators could accidentally preview risky files on a workstation.
  - Mitigation: Document preview precautions and require private objects,
    short-lived preview URLs, and no public publication before approval.
- Rollback: Revert the documentation changes and restore malware scanning as a
  production blocker if the manual operating process is rejected.

## Verification Plan

- Run `git diff --check`.
- Review release readiness rows for status consistency.

## Open Questions

- None for this documentation step.

## Progress

- Created implementation plan.
- Added media safety operations runbook.
- Updated release readiness checklist.
- Updated security and deployment architecture documents to reflect current
  signature and duration validation readiness.

## Completion Summary

The MVP media safety policy is now documented. It defines the current upload
validation controls, the limited-launch manual admin review boundary, the
conditions that require automatic scanning, and incident-response expectations.
Release readiness now tracks this as `Needs verification` until the project
owner explicitly accepts the limited-launch residual risk or automatic scanning
is implemented.

## Files Changed

- `docs/operations/media-safety-policy.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/architecture/security-and-operations.md`
- `docs/operations/ec2-rds-deployment-architecture.md`
- `docs/implementation-plan/2026-07-03/document-media-safety-policy.md`

## Tests Run And Results

- `git diff --check`: passed.

## Manual Verification Results

- Reviewed the release readiness checklist to ensure automatic malware scanning
  is not incorrectly marked as implemented.
- Reviewed existing architecture wording and removed stale statements that file
  signature validation is missing.

## Known Limitations

- No automatic scanner is implemented.
- The limited-launch process depends on admin review before publication.
- Project-owner acceptance is still required before the media safety row can be
  marked `Ready`.

## Follow-Up Recommendations

- Decide whether to accept the limited-launch manual review process for MVP or
  implement asynchronous automatic scanning before launch.
- If automatic scanning is selected, implement it through an application-layer
  port and keep scanner-specific dependencies in an adapter.
