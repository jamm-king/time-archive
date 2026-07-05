# Add Production Runtime Parameter Validation

## Objective

Add repository-safe validation for the production runtime parameter contract so
operators can verify production SSM parameter names and types before deploying
or enabling real payments.

## Scope

- Add a production runtime parameter validation script.
- Validate `deploy/production/ssm-parameters.example.json` against the renderer
  contract and production runtime documentation.
- Support optional live AWS SSM metadata validation without decrypting or
  printing parameter values.
- Update production runtime and release readiness documentation to reference the
  validator.

Out of scope:

- Writing production SSM parameters.
- Reading decrypted production secrets.
- Creating production AWS, Cloudflare, R2, or PayPal resources.
- Deploying production.

## Relevant Files or Modules

- `scripts/verify-production-runtime-parameters.sh`
- `deploy/production/ssm-parameters.example.json`
- `deploy/production/render-runtime-env.sh`
- `docs/operations/production-runtime-parameters.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-05/add-production-runtime-parameter-validation.md`

## Key Design Decisions

- Keep production validation separate from staging validation to avoid mixing
  environment-specific parameter names and release gates.
- Live validation checks only SSM metadata names and types. It does not request
  decryption and does not print values.
- The script validates PayPal Live placeholders and keeps
  `/time-archive/production/paypal/enabled` expected as a `String`, with value
  `false` in the committed fixture.
- The script should fail if the production fixture includes staging paths,
  missing required parameters, unexpected parameters, wrong types, multiline
  values, or non-placeholder secret-looking committed values.

## Step-by-Step Execution Plan

- [x] Inspect existing staging runtime parameter validator and production
  renderer.
- [x] Create this implementation plan.
- [x] Add production runtime parameter validation script.
- [x] Update production runtime and release readiness docs.
- [x] Run script, production deployment validator, JSON validation, and diff
  checks.

## Risks and Rollback Strategy

- Risk: Validator rejects a valid future production parameter.
  Mitigation: keep expected names aligned with the renderer and update the
  validator in the same PR as contract changes.
- Risk: Live validation could expose secrets.
  Mitigation: use `describe-parameters` metadata only, never
  `get-parameters --with-decryption`.
- Rollback: revert this branch. Production runtime parameter docs and renderer
  remain unchanged.

## Verification Plan

- Run `scripts/verify-production-runtime-parameters.sh`.
- Run `scripts/verify-production-runtime-parameters.sh --check-aws` only if
  approved and production metadata exists.
- Run `scripts/verify-production-deployment.sh`.
- Run `python -m json.tool deploy/production/ssm-parameters.example.json`.
- Run `git diff --check`.

## Open Questions

- Live production SSM metadata may not exist yet. The local fixture validator is
  still useful before provisioning.

## Progress

- 2026-07-05: Confirmed production runtime renderer already reads the required
  production PayPal, R2, database, Cloudflare, and rate-limit parameters.
- 2026-07-05: Added local production runtime parameter contract validation and
  optional AWS SSM metadata validation without value decryption.
- 2026-07-05: Added the production runtime parameter contract check to CI and
  linked the validator from the production runtime documentation.

## Completion Summary

Production runtime parameter validation now has a dedicated script that checks
the committed production SSM fixture against the expected parameter names,
types, safe placeholder values, renderer requirements, and runtime
documentation. The script also supports optional live AWS SSM metadata
validation with `describe-parameters` only, so it does not decrypt or print
production values.

## Files Changed

- `.github/workflows/ci.yml`
- `scripts/verify-production-runtime-parameters.sh`
- `docs/operations/production-runtime-parameters.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-05/add-production-runtime-parameter-validation.md`

## Tests Run and Results

- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-production-runtime-parameters.sh`
  passed.
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-production-deployment.sh`
  passed.
- `python -m json.tool deploy\production\ssm-parameters.example.json`
  passed.
- `git diff --check` passed.

## Manual Verification Results

- Confirmed CI includes the new production runtime parameter contract
  validation step in the production deployment workflow path.
- Confirmed the production runtime parameter documentation explains both local
  fixture validation and optional live SSM metadata validation.

## Known Limitations

- Live production SSM metadata validation was not run because production
  parameters are not confirmed to be provisioned yet.
- The release readiness item remains `Needs verification` until production
  parameters, IAM access, KMS policy, live SSM metadata, and runtime rendering
  are verified.

## Follow-Up Recommendations

- After production SSM parameters are provisioned, run the validator with
  `--check-aws` using the approved production-capable operator profile.
- Keep this validator aligned with any future changes to
  `deploy/production/render-runtime-env.sh` or the production SSM parameter
  contract.
