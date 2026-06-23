# Staging CloudFormation Foundation

## Purpose

This document describes the repository-owned AWS CloudFormation foundation for
Time Archive staging. The template is not evidence that any AWS or Cloudflare
resource has been created.

The template is intentionally staging-only. Production infrastructure requires
a separate review after staging deployment, recovery, cost, and security
behavior are verified.

## Files

| File | Responsibility |
| --- | --- |
| `infra/cloudformation/staging.yml` | Staging AWS resource and IAM definition. |
| `infra/cloudformation/staging.parameters.example.json` | Non-secret placeholder inputs. |
| `infra/cloudformation/requirements.txt` | Pinned CloudFormation validator. |
| `scripts/verify-staging-cloudformation.sh` | Schema, architecture-policy, and policy self-test entry point. |

## Resource Topology

The stack defines:

- One VPC and Internet Gateway.
- One public application subnet with automatic public IPv4 assignment.
- Two private RDS subnets in distinct Availability Zones.
- One ARM64 Amazon Linux 2023 EC2 instance.
- One private Single-AZ PostgreSQL RDS instance.
- Immutable ECR repositories for API and Web images.
- CloudWatch log groups, host metrics, and basic alarms.
- An EC2 instance role, a GitHub image-publisher role, and a GitHub staging
  deployment role.
- An SNS alert topic with an optional email subscription.

The application subnet is public only to provide the single EC2 host with
cost-conscious outbound access. Its security group has no ingress rules. The
template does not create an ALB, ACM certificate, NAT Gateway, Elastic IP,
bastion host, SSH key, or public application listener.

Cloudflare Tunnel remains the only application ingress. Cloudflare, R2, DNS,
edge certificates, WAF rules, and Tunnel credentials are outside this AWS
stack.

## Database Boundary

RDS uses two private subnets and accepts PostgreSQL traffic only from the
application security group. It is encrypted, not publicly accessible, and uses
gp3 storage with bounded autoscaling.

The PostgreSQL engine version has no repository default. Before each approved
change set, select an RDS-supported version and verify it against Time Archive
migrations and drivers.

The stack reads the master password from this prerequisite SSM SecureString:

```text
/time-archive/staging/database/master-password
```

CloudFormation uses the master credential only to create or update RDS. It is
not written to the example parameter file and must not be injected into the API
container. Runtime and Flyway database identities remain a separate bootstrap
task.

Deleting the stack creates a final RDS snapshot. The snapshot continues to
incur storage cost until it is reviewed and explicitly deleted.

## Host Bootstrap Boundary

EC2 user data:

- Installs Docker, Python, curl, and CloudWatch Agent.
- Enables Docker and SSM Agent.
- Downloads a pinned ARM64 Docker Compose binary.
- Verifies its owner-provided SHA-256 before execution.
- Creates the runtime directories expected by the deployment scripts.
- Starts basic host memory and disk metrics.
- Signals CloudFormation only after bootstrap completes successfully.

The user data does not contain application secrets, pull application images, or
start Time Archive. Image publication and SSM Run Command deployment remain
separate workflows. RDS creation waits for the EC2 bootstrap success signal so
an incomplete host does not produce an apparently healthy staging stack.

## IAM Boundary

The EC2 role can:

- Register with SSM and publish CloudWatch Agent data.
- Pull only the staging API and Web ECR repositories.
- Read only `/time-archive/staging/*` parameters.

The GitHub image-publisher role trusts only the repository `main` branch and
can push only the two staging ECR repositories. The GitHub deployment role
trusts only the `staging` GitHub environment and can send the approved AWS shell
document only to the staging EC2 instance.

The account-level GitHub OIDC provider is a prerequisite passed by ARN. The
staging stack does not own or replace this shared account resource.

## Validation

Install the pinned validator in an isolated environment, then run the shell
entry point:

```bash
python -m pip install --requirement infra/cloudformation/requirements.txt
./scripts/verify-staging-cloudformation.sh
```

CI performs the same validation without AWS credentials. The policy check
rejects:

- ALB, ACM, NAT Gateway, and Elastic IP resources.
- Application ingress rules or public RDS.
- Database access not sourced from the application security group.
- Unencrypted EC2 or RDS storage.
- Mutable or unscanned ECR repositories.
- Unrestricted GitHub OIDC subjects.
- Cross-environment SSM parameter reads.

## Provisioning Prerequisites

Before requesting an AWS change set, the project owner must provide or approve:

- AWS account and region.
- An account-level GitHub Actions OIDC provider ARN.
- A currently supported PostgreSQL engine version.
- A reviewed Docker Compose version and its Linux ARM64 SHA-256.
- The staging RDS master password as an SSM SecureString.
- An optional alert email and the resulting SNS subscription confirmation.
- The staging Cloudflare hostname, named Tunnel, Tunnel token, R2 resources,
  and runtime SSM parameters.
- The expected staging operating window and estimated monthly cost.

Do not put real values into the committed example file. Create an ignored or
external parameter file for an approved change set.

## Change Set And Approval Boundary

The first infrastructure operation must create a CloudFormation change set,
not execute a stack directly. Review the exact resources, replacements, IAM
changes, public IPv4 assignment, RDS settings, estimated cost, and deletion
behavior.

Executing the change set creates billable external state and requires explicit
project-owner approval. The same approval boundary applies to stack deletion
because deletion takes a final RDS snapshot and removes ECR images, log groups,
and the staging host.

## Known Limitations

- No production stack exists.
- No deployment or image-push workflow exists yet.
- The template does not create SecureString values, runtime database users,
  Cloudflare resources, or R2 resources.
- Staging uses one EC2 instance and Single-AZ RDS.
- EC2 outbound access is unrestricted initially; tighten it only after required
  AWS, Cloudflare, R2, package, and image endpoints are measured.
- CloudWatch alarms are basic and do not replace application-level metrics or
  Sentry.
