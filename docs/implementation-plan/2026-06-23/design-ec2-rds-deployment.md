# Design EC2 And RDS Deployment

## Objective

Define the cost-conscious staging and production deployment architecture for
Time Archive using EC2, RDS PostgreSQL, containerized Redis, Cloudflare R2,
CloudWatch, Sentry Developer, and SSM Parameter Store before provisioning any
external resources.

## Scope

- Define runtime topology, network boundaries, and environment isolation.
- Define EC2, RDS, Redis, R2, and ingress responsibilities.
- Define secret injection through SSM Parameter Store.
- Define image build, deployment, migration, health check, and rollback flows.
- Define the cost-conscious staging lifecycle and production sizing baseline.
- Reconcile the existing CI/CD strategy and release checklist with the selected
  deployment direction.

Out of scope:

- Creating or modifying AWS, Cloudflare, Sentry, PayPal, or GitHub resources.
- Adding production Compose files or deployment workflows.
- Implementing observability, media scanning, admin provisioning, or PayPal.
- Changing application behavior or public APIs.

## Relevant Files Or Modules

- `docs/operations/ec2-rds-deployment-architecture.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `docs/operations/release-readiness-checklist.md`
- `README.md`
- `docs/implementation-plan/2026-06-23/design-ec2-rds-deployment.md`

## Key Design Decisions

- Use one ARM64 EC2 `t4g.medium` instance for production application
  containers, but keep PostgreSQL in RDS.
- Use RDS PostgreSQL `db.t4g.small`, Single-AZ, encrypted gp3 storage as the
  MVP production baseline.
- Keep Redis on EC2 for sessions and distributed rate-limit counters.
- Use Cloudflare Tunnel as the only public application ingress so API and Web
  container ports are not published directly.
- Use separate R2 buckets, RDS databases, SSM paths, and deployment identities
  for staging and production.
- Keep staging stopped or absent outside release windows to control cost; never
  share production data or credentials.
- Use SSM Parameter Store `SecureString` values fetched by the EC2 instance
  role into a root-only runtime file outside the repository.
- Build immutable ARM64 images in CI, push them to ECR by Git SHA, and deploy
  through AWS SSM Run Command rather than SSH.
- Use CloudWatch for host, container, and deployment visibility. Sentry remains
  a later application integration within the free Developer plan.

## Step-By-Step Execution Plan

- [x] Confirm PR #59 is merged and create a dedicated documentation branch.
- [x] Inspect current deployment guidance, Compose files, Dockerfiles,
  configuration, and readiness gates.
- [x] Add this implementation plan.
- [x] Write the selected EC2/RDS deployment architecture and environment model.
- [x] Define secrets, networking, deployment, migration, rollback, backup, and
  observability boundaries.
- [x] Reconcile existing CI/CD, readiness, and README decision records.
- [x] Verify document consistency, links, and diff quality.
- [x] Record completion details and next implementation steps.

## Risks And Rollback Strategy

- Risk: A single EC2 instance remains an application availability bottleneck.
  - Mitigation: Keep RDS and R2 external, use immutable images, and define a
    replacement-instance recovery procedure. Move to multiple instances behind
    a load balancer when availability requirements justify the cost.
- Risk: Single-AZ RDS can be unavailable during an AZ or instance failure.
  - Mitigation: Enable backups and point-in-time recovery, test restore, and
    define Multi-AZ upgrade triggers before availability commitments increase.
- Risk: ARM64 image incompatibility can block deployment.
  - Mitigation: Build and smoke-test `linux/arm64` images in CI before resource
    provisioning. Do not assume future media-scanner images support ARM64.
- Risk: Runtime secret material is briefly present on the EC2 host.
  - Mitigation: Write it only under root-owned `/run`, never the checkout, and
    prevent command output and Docker logs from exposing it.
- Risk: Documentation can overstate readiness without deployed verification.
  - Mitigation: Keep every external-resource item blocked or pending until a
    staging implementation plan verifies it.

Rollback:

- Revert the documentation-only change. No infrastructure, runtime behavior,
  data, or external state is changed by this task.

## Verification Plan

- Cross-check every runtime variable and service with current application and
  Compose configuration.
- Confirm the selected design does not expose PostgreSQL, Redis, or API ports.
- Confirm staging and production never share R2, RDS, SSM, or payment data.
- Confirm the deployment flow uses immutable image tags and no long-lived AWS
  key in GitHub.
- Search for stale ECS-as-selected-target and undecided deployment statements.
- Run `git diff --check` and inspect all changed documents.

## Open Questions

- Final AWS account, VPC CIDR, domain names, and alert destinations remain
  owner-provided values at provisioning time.
- Production backup retention and RDS maintenance windows require explicit
  owner confirmation before resource creation.
- Cloudflare Tunnel is selected for the initial ingress design but must be
  validated against PayPal webhook reachability in staging.

## Progress Log

- 2026-06-23: Confirmed `main` contains PR #59 and is clean.
- 2026-06-23: Selected the cost-conscious EC2/RDS deployment baseline supplied
  by the project owner.
- 2026-06-23: Found that the existing CI/CD document still presents ECS Fargate
  as the recommended path and README still lists resolved owner decisions as
  undecided.
- 2026-06-23: Defined Cloudflare Tunnel ingress, private RDS and R2 boundaries,
  Redis persistence, SSM runtime injection, ECR images, GitHub OIDC, SSM Run
  Command deployment, controlled Flyway migration, and image rollback.
- 2026-06-23: Recorded current implementation gaps for client IP propagation,
  session cookie security, migration credentials, production Compose,
  observability, admin provisioning, PayPal, and media safety.

## Completion Summary

Defined the selected cost-conscious deployment architecture without creating
external resources. Production uses one ARM64 EC2 application host, RDS
PostgreSQL, Redis on EC2, private R2 storage, Cloudflare Tunnel, SSM Parameter
Store, CloudWatch, Sentry Developer, ECR, GitHub OIDC, and SSM Run Command.
Staging uses isolated resources and an on-demand lifecycle to limit cost.

The design explicitly keeps original media private, separates runtime and
migration database privileges, avoids public application and data ports, and
preserves immutable image rollback. Existing strategy, readiness, and README
documents now reflect the selected direction and remaining production blockers.

## Files Changed

- Added `docs/operations/ec2-rds-deployment-architecture.md`.
- Updated `docs/operations/ci-cd-and-testing-strategy.md`.
- Updated `docs/operations/release-readiness-checklist.md`.
- Updated `README.md`.
- Added this implementation plan.

## Tests Run And Results

- Local Markdown link scan across changed current-state documents: passed.
- Stale deployment and owner-decision phrase scan: passed.
- Official AWS, GitHub, and Cloudflare reference URL availability checks:
  passed.
- `git diff --check`: passed.

No application build or automated test suite was run because this task changes
documentation only.

## Manual Verification Results

- Confirmed PostgreSQL, Redis, API, and Web ports remain private in the design.
- Confirmed staging and production do not share RDS, R2, SSM, Cloudflare,
  PayPal, or Sentry identities and data.
- Confirmed deployment uses immutable image SHAs and short-lived GitHub OIDC
  credentials.
- Confirmed production fake payments, public original-media access, and
  long-lived GitHub AWS credentials are prohibited.
- Confirmed unresolved implementation work remains visible rather than being
  marked production-ready.

## Known Limitations

- No AWS, Cloudflare, GitHub, Sentry, PayPal, or R2 production resource was
  created or validated.
- `t4g.medium` capacity and every ARM64 image require staging load and smoke
  tests.
- Single-AZ RDS and one EC2 application host retain availability risks accepted
  for the cost-conscious MVP.
- Backup retention, maintenance windows, alert destinations, domain names, and
  exact account identifiers still require owner input.
- Cloudflare Tunnel ingress must be validated with PayPal Sandbox webhooks.

## Follow-Up Recommendations

1. Implement production-specific Compose, host bootstrap, and ARM64 image
   verification without provisioning external resources.
2. Define CloudFormation stacks and exact IAM policies for review.
3. Obtain owner approval for the itemized cost and external resource changes.
4. Provision isolated staging resources before production resources.
5. Continue with operational security and observability before media safety and
   PayPal integration.
