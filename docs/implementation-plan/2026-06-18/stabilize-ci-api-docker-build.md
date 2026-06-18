# Stabilize CI API Docker Build

## Objective

Stabilize GitHub Actions local flow jobs by avoiding Gradle dependency
resolution inside Docker image builds.

## Scope

- Add a CI runtime-only API Dockerfile that copies a prebuilt Spring Boot jar.
- Add a Docker Compose CI override that uses the runtime-only API Dockerfile.
- Update GitHub Actions local flow jobs to build the API jar on the runner
  before starting Docker Compose.
- Update the backend Docker image check to use the prebuilt jar image path.

## Out of Scope

- Changing the local developer Docker Compose workflow.
- Changing application behavior.
- Upgrading Gradle, Kotlin, or Spring Boot.
- Reworking dependency versions.

## Relevant Files or Modules

- `.github/workflows/ci.yml`
- `apps/api/.dockerignore`
- `apps/api/Dockerfile.ci`
- `docker-compose.ci.yml`
- `docs/implementation-plan/2026-06-18/stabilize-ci-api-docker-build.md`

## Key Design Decisions

- Keep `apps/api/Dockerfile` self-contained for local Docker builds.
- Use `apps/api/Dockerfile.ci` in GitHub Actions so dependency resolution
  happens through the runner's Gradle setup and cache.
- Do not treat Maven Central or Plugin Portal `403` responses as application
  failures. The CI pipeline should reduce exposure to external dependency
  resolution during Docker Compose smoke jobs.

## Step-by-Step Execution Plan

- [x] Create this implementation plan.
- [x] Add CI runtime-only API Dockerfile.
- [x] Add Docker Compose CI override.
- [x] Update CI backend Docker build step.
- [x] Update local flow jobs to run Gradle bootJar before Compose startup.
- [x] Run relevant local validation.
- [x] Update this plan with completion details.
- [ ] Commit and push the branch.

## Risks and Rollback Strategy

- Risk: CI and local Docker builds may diverge.
  - Mitigation: The CI Dockerfile only changes where the jar is built. Runtime
    image behavior remains the same.
- Risk: A stale jar could be copied in CI.
  - Mitigation: CI runs `./gradlew bootJar --no-daemon` immediately before
    Compose startup.
- Rollback: Revert the CI Dockerfile, Compose override, workflow changes, and
  this plan. No application data or schema changes are involved.

## Verification Plan

- `.\gradlew.bat bootJar` under `apps/api`
- `docker compose -f docker-compose.yml -f docker-compose.ci.yml config`
- `docker build -f apps/api/Dockerfile.ci -t time-archive-api:ci apps/api`
- `git diff --check`

## Open Questions

- None.

## Progress Log

- 2026-06-18: Investigated GitHub Actions failure showing Gradle dependency
  resolution inside Docker build failed with Maven `403` responses.
- 2026-06-18: Decided to move CI Gradle dependency resolution out of Docker
  build and into runner-level Gradle steps.
- 2026-06-18: Added `apps/api/Dockerfile.ci` as a runtime-only image that
  copies `build/libs/*.jar`.
- 2026-06-18: Added `docker-compose.ci.yml` so GitHub Actions can override the
  API build Dockerfile without changing the local developer Compose path.
- 2026-06-18: Updated local flow jobs and the backend Docker build check to
  build the API jar on the runner before Docker image creation.
- 2026-06-18: Updated `.dockerignore` so CI Docker builds can copy the prebuilt
  jar while keeping other build outputs excluded.
- 2026-06-18: Verified the CI API Dockerfile build and the local public
  timeline flow with the Compose CI override.

## Completion Summary

Stabilized CI API Docker builds by moving Gradle dependency resolution out of
Docker image builds.

GitHub Actions now builds the API jar on the runner using `setup-java` and
`gradle/actions/setup-gradle`, then starts Docker Compose with
`docker-compose.ci.yml`. The CI API image uses `apps/api/Dockerfile.ci`, which
copies the prebuilt jar and does not run Gradle inside Docker.

This avoids repeated Gradle plugin and Maven dependency resolution from inside
Compose smoke job Docker builds, which caused the reported `Could not resolve`
and Maven `403 Forbidden` failures.

## Files Changed

- `.github/workflows/ci.yml`
- `apps/api/.dockerignore`
- `apps/api/Dockerfile.ci`
- `docker-compose.ci.yml`
- `docs/implementation-plan/2026-06-18/stabilize-ci-api-docker-build.md`

## Tests Run and Results

- `.\gradlew.bat bootJar` under `apps/api` - passed
- `docker compose -f docker-compose.yml -f docker-compose.ci.yml config` -
  passed
- `docker build -f apps/api/Dockerfile.ci -t time-archive-api:ci apps/api` -
  passed
- `docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d --build api` -
  passed
- `START_SECOND=3000 END_SECOND=3001 bash ./scripts/verify-local-public-timeline-flow.sh` -
  passed
- `docker compose down` - passed
- `git diff --check` - passed

## Manual Verification Results

Verified that the failed local public timeline flow passes when the API service
is built through the CI Compose override.

## Known Limitations

- CI still needs network access for the runner-level Gradle build, but that path
  uses the GitHub Actions Gradle cache and avoids repeated dependency
  resolution inside Docker builds.
- The local developer Dockerfile remains self-contained and still runs Gradle
  inside Docker for local image builds.

## Follow-up Recommendations

- If CI dependency resolution remains flaky at the runner level, consider
  adding dependency verification or a repository proxy/cache. Do not solve that
  by moving dependency resolution back into Docker Compose smoke jobs.
