# Standardize Verification Scripts

## Objective

Standardize local and CI verification scripts on shell scripts only.

The repository currently has both shell and PowerShell variants for purchase flow
verification, while newer media upload verification is shell-only. This task
removes the legacy PowerShell script and documents shell scripts as the canonical
verification format for GitHub Actions and Windows Git Bash.

## Scope

- Delete `scripts/verify-local-purchase-flow.ps1`.
- Update README references to remove PowerShell instructions.
- Update manual verification documentation to describe shell-only verification.
- Add a repository-level documentation statement that new verification scripts
  should be maintained as `.sh` scripts.

Out of scope:

- Rewriting existing shell verification logic.
- Adding new verification flows.
- Changing CI behavior beyond documentation consistency.

## Relevant Files Or Modules

- `README.md`
- `docs/manual-verification/local-purchase-flow.md`
- `docs/manual-verification/local-media-upload-flow.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `scripts/verify-local-purchase-flow.ps1`
- `docs/implementation-plan/2026-06-17/standardize-verification-scripts.md`

## Key Design Decisions

- Shell scripts are the canonical verification scripts.
- GitHub Actions runs verification on Ubuntu, where shell scripts are native.
- Windows users should run verification scripts through Git Bash.
- Removing PowerShell avoids duplicate maintenance and behavior drift.

## Step-By-Step Execution Plan

- [x] Create this implementation plan.
- [x] Delete the PowerShell purchase flow script.
- [x] Update README and manual verification docs.
- [x] Update CI/CD testing strategy documentation.
- [x] Run focused verification.
- [x] Update this plan with completion details.
- [ ] Commit and push the branch.

## Risks And Rollback Strategy

- Risk: Windows users without Git Bash lose the PowerShell convenience path.
  - Mitigation: README and manual docs explicitly state that Git Bash is the
    supported Windows execution path.

Rollback:

- Restore `scripts/verify-local-purchase-flow.ps1` and its documentation
  references if PowerShell support is reintroduced.

## Verification Plan

- Run Git Bash syntax checks for shell scripts.
- Run `.\gradlew.bat test --max-workers=2`.
- Run `git diff --check`.

## Open Questions

- None.

## Progress

- 2026-06-17: Plan created.
- 2026-06-17: Removed the legacy PowerShell purchase verification script.
- 2026-06-17: Documented shell-only verification script policy in README,
  manual verification docs, and CI/CD testing strategy.
- 2026-06-17: Verified both shell scripts with Git Bash syntax checks and ran
  backend tests.

## Completion Summary

Standardized verification scripts on shell scripts.

The repository now treats `.sh` scripts as the canonical verification format for
GitHub Actions and Windows Git Bash. The legacy PowerShell purchase verification
script was removed to avoid duplicate logic and drift.

## Files Changed

- `README.md`
- `docs/manual-verification/local-purchase-flow.md`
- `docs/manual-verification/local-media-upload-flow.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `docs/implementation-plan/2026-06-17/standardize-verification-scripts.md`
- `scripts/verify-local-purchase-flow.ps1`

## Tests Run And Results

- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-local-purchase-flow.sh` passed.
- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-local-media-upload-flow.sh` passed.
- `.\gradlew.bat test --max-workers=2` passed.
- `git diff --check` passed.

## Manual Verification Results

- Confirmed the remaining verification scripts are shell scripts.
- Confirmed user-facing documentation no longer instructs users to run the
  removed PowerShell script.

## Known Limitations

- Historical implementation plans still mention the old PowerShell script as
  part of prior completed work. The current repository policy supersedes that
  historical context.

## Follow-Up Recommendations

- Keep new verification scripts shell-only unless there is a concrete operational
  requirement for another runtime.
