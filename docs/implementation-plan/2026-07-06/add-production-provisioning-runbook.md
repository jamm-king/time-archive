# Add Production Provisioning Runbook

## Objective

Add production provisioning runbooks and verification scripts needed before
creating the production CloudFormation change set, executing the production
stack, bootstrapping the production database user, and completing production
runtime parameter validation.

## Scope

- Add production CloudFormation parameter input validation.
- Add a production provisioning preflight self-test with fake AWS CLI coverage.
- Add a production database user bootstrap script.
- Add production provisioning and database-user runbooks.
- Wire non-mutating production provisioning checks into CI.

Out of scope:

- Creating or executing a production CloudFormation change set.
- Creating production AWS resources.
- Writing production SSM runtime parameters.
- Reading decrypted production secrets outside the approved bootstrap flow.
- Enabling PayPal Live payments.

## Relevant Files or Modules

- `scripts/verify-production-provisioning-inputs.sh`
- `scripts/verify-production-provisioning-preflight.sh`
- `scripts/bootstrap-production-db-user.sh`
- `infra/cloudformation/production.parameters.test.json`
- `.github/workflows/ci.yml`
- `docs/operations/production-provisioning-runbook.md`
- `docs/operations/production-database-user.md`
- `docs/operations/production-deployment-foundation.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-06/add-production-provisioning-runbook.md`

## Key Design Decisions

- Mirror the staging provisioning workflow so production execution stays
  familiar and reviewable.
- Keep all production AWS mutations outside CI and outside local validation.
- Require a production bootstrap master password at
  `/time-archive/bootstrap/production/database/master-password`.
- Require production CloudFormation parameter inputs to target the expected AWS
  account and `ap-northeast-2`.
- Use production stack outputs to resolve EC2, RDS, runtime path, and IAM role
  values.
- Keep the production database application username aligned with the ignored
  local runtime parameter input: `timearchive_prod_app`.
- Temporarily grant the EC2 role read access only to the single production
  bootstrap master-password parameter during database user bootstrap, then
  remove that inline policy.

## Step-by-Step Execution Plan

- [x] Inspect staging provisioning input/preflight and DB bootstrap scripts.
- [x] Create this implementation plan.
- [x] Add production provisioning input validation script and test fixture.
- [x] Add production provisioning preflight self-test.
- [x] Add production database user bootstrap script.
- [x] Add production provisioning and database-user runbooks.
- [x] Add CI checks for non-mutating production provisioning validation.
- [x] Run shell syntax, fake AWS preflight, local runtime validation, JSON
  validation, and diff checks.

## Risks and Rollback Strategy

- Risk: A production provisioning script could mutate AWS state unexpectedly.
  Mitigation: preflight scripts are read-only and CI validates they do not
  contain mutating CloudFormation/SSM commands.
- Risk: The database bootstrap script reads a production master password.
  Mitigation: require an explicit execution flag, scope temporary IAM access to
  one bootstrap parameter, never print values, and remove the temporary policy
  in cleanup.
- Risk: Production DB user grants are too broad.
  Mitigation: document the current one-user migration/runtime compromise and
  keep split migration/runtime identities as a follow-up before broader scale.
- Rollback: Before launch, rerun the bootstrap with corrected SSM runtime
  password or disable the role with `ALTER ROLE ... NOLOGIN`. Do not drop the
  role without checking object ownership.

## Verification Plan

- Run `scripts/verify-production-provisioning-preflight.sh`.
- Run `bash -n scripts/bootstrap-production-db-user.sh`.
- Run `scripts/verify-production-cloudformation.sh`.
- Run `python -m json.tool infra/cloudformation/production.parameters.test.json`.
- Run `git diff --check`.

## Open Questions

- Actual production CloudFormation change-set creation and execution require
  explicit approval.
- Production alert email and SNS subscription confirmation remain
  operator-owned.

## Progress

- 2026-07-06: Started from the existing staging provisioning and DB bootstrap
  pattern to keep production operations consistent with staging.
- 2026-07-06: Added production provisioning input validation and fake AWS
  preflight self-test coverage.
- 2026-07-06: Added production DB user bootstrap script using production stack
  outputs, production SSM paths, and `timearchive_prod_app`.
- 2026-07-06: Added production provisioning and database-user runbooks and wired
  non-mutating checks into CI.

## Completion Summary

Production provisioning is now prepared up to the change-set boundary. Operators
can validate production CloudFormation parameters locally, run read-only AWS
preflight, create a review-only change set from the runbook, and bootstrap the
production database user after stack creation and explicit approval. No AWS
resources were created, no production SSM parameters were written, and no
secrets were printed or committed.

## Files Changed

- `.github/workflows/ci.yml`
- `infra/cloudformation/production.parameters.test.json`
- `scripts/verify-production-provisioning-inputs.sh`
- `scripts/verify-production-provisioning-preflight.sh`
- `scripts/bootstrap-production-db-user.sh`
- `docs/operations/production-provisioning-runbook.md`
- `docs/operations/production-database-user.md`
- `docs/operations/production-deployment-foundation.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-06/add-production-provisioning-runbook.md`

## Tests Run and Results

- `scripts/verify-production-provisioning-preflight.sh` passed.
- `bash -n scripts/bootstrap-production-db-user.sh` passed.
- `python -m json.tool infra/cloudformation/production.parameters.test.json`
  passed.
- `scripts/verify-production-provisioning-inputs.sh --parameters infra/cloudformation/production.parameters.example.json --expected-account-id 231851555445`
  passed without contacting AWS.
- `scripts/verify-production-runtime-parameters.sh` passed.
- `git diff --check` passed.

## Manual Verification Results

- Confirmed production preflight scripts contain no mutating AWS commands.
- Confirmed production database bootstrap uses the production stack name,
  runtime path, bootstrap master-password path, and `timearchive_prod_app`.
- Confirmed production runbook preserves separate change-set creation and
  execution approval boundaries.

## Known Limitations

- Live `--check-aws` preflight was not run in this task.
- No production change set was created.
- No production stack was executed.
- Production database URL remains unresolved until the production stack creates
  the RDS endpoint.

## Follow-Up Recommendations

- Create `infra/cloudformation/production.parameters.local.json` from the
  example and run local validation.
- Run read-only AWS preflight with the approved production-capable profile.
- Create a review-only production CloudFormation change set and inspect it
  before requesting execution approval.
