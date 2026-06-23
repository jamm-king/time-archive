# Add Staging CloudFormation Foundation

## Objective

Add a reviewable, cost-conscious AWS CloudFormation foundation for the Time
Archive staging environment without creating or modifying any AWS or
Cloudflare resource.

## Scope

- Define staging networking, EC2, RDS, ECR, IAM, CloudWatch, and deployment
  outputs in CloudFormation.
- Add an example parameter file containing no secrets or account-specific real
  values.
- Add schema and architecture-policy validation through a shell script and
  GitHub Actions.
- Update deployment and release-readiness documentation with the implemented
  infrastructure-as-code boundary.

Out of scope:

- Running `aws cloudformation deploy`, creating a change set, or provisioning
  any billable resource.
- Creating Cloudflare Tunnels, DNS records, certificates, R2 buckets, or edge
  rules.
- Creating real SSM `SecureString` values or GitHub environment secrets.
- Publishing images to ECR or deploying application containers.
- Production infrastructure.

## Relevant Files Or Modules

- `infra/cloudformation/staging.yml`
- `infra/cloudformation/staging.parameters.example.json`
- `infra/cloudformation/requirements.txt`
- `scripts/verify-staging-cloudformation.sh`
- `.github/workflows/ci.yml`
- `docs/operations/ec2-rds-deployment-architecture.md`
- `docs/operations/production-deployment-foundation.md`
- `docs/operations/release-readiness-checklist.md`

## Exact Resource Boundary

The template will define:

- One VPC and Internet Gateway.
- One public application subnet with automatic public IPv4 assignment.
- Two private database subnets in distinct Availability Zones.
- One application security group with no ingress rules.
- One database security group allowing PostgreSQL only from the application
  security group.
- Immutable, scan-on-push ECR repositories for API and Web images.
- One Amazon Linux 2023 ARM64 EC2 instance with encrypted gp3 storage, IMDSv2,
  no SSH key, and an SSM-managed instance profile.
- One encrypted, private, Single-AZ RDS PostgreSQL instance using gp3 storage.
- Application, migration, Redis, `cloudflared`, and RDS CloudWatch log groups.
- Environment-scoped EC2 and GitHub OIDC IAM roles.
- Basic EC2 and RDS CloudWatch alarms with an optional notification email.

The template will not define ALB, ACM, NAT Gateway, Elastic IP, bastion host,
SSH ingress, application ingress, R2, or Cloudflare resources.

## Key Design Decisions

- The staging EC2 instance is in a public subnet only to obtain cost-conscious
  outbound connectivity. Its security group has no ingress rules.
- Cloudflare Tunnel remains the only public application ingress and is created
  outside AWS CloudFormation.
- RDS is private and reachable on port `5432` only from the application
  security group.
- RDS engine version is an explicit deployment parameter rather than an
  assumed repository default; the operator must select a version currently
  supported by RDS and verified by the application.
- The RDS master password is read through an SSM SecureString dynamic reference
  created before the stack. No database password is committed or passed in the
  example parameter file.
- Runtime application and migration users remain separate bootstrap work; the
  master credential is not injected into application containers.
- The GitHub OIDC provider is an account-level prerequisite passed by ARN. The
  staging stack creates narrowly scoped image-publisher and staging-deployer
  roles without attempting to own a shared provider.
- GitHub role trust is restricted to this repository's `main` ref for image
  publication and the `staging` GitHub environment for deployment.
- The initial stack uses AWS-managed encryption keys to avoid custom KMS key
  cost and policy complexity. A customer-managed key remains a future security
  decision.
- CloudFormation linting uses `cfn-lint`, the AWS CloudFormation project's
  maintained validator. Plain YAML parsing was rejected because it does not
  validate resource schemas or CloudFormation intrinsic functions. The version
  and its resolved dependency set are pinned for reproducible CI. Installed
  metadata was reviewed: licenses are MIT-0, Apache-2.0, MIT, BSD-family, and
  the compatible `Apache-2.0 AND CNRI-Python` expression; no GPL or AGPL
  dependency was introduced.

## IAM Boundary

The EC2 role will allow:

- SSM managed-instance operations through the AWS managed core policy.
- CloudWatch Agent operations through the AWS managed server policy.
- ECR pull access only to the two staging repositories, plus the account-level
  authorization-token action required by ECR.
- Read and decrypt access only under `/time-archive/staging/*`. Customer-managed
  KMS decrypt permission is intentionally absent until such a key is selected.

The GitHub image role will allow ECR push operations only to the two staging
repositories. The GitHub deploy role will allow SSM Run Command only against
the staging instance and the approved AWS shell document, plus command status
reads.

## Estimated Cost Impact

This task creates no resources and therefore incurs no cost. Applying the
future staging stack would create billable EC2, public IPv4, EBS, RDS, backup,
CloudWatch, and data-transfer usage. Staging should be created only for release
windows and removed when not needed. ECR storage and CloudWatch usage depend on
retention and volume.

## Step-By-Step Execution Plan

1. [Completed] Inspect the existing deployment foundation, architecture,
   workflow, and secret contracts.
2. [Completed] Add the staging CloudFormation template and non-secret example
   parameters.
