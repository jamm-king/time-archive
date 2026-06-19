# Update Current System Documentation

## Objective

Refresh repository documentation outside `docs/implementation-plan` so it
matches the current implementation, with special attention to the OpenAPI
contract.

## Scope

- Compare tracked documentation commit age against current API and app code.
- Update `docs/api/openapi.yaml` for the current backend API surface.
- Update stale architecture, operations, and manual verification documents when
  they contain outdated implementation details.
- Keep documentation in English.

Out of scope:

- Changing application behavior.
- Rewriting implementation plans.
- Adding new verification scripts.
- Large editorial rewrites unrelated to current-code accuracy.

## Relevant Files Or Modules

- `docs/api/openapi.yaml`
- `docs/architecture/*.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `docs/manual-verification/*.md`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/**`
- `apps/web/src/app/api/**`
- `scripts/*.sh`

## Key Design Decisions

- Treat OpenAPI as the highest-priority public contract.
- Prefer narrow updates that make stale statements accurate instead of broad
  documentation rewrites.
- Exclude `docs/implementation-plan/**` from staleness cleanup except for this
  task plan.

## Step-By-Step Execution Plan

- [x] Create a dedicated documentation branch from `main`.
- [x] Add this implementation plan.
- [x] Audit current API routes and DTOs against OpenAPI.
- [x] Update OpenAPI for missing fields and endpoints.
- [x] Audit architecture, operations, and manual verification docs for stale
  references.
- [x] Apply focused documentation updates.
- [x] Run documentation/API validation checks that are available locally.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: OpenAPI may accidentally document behavior not implemented in code.
  - Mitigation: Derive endpoint and schema changes from controller and DTO
    source files.
- Risk: Broad documentation edits could obscure review.
  - Mitigation: Keep changes focused on stale or missing details.

Rollback:

- Revert this documentation-only branch.

## Verification Plan

- Inspect current controller mappings and DTOs.
- Run `git diff --check`.
- If a local OpenAPI validation command exists, run it; otherwise document that
  no validator is configured.

## Open Questions

- None.

## Progress Log

- 2026-06-19: Confirmed local `main` matched `origin/main`.
- 2026-06-19: Created `docs/update-current-system-docs`.
- 2026-06-19: Listed documentation files outside implementation plans and
  compared their latest commits.
- 2026-06-19: Added implementation plan.
- 2026-06-19: Compared backend controller mappings and DTOs against
  `docs/api/openapi.yaml`.
- 2026-06-19: Updated OpenAPI for current-user `role` and admin original-media
  preview URL generation.
- 2026-06-19: Updated architecture and operations docs for current proxy,
  storage, session, CI, moderation, and audit-log scope details.
- 2026-06-19: Confirmed OpenAPI path list covers current REST controller
  mappings.

## Completion Summary

Refreshed current system documentation outside existing implementation plans.
The OpenAPI contract now includes the current-user `role` field and the
admin-only original media preview URL endpoint. Architecture and operations
docs now reflect the current web proxy shape, implemented port names, Redis
session usage, MinIO local media storage, current CI checks, admin media preview
behavior, and the narrower current audit-log scope.

## Files Changed

- `docs/api/openapi.yaml`
- `docs/architecture/domain-model.md`
- `docs/architecture/mvp-scope.md`
- `docs/architecture/security-and-operations.md`
- `docs/architecture/time-archive-architecture.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `docs/implementation-plan/2026-06-19/update-current-system-docs.md`

## Tests Run And Results

- `git diff --check`
  - Passed.
- Controller mapping and OpenAPI path comparison by inspection
  - Passed.
- OpenAPI YAML parsing with Python
  - Not run because PyYAML is not installed in the local Python environment.

## Manual Verification Results

- Reviewed backend REST controller mappings against documented OpenAPI paths.
- Reviewed relevant DTOs for `CurrentUserResponse` and admin media preview URL
  response.

## Known Limitations

- No OpenAPI validator is configured in the repository.
- Manual verification docs were not broadly rewritten because the current
  scripts still match their documented flows.

## Follow-Up Recommendations

- Add an OpenAPI validation command to CI so syntax and schema drift can be
  checked automatically.
- Add a small API contract check that compares documented paths against Spring
  controller mappings.
