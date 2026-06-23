# Add Production Deployment Foundation

## Objective

Add a reviewable production deployment foundation for the selected EC2, RDS,
Redis, R2, Cloudflare Tunnel, SSM Parameter Store, and CloudWatch architecture
without creating or modifying external infrastructure.

## Scope

- Add a production-only Docker Compose definition.
- Define the SSM parameter-to-runtime environment contract.
- Add host bootstrap, deployment, health verification, and static policy
  verification scripts.
- Add CI validation for the deployment policy and ARM64 container builds.
- Update operations documentation and the release-readiness checklist.

Out of scope:

- Creating AWS, Cloudflare, R2, Sentry, PayPal, or GitHub resources.
- Pushing images to ECR or running an actual staging deployment.
- Application feature changes, database schema changes, and PayPal integration.

## Relevant Files Or Modules

- `deploy/production/docker-compose.yml`
- `deploy/production/runtime.env.example`
- `deploy/production/ssm-parameters.example.json`
- `deploy/production/*.sh`
- `scripts/verify-production-deployment.sh`
- `.github/workflows/ci.yml`
- `docs/operations/ec2-rds-deployment-architecture.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- Production Compose contains API, Web, Redis, and Cloudflare Tunnel only;
  PostgreSQL and MinIO remain outside the application host.
- Flyway runs as an explicit one-shot migration profile before application
  replacement. The API runtime does not run Flyway.
- Sensitive runtime values are fetched from environment-specific SSM paths and
  rendered to a root-readable shell environment file with mode `0600`.
- API and Web images use full Git SHA tags. Third-party infrastructure images
  use reviewed digest references.
- No application ports are published on the host. Cloudflare Tunnel is the
  ingress path.
- Compose logs use the `awslogs` driver and environment-specific log streams.
- CI uses the maintained official Docker Buildx and QEMU actions. The
  alternative of checking image manifests only was rejected because it would
  not compile application images for the selected ARM64 target. These actions
  are CI tooling and do not change application runtime dependencies.
- No external infrastructure command is executed in this task.

## Step-By-Step Execution Plan

1. [Completed] Inspect existing Compose, Dockerfile, CI, configuration, and
   deployment documentation conventions.
2. [Completed] Add the production Compose and runtime configuration contract.
3. [Completed] Add and stabilize SSM rendering, host bootstrap, deployment,
   health verification, and static policy verification scripts.
4. [Completed] Add production deployment policy and ARM64 image build checks to
   GitHub Actions.
5. [Completed] Update operations documentation and the release-readiness
   checklist.
6. [Completed] Run static validation, Docker Compose validation, ARM64 builds,
   and repository checks.

## Risks And Rollback Strategy

- An incorrect runtime contract could omit a required secret or expose it to an
  unrelated container. Static policy validation checks required values and
  service-level environment isolation.
- ARM64 incompatibility could make the selected Graviton host unusable. CI
  builds both application images for `linux/arm64` before deployment work
  proceeds.
- A failed schema migration could prevent deployment. The deployment script
  stops before replacing running services when the one-shot migration fails.
- Runtime files may contain sensitive values. They are generated outside the
  repository, written atomically, and restricted to mode `0600`.
- Rollback for this repository-only task is removal of the added deployment
  files and CI job. No external state is changed.

## Verification Plan

- Run `./scripts/verify-production-deployment.sh` from Git Bash or Linux.
- Validate shell syntax for every added shell script.
- Validate rendered Compose JSON and negative missing-secret behavior.
- Build API and Web images for `linux/arm64` with Docker Buildx.
- Run `git diff --check` and inspect executable file modes.
- No production manual verification is possible until approved infrastructure
  exists.

## Open Questions

- Exact ECR repository URLs and third-party image digests will be selected when
  the infrastructure stack is provisioned.
- The migration-only database identity remains a separate follow-up because the
  current application uses the primary datasource for Flyway and runtime data.

## Progress

- Production Compose, placeholder runtime contract, SSM fixture, and
  operational scripts are implemented.
- Static policy validation, fail-fast secret validation, immutable image
  validation, and SSM fixture rendering pass locally.
- GitHub Actions now performs the same deployment policy validation and builds
  API and Web images for `linux/arm64`.

## Completion Summary

The repository now contains a production-only Compose topology, an
environment-scoped SSM runtime contract, deterministic host and deployment
scripts, health checks, and an automated policy verifier. The CI workflow adds
real ARM64 image builds without pushing or provisioning external resources.

## Files Changed

- Added `deploy/production/docker-compose.yml`.
- Added the production runtime example and SSM fixture under
  `deploy/production`.
- Added host bootstrap, runtime rendering, deployment, and health scripts under
  `deploy/production`.
- Added `scripts/verify-production-deployment.sh`.
- Updated `.github/workflows/ci.yml`.
- Added `docs/operations/production-deployment-foundation.md` and updated the
  deployment architecture and release-readiness documents.

## Tests Run And Results

- `./scripts/verify-production-deployment.sh`: passed on Windows Git Bash.
- Docker Compose production policy and rendered SSM fixture validation: passed.
- Missing database secret and mutable Redis image negative tests: passed.
- Shell syntax validation for repository shell scripts: passed.
- `git diff --check`: passed before final plan update and will be repeated.
- Local `linux/arm64` Docker build: attempted twice but did not complete within
  the 20-minute combined and 5-minute API-only limits on Docker Desktop.
  GitHub Actions remains the required ARM64 build verification environment.

## Manual Verification Results

No staging or production deployment was run. This is intentional because the
required AWS and Cloudflare resources do not exist under an approved
provisioning plan.

## Known Limitations

- ECR repositories, GitHub OIDC roles, SSM parameters, CloudWatch resources,
  Cloudflare Tunnel, RDS, and EC2 are not provisioned.
- The deployment records the previous release but does not automatically
  execute rollback.
- Runtime and Flyway database identities are not separated yet.
- ARM64 completion must be confirmed by the new CI job.

## Follow-Up Recommendations

1. Add reviewed CloudFormation for the selected AWS baseline under a separate
   high-impact implementation plan.
2. Add ECR image publication, GitHub OIDC authentication, and SSM Run Command
   deployment after the infrastructure roles exist.
3. Provision staging first and verify migration, health checks, rollback, log
   delivery, and environment isolation before production.
