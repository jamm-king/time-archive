# Execute Production Runtime Parameter Preparation

## Objective

Prepare the local production runtime parameter input file for validation and
later SSM provisioning without committing or printing production secrets.

## Scope

- Fill repository-safe or agent-generated production runtime values in the
  ignored `deploy/production/runtime-parameters.local.json` file.
- Validate the local production runtime input without contacting AWS.
- Identify the remaining AWS production infrastructure steps required before
  SSM write and deployment.
- Do not print, commit, or copy secret values into repository documents.

Out of scope:

- Creating production AWS resources without explicit approval.
- Writing production SSM parameters without explicit approval.
- Enabling PayPal Live payments.
- Running production deployment.

## Relevant Files or Modules

- `deploy/production/runtime-parameters.local.json` (ignored, not committed)
- `scripts/put-production-runtime-parameters.sh`
- `scripts/verify-production-runtime-parameters.sh`
- `docs/operations/production-runtime-parameters.md`
- `docs/implementation-plan/2026-07-06/execute-production-runtime-parameter-preparation.md`

## Key Design Decisions

- Keep production PayPal disabled during initial runtime provisioning.
- Use the known Cloudflare account endpoint and production media domain for R2
  object reference configuration.
- Generate internal application secrets locally and never print them.
- Leave the database URL unresolved until production RDS exists and its private
  endpoint is known.
- Treat production RDS creation and SSM writes as high-impact operations
  requiring explicit approval.

## Step-by-Step Execution Plan

- [x] Confirm the local production runtime input file exists and is ignored.
- [x] Check which values still contain placeholders without printing secrets.
- [x] Fill agent-owned production runtime values in the ignored local file.
- [ ] Validate the local file without contacting AWS.
- [x] Prepare the approval boundary for production RDS/SSM operations.

## Risks and Rollback Strategy

- Risk: A secret could be leaked through logs or Git.
  Mitigation: do not print values; keep the local input ignored; commit only
  documentation that contains no secret values.
- Risk: Production values could accidentally reuse staging resources.
  Mitigation: the writer rejects staging references and the local validation
  fails on placeholders.
- Risk: Production infrastructure creation incurs cost and creates durable
  state.
  Mitigation: request explicit approval before creating RDS or writing SSM
  parameters; rollback by deleting only newly created resources before launch
  or overwriting specific incorrect parameters.

## Verification Plan

- Run `scripts/put-production-runtime-parameters.sh --validate-only`.
- Run `scripts/verify-production-runtime-parameters.sh`.
- Confirm `deploy/production/runtime-parameters.local.json` remains ignored.
- Do not run live AWS writes until approval is granted.

## Open Questions

- Production RDS endpoint is not available until production infrastructure is
  created or an existing endpoint is provided.
- Production RDS should mirror staging architecture, with production backup and
  deletion-protection safeguards.

## Progress

- 2026-07-06: Confirmed the local production runtime input exists and is
  ignored by Git.
- 2026-07-06: Confirmed remaining placeholders are database URL, database user,
  database password, R2 endpoint, R2 presigned endpoint, R2 public base URL, R2
  bucket, and rate-limit salt; client IP header is intentionally empty until
  Cloudflare production ingress is verified.
- 2026-07-06: Filled production R2 endpoint, presigned endpoint, object
  reference base URL, bucket, database username, generated database password,
  generated rate-limit salt, fixed PayPal Live callback values, and fixed AWS
  region/log-prefix values in the ignored local runtime input without printing
  values.
- 2026-07-06: Verified only `/time-archive/production/database/url` remains a
  placeholder. The validate-only writer now fails only because production RDS
  does not exist yet, so its private endpoint is unknown.
- 2026-07-06: Read-only AWS discovery through `time-archive-staging-admin`
  confirmed account `231851555445` has only the `time-archive-staging`
  CloudFormation stack and only the staging RDS instance. No production RDS or
  production CloudFormation stack exists yet.

## Current State

The ignored local production runtime parameter input is prepared as far as it
can be without production AWS infrastructure. SSM writes must not run yet
because the database URL still points at a placeholder.

The next required operation is production infrastructure provisioning,
preferably by extending the reviewed staging CloudFormation pattern to support
production with production-specific safeguards:

- environment path `/time-archive/production/`;
- isolated production VPC/subnets/security groups;
- PostgreSQL RDS matching the staging engine and instance class;
- production backup retention of at least 7 days;
- production deletion protection enabled;
- production master password bootstrap SSM path;
- production ECR, EC2, IAM, CloudWatch, and SNS resources isolated from
  staging.

## High-Impact Approval Boundary

Creating the production stack or RDS instance will create billable AWS
resources and durable production state. It requires explicit approval before
execution.

Impact:

- Monthly AWS cost starts for EC2, RDS, EBS, CloudWatch, snapshots, and related
  resources.
- A durable production database endpoint is created.
- Production IAM roles, ECR repositories, log groups, alarms, and network
  resources may be created if using the full stack.

Rollback:

- Before launch, delete the newly created production stack only after reviewing
  final snapshot behavior and ECR/log retention.
- If only SSM values are wrong, overwrite the affected parameter only; do not
  delete the whole production path.
- If RDS endpoint changes before launch, update
  `/time-archive/production/database/url` and rerun local and metadata
  validation.

Alternative:

- Create only a standalone production RDS instance, but this is less consistent
  with staging because it bypasses the reviewed EC2/network/IAM/CloudWatch
  foundation.
