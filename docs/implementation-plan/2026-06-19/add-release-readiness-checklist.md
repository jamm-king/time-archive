# Add Release Readiness Checklist

## Objective

Create a release readiness checklist that summarizes the MVP security,
operations, deployment, and verification work required before exposing Time
Archive outside local development.

## Scope

- Add an operations checklist document under `docs/operations`.
- Cover security, payment, storage, database, CI, observability, deployment,
  and known MVP limitations.
- Link the checklist from existing operations documentation and README.

Out of scope:

- Code changes.
- Infrastructure provisioning.
- Cloudflare R2 wiring.
- Payment provider integration.
- README rewrite.

## Relevant Files Or Modules

- `docs/operations/release-readiness-checklist.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `README.md`
- `docs/implementation-plan/2026-06-19/add-release-readiness-checklist.md`

## Key Design Decisions

- Keep the checklist operational and concrete, not aspirational.
- Clearly distinguish MVP-ready items from production blockers.
- Treat fake payment, local admin bootstrap, MinIO defaults, and missing rate
  limits as explicit release risks.
- Avoid duplicating all architecture details; link the checklist as the release
  gate document.

## Step-By-Step Execution Plan

- [x] Create a dedicated docs branch from latest `main`.
- [x] Add this implementation plan.
- [x] Draft the release readiness checklist.
- [x] Link it from README and CI/CD documentation.
- [x] Run documentation whitespace checks.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: The checklist may imply production readiness where gaps remain.
  - Mitigation: Use explicit status labels and release blockers.
- Risk: The document may duplicate existing architecture docs.
  - Mitigation: Keep it as a concise release gate and point to existing docs
    for detailed design.

Rollback:

- Remove the checklist document, links, and this implementation plan.

## Verification Plan

- `git diff --check`
- Manual review of document links and scope.

## Open Questions

- None.

## Progress Log

- 2026-06-19: Confirmed local `main` is up to date.
- 2026-06-19: Created `docs/release-readiness-checklist`.
- 2026-06-19: Added implementation plan.
- 2026-06-19: Added `docs/operations/release-readiness-checklist.md`.
- 2026-06-19: Linked the checklist from README and CI/CD documentation.
- 2026-06-19: Ran documentation whitespace checks successfully.

## Completion Summary

Added a release readiness checklist for the Time Archive MVP. The checklist
summarizes release blockers, MVP-ready areas, security, payment, storage,
database, CI, deployment, observability, known limitations, R2 preparation, and
go/no-go rules.

## Files Changed

- `docs/operations/release-readiness-checklist.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `README.md`
- `docs/implementation-plan/2026-06-19/add-release-readiness-checklist.md`

## Tests Run And Results

- `git diff --check`: passed.

## Manual Verification Results

- Reviewed README and CI/CD documentation links to the release readiness
  checklist.

## Known Limitations

- This task only documents release readiness. It does not resolve production
  blockers such as real payment integration, R2 wiring, backups, rate limiting,
  or observability.

## Follow-Up Recommendations

- Use the checklist to drive the next production-hardening tasks.
- Update the checklist whenever a blocker is resolved or accepted.
