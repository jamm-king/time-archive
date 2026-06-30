# Optimize Build Cache

## Objective

Reduce GitHub Actions build time for API-related changes, especially the ARM64
API Docker build in the production deployment policy job.

## Scope

- Improve the API production Dockerfile so Gradle dependencies and build cache
  can be reused by BuildKit/GitHub Actions cache.
- Remove avoidable duplicate Gradle work in the backend CI job.
- Keep deployment semantics, image contents, and verification coverage
  unchanged.

Out of scope:

- Reworking all local flow jobs to share build artifacts.
- Changing staging or production deployment behavior.
- Adding new build dependencies.
- Enabling remote Gradle build cache.

## Relevant Files Or Modules

- `apps/api/Dockerfile`
- `.github/workflows/ci.yml`

## Key Design Decisions

- Use Docker BuildKit cache mounts for `/root/.gradle` during dependency
  resolution and `bootJar`.
- Add a dependency-resolution layer before copying `src` so source-only changes
  can reuse dependency layers.
- Keep `--no-daemon` for deterministic CI/container builds.
- Replace `test` plus `build` in the backend CI job with a single `build`
  command because Gradle `build` already depends on `test`.

## Step-by-step Execution Plan

1. Create this implementation plan.
2. Update `apps/api/Dockerfile` with BuildKit cache mounts and dependency
   prefetching.
3. Simplify backend CI Gradle invocation.
4. Run API build and Docker build checks locally where practical.
5. Update this plan with verification results and any limitations.

## Risks And Rollback Strategy

- Risk: Dockerfile dependency prefetch command may execute more tasks than
  intended. Mitigation: use Gradle `dependencies`, which resolves dependency
  metadata without compiling application sources.
- Risk: BuildKit cache mount behavior differs between local Docker and GitHub
  Actions. Mitigation: keep normal Docker layer cache behavior intact and rely
  on existing Buildx `cache-from/cache-to` in Actions.
- Rollback: revert the Dockerfile and CI workflow changes.

## Verification Plan

- Run `apps/api/gradlew.bat build --max-workers=2`.
- Run `docker build -f apps/api/Dockerfile -t time-archive-api:cache-check apps/api`
  if Docker is available.
- Run `git diff --check`.

## Open Questions

- None.

## Progress

- Created `feature/build-cache-optimization` from latest `main`.
- Updated the API Dockerfile to prefetch Gradle dependencies before copying
  source files.
- Added BuildKit cache mounts for `/root/.gradle` during dependency prefetch
  and `bootJar`.
- Suppressed dependency tree output during Docker prefetch to keep GitHub
  Actions logs smaller.
- Simplified the backend CI job from separate `test` and `build` steps to a
  single `build` step because Gradle `build` already runs tests.

## Completion Summary

The API production Docker build now has a dependency prefetch layer that can be
reused when only application source changes. Gradle's user home is mounted as a
BuildKit cache during dependency resolution and `bootJar`, which lets existing
GitHub Actions Buildx cache settings preserve Gradle downloads across ARM64
Docker builds. The backend CI job no longer runs `test` and then `build`
separately.

## Files Changed

- `.github/workflows/ci.yml`
- `apps/api/Dockerfile`
- `docs/implementation-plan/2026-06-30/optimize-build-cache.md`

## Tests Run And Results

- `apps/api/gradlew.bat build --max-workers=2` passed.
- `docker build -f apps/api/Dockerfile -t time-archive-api:cache-check apps/api`
  passed.
- `PYTHONPATH=temp/cfn-lint ./scripts/verify-staging-image-publish-workflow.sh`
  passed.
- `./scripts/verify-production-deployment.sh` passed.
- `git diff --check` passed.

## Manual Verification Results

- A repeated local Docker build reused earlier layers and kept the dependency
  prefetch log concise after redirecting dependency tree output.

## Known Limitations

- The first cold Docker build still downloads Gradle and dependencies.
- `bootJar` still compiles source when application code changes; this change
  optimizes dependency and layer reuse, not Kotlin compilation itself.
- Local flow jobs still build the API jar independently. Artifact sharing can be
  considered separately if those jobs remain too slow.

## Follow-up Recommendations

- Compare the next production deployment job runtime against previous runs.
- If local flow jobs remain slow, add a single API jar artifact producer job and
  have flow jobs download that artifact instead of running `bootJar` repeatedly.
