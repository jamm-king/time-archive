# Add Production CloudFormation Foundation

## Objective

Add a production CloudFormation foundation that mirrors the reviewed staging
deployment architecture while strengthening production database safety controls
before any production resources are created.

## Scope

- Add a production CloudFormation template derived from the staging foundation.
- Add a production parameter example file.
- Add local/CI validation for production CloudFormation schema and architecture
  policy.
- Document the production provisioning boundary and next operator steps.

Out of scope:

- Creating a production CloudFormation change set.
- Executing a production stack.
- Writing production SSM runtime parameters.
- Enabling PayPal Live payments.

## Relevant Files or Modules

- `infra/cloudformation/production.yml`
- `infra/cloudformation/production.parameters.example.json`
- `scripts/verify-production-cloudformation.sh`
- `.github/workflows/ci.yml`
- `.gitignore`
- `docs/operations/production-deployment-foundation.md`
- `docs/implementation-plan/2026-07-06/add-production-cloudformation-foundation.md`

## Key Design Decisions

- Keep production topology aligned with staging: one public outbound-only EC2
  host, private RDS subnets, no inbound application security group rules,
  Cloudflare Tunnel as the application ingress, ECR, SSM, CloudWatch, SNS, and
  GitHub OIDC roles.
- Use production-isolated names, SSM paths, IAM policy names, GitHub
  environment trust, and log namespaces.
- Strengthen production RDS defaults relative to staging:
  - backup retention at least 7 days;
  - deletion protection enabled;
  - automated backups retained on deletion;
  - final snapshot behavior preserved through CloudFormation deletion/update
    policies.
- Use a non-overlapping production VPC CIDR range.
- Keep the first operation as change-set creation and review, not stack
  execution.

## Step-by-Step Execution Plan

- [x] Inspect staging CloudFormation template and validation script.
- [x] Create this implementation plan.
- [x] Add production CloudFormation template and parameter example.
- [x] Add production CloudFormation validation script.
- [x] Add CI validation.
- [x] Update production deployment documentation.
- [x] Run CloudFormation validation and diff checks locally.

## Risks and Rollback Strategy

- Risk: Production stack execution creates billable durable AWS resources.
  Mitigation: this task only adds repository files; actual change-set creation
  and execution remain separate approval-gated operations.
- Risk: Production template drifts from staging.
  Mitigation: derive from staging and keep validation policy checks parallel.
- Risk: Production database could be accidentally less protected than staging.
  Mitigation: validator requires deletion protection and backup retention.
- Rollback: revert this branch before any stack operation. If a future
  production stack is created and must be removed before launch, delete only
  after reviewing final snapshot and deletion protection behavior.

## Verification Plan

- Run `scripts/verify-production-cloudformation.sh`.
- Run `scripts/verify-staging-cloudformation.sh` to ensure staging validation
  remains intact.
- Run `python -m json.tool` for production parameter example.
- Run `git diff --check`.

## Open Questions

- Production stack execution requires explicit approval and review of the
  generated change set.
- Production alert email and SNS subscription confirmation are operator-owned.

## Progress

- 2026-07-06: Started from staging foundation because production should mirror
  staging architecture while using production-isolated resources and stronger
  RDS safety defaults.
- 2026-07-06: Added `infra/cloudformation/production.yml` with production-only
  environment values, non-overlapping CIDRs, production bootstrap SSM path,
  production GitHub environment trust, and production RDS backup/deletion
  safeguards.
- 2026-07-06: Added production CloudFormation validation and CI coverage.
- 2026-07-06: Updated production deployment documentation and release readiness
  checklist references.

## Completion Summary

The repository now has a production CloudFormation foundation that mirrors the
staging topology while isolating production resources and strengthening RDS
safety defaults. The production validator checks schema and architecture policy
locally and in CI. No AWS production resources were created by this task.

## Files Changed

- `.github/workflows/ci.yml`
- `.gitignore`
- `infra/cloudformation/production.yml`
- `infra/cloudformation/production.parameters.example.json`
- `scripts/verify-production-cloudformation.sh`
- `docs/operations/production-deployment-foundation.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-06/add-production-cloudformation-foundation.md`

## Tests Run and Results

- `bash -n scripts/verify-production-cloudformation.sh` passed.
- `python -m json.tool infra/cloudformation/production.parameters.example.json`
  passed.
- `scripts/verify-production-cloudformation.sh` passed using the pinned
  CloudFormation validator in a temporary local virtual environment.
- `scripts/verify-staging-cloudformation.sh` passed using the same temporary
  local virtual environment.
- `git diff --check` passed.

## Manual Verification Results

- Confirmed production CloudFormation files contain no staging strings.
- Confirmed the production validator enforces production bootstrap path,
  production GitHub deployment environment trust, RDS deletion protection,
  retained automated backups, and at least 7 days of backup retention.

## Known Limitations

- No production CloudFormation change set was created.
- No production stack was executed.
- No production RDS endpoint exists yet, so
  `/time-archive/production/database/url` remains unresolved in the ignored
  local runtime parameter input.

## Follow-Up Recommendations

- Create a production CloudFormation local parameter file outside Git or at the
  ignored `infra/cloudformation/production.parameters.local.json` path.
- Create and review a production CloudFormation change set before execution.
- After stack creation, use the `DatabaseEndpoint` output to complete
  `deploy/production/runtime-parameters.local.json` and run production runtime
  parameter validation.
