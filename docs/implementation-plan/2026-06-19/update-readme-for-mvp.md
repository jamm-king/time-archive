# Update README For MVP

## Objective

Update the root README so a new contributor can understand the current MVP
scope, run the local stack, execute verification scripts, create admin users,
and understand current production blockers.

## Scope

- Update project status and MVP feature summary.
- Clarify Docker Compose and local development workflows.
- Consolidate verification commands.
- Document admin bootstrap and development-only fake payment behavior.
- Document object storage policy for MinIO and future Cloudflare R2.
- Link release readiness and production blockers.
- Call out owner-operated tasks that require external accounts or decisions.

Out of scope:

- Code changes.
- Infrastructure provisioning.
- Resolving release blockers.
- Full rewrite of architecture documents.

## Relevant Files Or Modules

- `README.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-06-19/update-readme-for-mvp.md`

## Key Design Decisions

- Keep README practical and current rather than exhaustive.
- Point to detailed architecture and operations docs instead of duplicating
  every decision.
- Be explicit that the project is a local MVP, not production-ready.
- List tasks that need project-owner action separately.

## Step-By-Step Execution Plan

- [x] Create a dedicated docs branch from latest `main`.
- [x] Add this implementation plan.
- [x] Update README status, quick start, verification, storage, admin, and
  production blocker sections.
- [x] Run documentation whitespace checks.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: README may imply production readiness.
  - Mitigation: Keep production blockers prominent.
- Risk: README may become too long.
  - Mitigation: Keep details concise and link to deeper docs.

Rollback:

- Revert the README and this implementation plan.

## Verification Plan

- `git diff --check`
- Manual review of README links and commands.

## Open Questions

- None.

## Progress Log

- 2026-06-19: Confirmed local `main` is up to date.
- 2026-06-19: Created `docs/update-readme-for-mvp`.
- 2026-06-19: Added implementation plan.
- 2026-06-19: Reworked README around current MVP status, Docker Compose quick
  start, verification scripts, admin bootstrap, storage policy, production
  blockers, and project-owner tasks.
- 2026-06-19: Ran documentation whitespace checks successfully.

## Completion Summary

Updated the root README to reflect the current local MVP instead of early
backend-only development. The README now highlights implemented MVP areas,
production blockers, Docker Compose quick start, local development commands,
verification scripts, admin bootstrap, object storage policy, database
configuration, and project-owner tasks.

## Files Changed

- `README.md`
- `docs/implementation-plan/2026-06-19/update-readme-for-mvp.md`

## Tests Run And Results

- `git diff --check`: passed.

## Manual Verification Results

- Reviewed README references to existing architecture, operations, OpenAPI,
  manual verification, and release readiness documents.

## Known Limitations

- This task updates documentation only. It does not resolve production blockers
  such as real payments, R2 provisioning, rate limiting, backups, or
  observability.

## Follow-Up Recommendations

- Use the project-owner task list to drive external-account and production
  readiness decisions.
- Revisit README after real payment and production storage work starts.
