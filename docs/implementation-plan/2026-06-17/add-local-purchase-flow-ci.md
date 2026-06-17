# Add Local Purchase Flow CI

## Objective

Run the canonical local purchase flow verification shell script in GitHub Actions.

The script has been stabilized locally. This task connects it to CI so pull requests verify the Docker Compose backend stack and the development-stage purchase flow.

## Scope

- Update `.github/workflows/ci.yml`.
- Add a separate CI job for local purchase flow verification.
- Start the Docker Compose stack in GitHub Actions.
- Run `scripts/verify-local-purchase-flow.sh`.
- Collect Docker Compose logs on failure.
- Update CI/CD testing strategy documentation.

## Out of Scope

- Production deployment.
- Staging deployment.
- Real payment provider integration.
- Frontend E2E tests.
- Database cleanup scripts.

## Relevant Files

- `.github/workflows/ci.yml`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `docs/implementation-plan/2026-06-17/add-local-purchase-flow-ci.md`

## Key Design Decisions

- Keep the existing backend job unchanged.
- Add a separate `local-purchase-flow` job to isolate Docker Compose smoke test failures from Gradle failures.
- Run `docker compose up -d --build` in CI.
- Run the canonical shell script, not the PowerShell script.
- Use a CI-specific range to avoid overlapping with documented local defaults.
- Always print Docker Compose logs on failure.
- Add health retry behavior to the shell script so CI does not race application startup.

## Verification Plan

- Run `.\gradlew.bat test`.
- Run `.\gradlew.bat build`.
- Run local Docker Compose stack and shell script if needed after workflow changes.
- Check workflow YAML syntax by reading the resulting file.

## Risks and Rollback Strategy

- Risk: Docker Compose image build makes CI slower.
  - Mitigation: Keep it in a separate job and only run one concise purchase flow script.
- Risk: CI may fail if the app takes longer to become ready.
  - Mitigation: The verification script starts with actuator health and fails clearly.
- Risk: Docker logs can be noisy.
  - Mitigation: Print logs only on failure.
- Rollback: Remove the `local-purchase-flow` job from CI.

## Progress

- [x] Latest `main` pulled.
- [x] Implementation branch created.
- [x] Implementation plan created.
- [x] GitHub Actions workflow updated.
- [x] CI/CD testing strategy updated.
- [x] Shell script health retry added.
- [x] Verification commands run.
- [x] Completion details recorded.

## Implementation Notes

- Added a `local-purchase-flow` GitHub Actions job.
- The job starts the full Docker Compose stack with `docker compose up -d --build`.
- The job runs `scripts/verify-local-purchase-flow.sh`.
- Docker Compose logs are printed only on failure.
- Docker Compose is stopped in an `always()` cleanup step.
- The shell verification script now waits for `/actuator/health` to become `UP` before continuing.

## Verification Results

- `C:\Program Files\Git\bin\bash.exe -lc "cd /d/develop/time-archive && START_SECOND=120 END_SECOND=130 ./scripts/verify-local-purchase-flow.sh"`: passed.
- `.\gradlew.bat test`: passed.
- `.\gradlew.bat build`: passed.

## Completion Summary

The canonical shell purchase-flow verification script is now connected to GitHub Actions through a dedicated CI job. The script was also hardened with health retry behavior for CI startup timing.

## Files Changed

- `.github/workflows/ci.yml`
- `docs/implementation-plan/2026-06-17/add-local-purchase-flow-ci.md`
- `docs/manual-verification/local-purchase-flow.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `scripts/verify-local-purchase-flow.sh`

## Known Limitations

- The CI job builds the Docker image through Docker Compose, so it can add noticeable runtime.
- This CI job verifies the development-stage fake purchase flow only.
- Real payment provider webhook verification is still future work.

## Follow-Up Recommendations

- Monitor CI runtime after the workflow runs on GitHub.
- Add production-like payment provider webhook tests only after real provider integration exists.
