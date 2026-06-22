# Externalize Local Compose Secrets

## Objective

Remove sensitive local credentials and key material from committed Docker
Compose and application defaults while keeping MinIO and R2 local development
reproducible through explicit, ignored environment files.

## Scope

- Add committed local and R2 environment example files.
- Ignore real environment files while allowing `*.example` files.
- Require database, MinIO, R2, and rate-limit secret values through Compose
  interpolation.
- Remove sensitive defaults from the main Spring configuration.
- Add test-only configuration values under `src/test/resources`.
- Update local startup, R2 setup, and deployment guidance.

Out of scope:

- Production secret manager provisioning.
- Production deployment manifests.
- Credential rotation automation.
- Changing database, MinIO, R2, or Redis providers.

## Relevant Files Or Modules

- `.gitignore`
- `.env.local.example`
- `.env.r2.local.example`
- `docker-compose.yml`
- `docker-compose.r2.yml`
- `apps/api/src/main/resources/application.yml`
- `apps/api/src/test/resources/application-test.yml`
- `README.md`
- `docs/operations/r2-storage-setup.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/architecture/security-and-operations.md`

## Key Design Decisions

- Use explicit `.env.local` and `.env.r2.local` names rather
  than relying on Docker Compose automatic `.env` loading.
- Commit only example files with placeholders. Real values remain ignored.
- Allow multiple `--env-file` arguments so R2 local verification
  composes base local settings with R2-specific settings without duplication.
- Keep non-sensitive local endpoints in Compose, but require passwords,
  credentials, and HMAC salts from environment files.
- Pass variables explicitly in Compose instead of using `env_file:`,
  preventing unrelated local variables from being injected into containers.
- Remove sensitive fallback values from `application.yml` so missing
  deployment configuration fails fast.
- Keep harmless test-only values in `src/test/resources/application-test.yml`
  and activate the profile for Gradle test tasks.
- Production must use deployment secret management. If Compose is used on a
  host, its env file must live outside the repository with restricted file
  permissions.

## Step-By-Step Execution Plan

- [x] Continue on the unmerged `feature/redis-rate-limiting` branch as
  explicitly requested.
- [x] Inspect Compose, application defaults, ignore rules, R2 examples, and
  documentation references.
- [x] Add this implementation plan.
- [x] Add environment example files and ignore exceptions.
- [x] Externalize base and R2 Compose sensitive values.
- [x] Remove application sensitive defaults and add test-only values.
- [x] Update documentation and historical current-state references where
  necessary.
- [x] Run backend tests/build, OpenAPI validation, Compose validation, and diff
  checks.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: Existing local commands without `--env-file` will fail.
  - Mitigation: Update README and operations docs with exact commands and
    fail-fast messages.
- Risk: Existing `.env.r2` users must migrate to the new split files.
  - Mitigation: Document the copy/migration commands and keep variable names
    stable.
- Risk: Removing Spring defaults can break tests or direct JVM startup.
  - Mitigation: Add test-only values and document environment loading for
    direct `bootRun`.
- Risk: Special characters in credentials can break shell-based MinIO init.
  - Mitigation: Pass credentials as container environment variables and quote
    shell expansion inside the init container.

Rollback:

- Restore committed local defaults, remove the example files and test config,
  and revert documentation. No data migration is required.

## Verification Plan

- Confirm real environment files remain ignored and examples are tracked.
- Validate base Compose using `.env.local.example`.
- Validate R2 Compose using both example files.
- Confirm Compose fails fast when required sensitive variables are absent.
- Run full backend tests and build.
- Validate OpenAPI and run `git diff --check`.

## Open Questions

- Production secret manager selection remains part of deployment design.

## Progress Log

- 2026-06-22: Confirmed committed Compose and application configuration still
  contained local database, MinIO, storage, and rate-limit secret defaults.
- 2026-06-22: Selected explicit layered local env files and deployment-managed
  production secrets.
- 2026-06-22: Added committed placeholder templates, externalized Compose and
  Spring secrets, and added a test-only configuration overlay.
- 2026-06-22: Updated local, CI, R2, security, and release-readiness guidance
  for explicit environment files.
- 2026-06-22: Rebuilt and started the full local stack with user-provided base
  and R2 local environment files. All long-running services started and the
  exposed API and web endpoints responded successfully.

## Completion Summary

Sensitive database, MinIO, S3-compatible storage, and rate-limit values no
longer have committed Compose or Spring runtime fallbacks. Local development
now uses explicit ignored `.env.local` and `.env.r2.local` files created from
committed placeholder templates. GitHub Actions uses the base example file for
non-production Compose verification, while Gradle tests use a dedicated test
profile with harmless test-only values.

## Files Changed

- Added `.env.local.example` and `.env.r2.local.example`.
- Updated `.gitignore`, `docker-compose.yml`, and `docker-compose.r2.yml`.
- Updated API runtime configuration, rate-limit properties, Gradle test profile
  activation, and rate-limit filter tests.
- Added `apps/api/src/test/resources/application-test.yml`.
- Updated GitHub Actions Compose environment selection.
- Updated README, architecture, operations, release-readiness, and manual
  verification documentation.
- Removed the superseded `docs/operations/r2.env.example` template.

## Tests Run And Results

- `apps/api/gradlew.bat test --max-workers=2`: passed, 183 tests.
- `apps/api/gradlew.bat build --max-workers=2`: passed.
- `scripts/verify-openapi.sh`: passed.
- `docker compose --env-file .env.local.example config --quiet`: passed.
- `docker compose --env-file .env.local.example --env-file
  .env.r2.local.example -f docker-compose.yml -f docker-compose.r2.yml config
  --quiet`: passed.
- Base Compose without required environment values: failed as expected with a
  missing-variable error.
- `git diff --check`: passed.

## Manual Verification Results

- Confirmed `.env.local` and `.env.r2.local` are ignored by Git.
- Confirmed both committed `*.example` files remain visible to Git.
- Confirmed the working tree contains no real local environment files.
- Built and started the R2-overlay stack with `.env.local` and
  `.env.r2.local` without printing their contents.
- PostgreSQL, Redis, MinIO, and API reported healthy; Web reported running.
- MinIO bucket initialization exited successfully with code 0.
- API health, public timeline, and Web requests returned HTTP 200.
- API and Web startup logs contained no `ERROR`, `Exception`, or `Failed`
  patterns in the inspected tail.

## Known Limitations

- Existing `.env.r2` users must manually split values into `.env.local` and
  `.env.r2.local` before recreating containers.
- Docker Compose environment values remain observable to users with Docker host
  access. Production must use deployment-managed secrets and appropriately
  restricted operational access.
- Production secret manager and deployment manifest integration remain pending.
- Container startup verifies configuration loading but does not prove R2 object
  upload, download, or custom-domain delivery. Those remain covered by the
  dedicated media flow verification.

## Follow-Up Recommendations

- Select the production deployment platform and bind its secret manager to the
  required runtime variables without introducing a repository `.env.prod`.
- Rotate any credential if it was previously committed with a real value or
  shared outside its intended environment.