3. [Completed] Add schema and architecture-policy validation.
4. [Completed] Add the CloudFormation verification job to CI.
5. [Completed] Update operations and release-readiness documentation.
6. [Completed] Run linting, policy checks, shell checks, and repository diff
   checks.

## Risks And Rollback Strategy

- A permissive security group could expose the origin or database. Policy
  verification rejects application ingress, public RDS, and non-source-group
  database access.
- Overbroad IAM could allow cross-environment access. Role resources and SSM
  paths are checked against staging-scoped ARNs and trust claims.
- An unsupported PostgreSQL version could block stack creation. The version is
  an explicit operator parameter and must be checked immediately before an
  approved change set.
- SSM dynamic references require prerequisite parameters and do not rotate the
  RDS password automatically without a stack update. Rotation remains a
  follow-up runbook.
- User data package availability can change. Host readiness must be verified on
  a staging instance before deployment automation is enabled. A CloudFormation
  creation signal makes package or checksum failure fail the stack, and RDS
  creation waits for that signal.
- Repository rollback is a Git revert. No external rollback is required because
  this task does not apply the template.
- A future applied staging stack should be removed through a reviewed
  CloudFormation delete after deciding whether to retain its final RDS
  snapshot.

## Verification Plan

- Run `cfn-lint infra/cloudformation/staging.yml`.
- Run `./scripts/verify-staging-cloudformation.sh`.
- Confirm no forbidden resource types or public ingress rules exist.
- Confirm all repository shell scripts pass `bash -n`.
- Run `git diff --check`.
- Confirm no secrets, account IDs, Tunnel tokens, or real parameter values are
  present in the staged diff.

## Open Questions

- The exact staging PostgreSQL engine version must be selected immediately
  before provisioning from currently supported RDS versions.
- The staging application hostname, Cloudflare Tunnel token, alert email, AWS
  account ID, and GitHub OIDC provider ARN remain owner-provided deployment
  inputs.
- Runtime and migration database user creation remains a separate data
  bootstrap design.

## Progress

- Confirmed `main` includes PR #62 and the Cloudflare Tunnel HTTPS decision.
- Confirmed production Compose publishes no application ports and already
  expects environment-scoped SSM and CloudWatch values.
- Added a schema-valid staging template covering the approved AWS resource and
  IAM boundaries.
- Added policy self-tests that detect public application ingress and public
  RDS configuration.
- Verified `cfn-lint` version `1.51.5` and its MIT-0 license from installed
  package metadata.

## Completion Summary

The repository now contains a staging-only CloudFormation foundation for the
approved EC2, private RDS, ECR, IAM, CloudWatch, SSM access, and no-public-origin
architecture. It includes a non-secret parameter example, pinned validation
tooling, a shell policy verifier with negative self-tests, and a required CI
job.

The EC2 bootstrap verifies the pinned Docker Compose binary, starts host
metrics, and signals CloudFormation. RDS creation waits for that signal so a
failed host bootstrap cannot produce a successful stack with a billable but
unusable database.

No AWS, Cloudflare, R2, GitHub environment, or SSM resource was created or
modified.

## Files Changed

- Added `infra/cloudformation/staging.yml`.
- Added `infra/cloudformation/staging.parameters.example.json`.
- Added the pinned `infra/cloudformation/requirements.txt` validator lock.
- Added `scripts/verify-staging-cloudformation.sh`.
- Added the CloudFormation verification job to `.github/workflows/ci.yml`.
- Added `docs/operations/staging-cloudformation-foundation.md`.
- Updated the deployment architecture, production deployment foundation,
  release-readiness checklist, and README.
- Added this implementation plan.

## Tests Run And Results

- `cfn-lint 1.51.5` against `ap-northeast-2`: passed.
- `./scripts/verify-staging-cloudformation.sh`: passed.
- Public application ingress and public RDS policy self-tests: passed.
- Shell syntax validation for all repository shell scripts: passed.
- GitHub Actions YAML and CloudFormation parameter JSON parsing: passed.
- Local Markdown link validation: passed.
- `git diff --check`: passed.
- Validator and resolved dependency license metadata: reviewed; no GPL or AGPL
  dependency was introduced.

## Manual Verification Results

No change set or stack was created because external infrastructure operations
can incur cost and require a separate explicit approval. EC2 user data, RDS
engine availability, IAM execution, alarm delivery, and deletion behavior must
therefore be verified in a future staging change set and stack.

## Known Limitations

- The template has not been evaluated by the AWS CloudFormation service.
- The final PostgreSQL engine version and Docker Compose ARM64 checksum are not
  selected.
- The account-level GitHub OIDC provider and staging SSM SecureStrings do not
  exist under this repository task.
- Runtime and migration database users are not bootstrapped.
- Image publication, SSM deployment, Cloudflare, R2, and application health
  verification remain separate tasks.
- Staging is Single-AZ and uses one EC2 host with unrestricted outbound access.

## Follow-Up Recommendations

1. Add the ECR ARM64 image publication workflow using the output image role.
2. Add the staging deployment workflow using the output SSM deploy role.
3. Collect the required owner inputs and produce an AWS change set without
   executing it.
4. Review the change set, estimated cost, IAM, RDS version, bootstrap behavior,
   and deletion snapshot policy before requesting approval to execute it.
