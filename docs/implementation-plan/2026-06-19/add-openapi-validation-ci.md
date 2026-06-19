# Add OpenAPI Validation CI

## Objective

Add automated validation for `docs/api/openapi.yaml` so pull requests fail when
the OpenAPI document is syntactically invalid or violates the OpenAPI schema.

## Scope

- Add a repository script for OpenAPI validation.
- Add a GitHub Actions CI job that runs the validation script.
- Document the validation command in project documentation if needed.

Out of scope:

- Runtime contract tests between controllers and OpenAPI.
- Generated clients or server stubs.
- API breaking-change detection.
- Adding new npm or Gradle dependencies to the repository.

## Relevant Files Or Modules

- `docs/api/openapi.yaml`
- `scripts/verify-openapi.sh`
- `.github/workflows/ci.yml`
- `docs/implementation-plan/2026-06-19/add-openapi-validation-ci.md`

## Key Design Decisions

- Use the Redocly CLI Docker image in the validation script to avoid adding a
  repository package manager dependency only for OpenAPI validation.
- Keep the script shell-only so it can run on GitHub Actions Ubuntu runners and
  locally through Git Bash on Windows.
- Validate the whole OpenAPI file rather than only checking YAML syntax.

## Step-By-Step Execution Plan

- [x] Create a dedicated feature branch from latest `main`.
- [x] Add this implementation plan.
- [x] Add `scripts/verify-openapi.sh`.
- [x] Add an `openapi` CI job.
- [x] Run the validation script.
- [x] Run shell syntax and whitespace checks.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: Docker image availability can affect CI reliability.
  - Mitigation: Use a stable public CLI image and keep the script isolated so
    the validation command can be replaced later without affecting application
    code.
- Risk: The OpenAPI document may currently have schema issues.
  - Mitigation: Fix only OpenAPI validity issues required by the validator.

Rollback:

- Remove the validation script, CI job, and this implementation plan.

## Verification Plan

- `C:\Program Files\Git\bin\bash.exe -n ./scripts/verify-openapi.sh`
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-openapi.sh`
- `git diff --check`

## Open Questions

- None.

## Progress Log

- 2026-06-19: Confirmed local `main` is up to date.
- 2026-06-19: Created `feature/openapi-validation-ci`.
- 2026-06-19: Added implementation plan.
- 2026-06-19: Added Docker-based Redocly OpenAPI validation script.
- 2026-06-19: Added an OpenAPI validation job to GitHub Actions CI.
- 2026-06-19: Updated OpenAPI nullable schemas to OpenAPI 3.1-compatible
  union types.
- 2026-06-19: Added Redocly configuration to keep validation focused on
  schema validity instead of unrelated documentation policy rules.
- 2026-06-19: Updated README and CI/CD documentation.
- 2026-06-19: Fixed the OpenAPI verification script file mode so GitHub
  Actions can execute it directly.

## Completion Summary

Added automated OpenAPI validation through `scripts/verify-openapi.sh` and a
dedicated GitHub Actions `OpenAPI` job. The validator runs Redocly CLI through
Docker, so the repository does not need new npm or Gradle dependencies. Existing
OpenAPI nullable fields were updated to OpenAPI 3.1-compatible union types.

## Files Changed

- `.github/workflows/ci.yml`
- `redocly.yaml`
- `scripts/verify-openapi.sh`
- `docs/api/openapi.yaml`
- `README.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `docs/implementation-plan/2026-06-19/add-openapi-validation-ci.md`

## Tests Run And Results

- `C:\Program Files\Git\bin\bash.exe -n ./scripts/verify-openapi.sh`: passed.
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-openapi.sh`: passed.
- `git diff --check`: passed.
- `git ls-files --stage scripts/verify-openapi.sh`: confirmed executable
  file mode after the follow-up fix.

## Manual Verification Results

- Confirmed Redocly validates `docs/api/openapi.yaml` successfully through the
  new script.

## Known Limitations

- This validates OpenAPI document structure and lint rules, not runtime
  controller-to-contract conformance.
- The script depends on Docker and pulls the Redocly CLI image when it is not
  already present locally.

## Follow-Up Recommendations

- Consider adding contract tests later if controller behavior and OpenAPI drift
  becomes a recurring risk.
