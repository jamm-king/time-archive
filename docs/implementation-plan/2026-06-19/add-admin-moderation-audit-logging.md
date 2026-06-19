# Add Admin Moderation Audit Logging

## Objective

Record audit logs for admin media moderation actions so approve, reject, and
hide operations are traceable.

## Scope

- Append audit logs from `ApproveMediaAsset`, `RejectMediaAsset`, and
  `HideMediaAsset`.
- Keep media state changes and audit append in the same transaction.
- Add focused application tests for audit log creation.
- Update relevant architecture/security documentation.

Out of scope:

- Exposing audit logs through an API.
- Adding audit logs for every admin endpoint.
- Building an admin audit UI.
- Refactoring audit log persistence.

## Relevant Files Or Modules

- `apps/api/src/main/kotlin/com/timearchive/application/ApproveMediaAsset.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/RejectMediaAsset.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/HideMediaAsset.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/AdminMediaModerationTest.kt`
- `docs/architecture/mvp-scope.md`
- `docs/architecture/security-and-operations.md`

## Key Design Decisions

- Audit append is part of the same transaction as moderation state update.
- `actorUserId` is the admin user ID from the command.
- `actorType` is `USER`.
- `resourceType` is `MEDIA_ASSET`.
- Actions are `MEDIA_ASSET_APPROVED`, `MEDIA_ASSET_REJECTED`, and
  `MEDIA_ASSET_HIDDEN`.
- `beforeState` and `afterState` store compact JSON containing moderation
  status and relevant media URLs.

## Step-By-Step Execution Plan

- [x] Create a dedicated feature branch from latest `main`.
- [x] Add this implementation plan.
- [x] Add audit logging to approve, reject, and hide use cases.
- [x] Wire `AuditLogPort` into moderation use case configuration.
- [x] Add application tests for audit log creation.
- [x] Update documentation for implemented moderation audit logging.
- [x] Run backend tests.
- [x] Run whitespace checks.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: Audit JSON shape may become hard to query.
  - Mitigation: Keep JSON compact and stable; use action/resource columns for
    primary filtering.
- Risk: Audit append failure blocks moderation.
  - Mitigation: This is intentional for MVP because admin moderation actions
    should not silently occur without traceability.

Rollback:

- Revert use case constructor changes, audit append code, tests, documentation,
  and this implementation plan.

## Verification Plan

- `.\gradlew.bat test --max-workers=2`.
- `git diff --check`.

## Open Questions

- None.

## Progress Log

- 2026-06-19: Confirmed local `main` includes admin preview flow verification.
- 2026-06-19: Created `feature/admin-moderation-audit-logging`.
- 2026-06-19: Added implementation plan.
- 2026-06-19: Added audit append to approve, reject, and hide moderation use
  cases.
- 2026-06-19: Wired `AuditLogPort` into moderation use case configuration.
- 2026-06-19: Added application tests for moderation audit log creation.
- 2026-06-19: Updated MVP scope and security documentation.
- 2026-06-19: Ran backend tests successfully.
- 2026-06-19: Ran whitespace checks successfully.

## Completion Summary

Admin media approval, rejection, and hiding now append audit logs in the same
transaction as the moderation state change. Each audit log records the admin
actor, moderation action, media asset resource, compact before/after state, and
creation timestamp.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/application/ApproveMediaAsset.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/RejectMediaAsset.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/HideMediaAsset.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/AdminMediaModerationTest.kt`
- `docs/architecture/mvp-scope.md`
- `docs/architecture/security-and-operations.md`
- `docs/implementation-plan/2026-06-19/add-admin-moderation-audit-logging.md`

## Tests Run And Results

- `.\gradlew.bat test --max-workers=2`
  - Passed.
- `git diff --check`
  - Passed.

## Manual Verification Results

- Not run. This change is covered by application tests and does not add a new
  API or UI behavior.

## Known Limitations

- Audit logs are not yet exposed through an admin read API or UI.
- Moderation audit log `requestId` is currently `null` because moderation
  commands do not carry request IDs.

## Follow-Up Recommendations

- Add request correlation IDs to admin commands once request tracing is
  introduced.
- Add an admin audit log read API when operational review workflows need it.
