# Restructure Monorepo Layout

## Objective

Restructure the repository before adding the frontend so the backend API and
future web frontend have clear application boundaries.

Target layout:

```text
time-archive/
  apps/
    api/
      Spring Boot backend
    web/
      Future Next.js frontend
  docs/
  scripts/
  docker-compose.yml
  README.md
  LICENSE
```

## Scope

- Move the current Spring Boot backend project into `apps/api`.
- Keep repository-level documentation, scripts, Docker Compose, license, and
  GitHub workflow files at the root.
- Update Docker Compose to build the API from `apps/api`.
- Update GitHub Actions backend commands to run from `apps/api`.
- Update repository documentation for local backend commands.
- Verify that backend tests, build, Docker image build, Docker Compose, and
  local verification scripts still work.

## Out of Scope

- Frontend scaffolding.
- Backend package renaming.
- API behavior changes.
- Database schema changes.
- Docker image hardening.
- CI optimization beyond restoring existing checks under the new layout.

## Relevant Files or Modules

- `apps/api`
- `.github/workflows/ci.yml`
- `.gitignore`
- `docker-compose.yml`
- `README.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `docs/manual-verification/*.md`
- `scripts/*.sh`

## Key Design Decisions

- The backend remains a standalone Gradle project under `apps/api`.
- The Gradle wrapper moves with the backend project. Backend commands should be
  run from `apps/api`.
- Docker Compose remains at the repository root because it coordinates multiple
  services and verification scripts.
- `apps/web` will not be created in this task unless the frontend scaffold task
  starts. This keeps the current PR focused on repository structure.
- Historical implementation plans are not rewritten wholesale. Current
  operational documentation is updated to describe the new layout.

## Step-by-Step Execution Plan

- [x] Update `main` and create a dedicated feature branch.
- [x] Create this implementation plan.
- [x] Move backend files into `apps/api`.
- [x] Update Docker Compose API build context.
- [x] Update CI backend working directories and Docker build path.
- [x] Update README and operational docs.
- [x] Run Gradle tests and build from `apps/api`.
- [x] Run Docker build from `apps/api`.
- [x] Run Docker Compose config and local smoke verification from the root.
- [x] Commit and push the branch.

## Risks and Rollback Strategy

- Risk: CI commands may still assume the Gradle project is at the repository
  root.
  - Mitigation: Use explicit `working-directory: apps/api` for backend CI steps.
- Risk: Docker Compose may fail if build context paths are wrong.
  - Mitigation: Run `docker compose config` and at least one local verification
    script.
- Risk: Review noise is high because many files move.
  - Mitigation: Keep behavior unchanged and avoid unrelated edits.
- Rollback: Revert the restructuring commit. Since no schema or behavior change
  is expected, rollback is limited to repository layout and path references.

## Verification Plan

- From `apps/api`:
  - `.\gradlew.bat test --max-workers=2`
  - `.\gradlew.bat build`
- From repository root:
  - `docker build -t time-archive-api:local apps/api`
  - `docker compose config`
  - `docker compose up -d --build`
  - `START_SECOND=... END_SECOND=... ./scripts/verify-local-purchase-flow.sh`
  - `docker compose down`
  - `git diff --check`

## Open Questions

- Should a root-level task runner be added later to simplify commands across
  `apps/api` and `apps/web`?

## Progress Log

- 2026-06-17: Updated `main` from `origin/main`.
- 2026-06-17: Created `codex/restructure-monorepo-layout`.
- 2026-06-17: Created implementation plan.
- 2026-06-17: Moved the Spring Boot backend project into `apps/api`.
- 2026-06-17: Updated Docker Compose, CI, README, `.gitignore`, and
  `.gitattributes` for the new layout.
- 2026-06-17: Verified Gradle test/build, Docker image build, Docker Compose
  config, and local public timeline E2E flow.

## Completion Summary

Restructured the repository into a monorepo-ready layout.

The Spring Boot backend now lives under `apps/api`. Repository-level
documentation, verification scripts, Docker Compose, license, and GitHub
workflow files remain at the root. This keeps the repository ready for a future
`apps/web` frontend without mixing frontend and backend project files.

No backend package names, API behavior, database migrations, or runtime behavior
were intentionally changed.

## Files Changed

- `.github/workflows/ci.yml`
- `.gitattributes`
- `.gitignore`
- `README.md`
- `docker-compose.yml`
- `docs/implementation-plan/2026-06-17/restructure-monorepo-layout.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `apps/api/**`

## Tests Run and Results

- `.\gradlew.bat test --max-workers=2` from `apps/api` - passed
- `.\gradlew.bat build` from `apps/api` - passed
- `docker build -t time-archive-api:local apps/api` from root - passed
- `docker compose config` from root - passed
- `docker compose up -d --build` from root - passed
- `START_SECOND=3300 END_SECOND=3301 ./scripts/verify-local-public-timeline-flow.sh` from root - passed
- `docker compose down` from root - passed
- `git diff --check` from root - passed

## Manual Verification Results

The local public timeline verification completed with:

```text
[verify-timeline] Local public timeline flow verification passed
```

This verifies that the root Docker Compose stack still builds and runs the API
from `apps/api` and that repository-level scripts still work from the root.

## Known Limitations

- No root-level task runner was added. Backend commands should be run from
  `apps/api`, while Docker Compose and verification scripts should be run from
  the repository root.
- Historical implementation plans still mention the pre-restructure root-level
  backend paths because they document past work.

## Follow-up Recommendations

- Scaffold the frontend under `apps/web`.
- Consider a root-level task runner after `apps/web` exists if repeated
  cross-app commands become cumbersome.
