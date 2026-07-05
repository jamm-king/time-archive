# Add Production Runtime Provisioning Pack

## Objective

Add a repository-safe production runtime parameter provisioning pack so an
operator can prepare, validate, dry-run, and write production SSM runtime
parameters without committing or printing secrets.

## Scope

- Add an ignored local-input template for production runtime parameters.
- Add a production parameter writer script modeled after the staging workflow.
- Update the production runtime parameter runbook with the provisioning flow.
- Update `.gitignore` so the real local production input file cannot be
  committed accidentally.
- Keep live AWS writes operator-triggered only.

Out of scope:

- Creating production AWS, R2, Cloudflare, or PayPal resources.
- Writing production parameters during this task.
- Decrypting or printing production secrets.
- Building a production deployment workflow.

## Relevant Files or Modules

- `.gitignore`
- `deploy/production/runtime-parameters.local.example.json`
- `deploy/production/ssm-parameters.example.json`
- `scripts/put-production-runtime-parameters.sh`
- `scripts/verify-production-runtime-parameters.sh`
- `.github/workflows/ci.yml`
- `docs/operations/production-runtime-parameters.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-05/add-production-runtime-provisioning-pack.md`

## Key Design Decisions

- Follow the existing staging runtime parameter workflow to avoid introducing a
  new operational pattern.
- Store real production values only in
  `deploy/production/runtime-parameters.local.json`, which must remain ignored
  by Git.
- Support `--validate-only` and `--dry-run` before any AWS write.
- Require an explicit 12-digit expected AWS account ID and region guard.
- Refuse placeholder, staging-like, empty, multiline, or incorrectly typed
  values before writing.
- Allow `/time-archive/production/rate-limit/client-ip-header` to be omitted
  when its value is empty, matching the staging behavior.
- Keep PayPal Live disabled by default. Allow `paypal/enabled=true` only when
  the operator passes an explicit launch flag.

## Step-by-Step Execution Plan

- [x] Inspect existing staging parameter writer and production validator.
- [x] Create this implementation plan.
- [x] Add ignored production local input template.
- [x] Add production runtime parameter writer script.
- [x] Update production runtime and release readiness documentation.
- [x] Run local validation, shell syntax checks, production deployment
  validation, JSON validation, and diff checks.

## Risks and Rollback Strategy

- Risk: The writer could overwrite production SSM parameters with incorrect
  values.
  Mitigation: require explicit account ID, support validate-only/dry-run, and
  reject placeholders and staging-like values before writes.
- Risk: Secrets could leak through logs.
  Mitigation: log only names and types; never print values; use temporary files
  with restrictive permissions where supported.
- Risk: Production contract drifts from the writer.
  Mitigation: validate against the committed production SSM contract and keep
  the runtime contract validator in CI.
- Rollback: revert this branch. Any live SSM writes, if performed later by an
  operator, must be rolled back by overwriting only the affected parameter.

## Verification Plan

- Run `scripts/put-production-runtime-parameters.sh --validate-only` against a
  non-secret placeholder-free synthetic temp input.
- Run `scripts/verify-production-runtime-parameters.sh`.
- Run `scripts/verify-production-deployment.sh`.
- Run `python -m json.tool` for production JSON templates.
- Run `git diff --check`.

## Open Questions

- The actual production AWS account ID and production-capable AWS profile are
  operator-owned values and are not committed here.
- Live production parameter writes remain pending explicit operator approval.

## Progress

- 2026-07-05: Confirmed staging already has a safe writer workflow and
  production already has a contract validator.
- 2026-07-05: Added the ignored production local input file path and committed
  placeholder template.
- 2026-07-05: Added a production SSM writer script with validate-only, dry-run,
  account guard, region guard, placeholder rejection, staging-reference
  rejection, and non-secret logging.
- 2026-07-05: Updated the production runtime runbook and release readiness
  checklist with the provisioning flow.
- 2026-07-05: Added CI shell syntax validation for the production parameter
  writer without requiring local production secrets or contacting AWS.
- 2026-07-05: Added an explicit `--allow-paypal-enabled` launch flag so the
  writer blocks PayPal Live enablement by default but can still be used for the
  approved launch transition.

## Completion Summary

Production runtime parameter provisioning now mirrors the staging workflow. The
repository contains a committed placeholder template and an ignored local-input
path for real values. Operators can validate the local input without contacting
AWS, run an AWS-account-checked dry run, and then write production SSM
parameters only after explicit approval. The writer logs only names and types
and rejects placeholders, staging references, empty required values, multiline
values, incorrect parameter types, and PayPal Live enablement unless the
operator passes the explicit launch flag.

## Files Changed

- `.gitignore`
- `deploy/production/runtime-parameters.local.example.json`
- `scripts/put-production-runtime-parameters.sh`
- `.github/workflows/ci.yml`
- `docs/operations/production-runtime-parameters.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-05/add-production-runtime-provisioning-pack.md`

## Tests Run and Results

- `scripts/put-production-runtime-parameters.sh --validate-only` passed against
  a temporary synthetic placeholder-free input file.
- `bash -n scripts/put-production-runtime-parameters.sh` passed.
- `scripts/verify-production-runtime-parameters.sh` passed.
- `scripts/verify-production-deployment.sh` passed.
- `python -m json.tool deploy/production/runtime-parameters.local.example.json`
  passed.
- `python -m json.tool deploy/production/ssm-parameters.example.json` passed.
- `git diff --check` passed.

## Manual Verification Results

- Confirmed the production local input file path is ignored by Git.
- Confirmed the runbook documents validate-only, dry-run, live write, metadata
  validation, and the requirement to keep PayPal Live disabled during initial
  runtime provisioning.

## Known Limitations

- Live production SSM writes were not performed.
- Live SSM metadata validation was not run because production parameters are not
  confirmed to be provisioned yet.
- The production release gate remains `Needs verification` until SSM
  provisioning, IAM/KMS access, live metadata validation, and runtime rendering
  are verified.

## Follow-Up Recommendations

- After real production values are prepared locally, run validate-only first,
  then dry-run with the production-capable operator profile.
- After explicit approval, write production parameters and run
  `scripts/verify-production-runtime-parameters.sh --check-aws`.
- Keep `/time-archive/production/paypal/enabled=false` until the PayPal Live
  low-value payment drill and webhook verification pass. Use
  `--allow-paypal-enabled` only for that approved launch transition.
