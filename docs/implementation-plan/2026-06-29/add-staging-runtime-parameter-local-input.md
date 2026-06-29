# Add Staging Runtime Parameter Local Input

## Objective

Allow the project owner to prepare real staging runtime parameter values in an
ignored local file, without sending secrets through chat or committing them.

## Scope

- Add a committed local input example file for staging runtime parameters.
- Ignore the real local input file.
- Add a script that validates the local input locally and can write parameters
  to AWS SSM Parameter Store without printing values.
- Update the staging runtime parameter runbook.

Out of scope:

- Creating real parameters during this repository change.
- Creating the staging database user.
- Running EC2 deployment.
- Storing or printing any real secret values.

## Relevant Files

- `.gitignore`
- `deploy/staging/runtime-parameters.local.example.json`
- `deploy/staging/runtime-parameters.local.json`
- `scripts/put-staging-runtime-parameters.sh`
- `scripts/verify-staging-runtime-parameters.sh`
- `docs/operations/staging-runtime-parameters.md`

## Key Design Decisions

- The real local input file is ignored by Git and must never be committed.
- The input format mirrors SSM `Parameters` entries so it can be validated
  against the committed runtime contract.
- The put script uses `ssm put-parameter --overwrite` and logs only parameter
  names and types, never values.
- AWS metadata validation remains separate and read-only.
- Local input validation can run without contacting AWS.

## Step-by-step Execution Plan

1. Add the implementation plan and inspect the existing runtime parameter
   contract.
2. Add ignored local input path and committed example file.
3. Add a put script with local validation and non-printing AWS writes.
4. Update the runbook with copy/edit/validate/apply instructions.
5. Run local validation and syntax checks without AWS mutation.

## Risks And Rollback Strategy

- Risk: secret values are accidentally committed. Mitigation: add the real
  local input file to `.gitignore`, provide only placeholder examples, and scan
  staged changes before completion.
- Risk: the put script prints values. Mitigation: script logs only names and
  types and keeps generated temp files private.
- Risk: wrong AWS account receives parameters. Mitigation: require
  `--expected-account-id` before AWS mutation.
- Rollback: revert this repository-only change. If real parameters are later
  written incorrectly, overwrite the affected parameters or delete them with
  explicit approval.

## Verification Plan

- Run `scripts/verify-staging-runtime-parameters.sh`.
- Run `scripts/put-staging-runtime-parameters.sh --dry-run`.
- Run shell syntax validation for new shell scripts.
- Run `git diff --check`.
- Confirm `deploy/staging/runtime-parameters.local.json` is ignored.

## Open Questions

- Real R2 credentials, Cloudflare Tunnel token, and database credentials remain
  owner-supplied local values.

## Progress

- Created the dedicated feature branch from latest `main`.
- Added `deploy/staging/runtime-parameters.local.example.json` as the committed
  input template.
- Added `deploy/staging/runtime-parameters.local.json` to `.gitignore`.
- Added `scripts/put-staging-runtime-parameters.sh` with local validation,
  AWS account check, dry-run, and write modes.
- Updated the staging runtime parameter runbook with copy, edit, validate, and
  apply instructions.
- Allowed the local input reader to accept UTF-8 files with a BOM because
  Windows PowerShell can write that encoding when the owner edits the ignored
  JSON file locally.

## Completion Summary

The project owner can now copy a committed staging runtime parameter template
to an ignored local JSON file, enter real values locally, validate the file
without contacting AWS, and later write the values to SSM Parameter Store
without printing them.

## Files Changed

- `.gitignore`
- `deploy/staging/runtime-parameters.local.example.json`
- `docs/implementation-plan/2026-06-29/add-staging-runtime-parameter-local-input.md`
- `docs/operations/staging-runtime-parameters.md`
- `scripts/put-staging-runtime-parameters.sh`

## Tests Run And Results

- `C:\Program Files\Git\bin\bash.exe -n scripts/put-staging-runtime-parameters.sh`
  - Passed.
- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-staging-runtime-parameters.sh`
  - Passed.
- `scripts/put-staging-runtime-parameters.sh --validate-only` with a temporary
  fake-value local input file
  - Passed.
- `git check-ignore -v deploy/staging/runtime-parameters.local.json`
  - Passed.
- `C:\Program Files\Git\bin\bash.exe -lc "./scripts/verify-staging-runtime-parameters.sh"`
  - Passed.
- `C:\Program Files\Git\bin\bash.exe -lc "./scripts/verify-staging-deployment-runtime.sh"`
  - Passed.
- `git diff --check`
  - Passed.

## Manual Verification Results

The put script was reviewed to ensure it logs only parameter names and types,
not values. The committed local input example contains placeholders only. The
real local input path is ignored by Git.

## Known Limitations

- Real staging parameter values have not been entered or written to AWS.
- The AWS write path has not been executed in this task.
- The staging database application/migration user still needs to be created
  before deployment.

## Follow-up Recommendations

- The project owner should copy the example file to
  `deploy/staging/runtime-parameters.local.json` and fill in real values.
- After local validation, run the put script with AWS profile and expected
  account ID only after explicit approval.
- Run the read-only metadata verifier after parameters are written.
