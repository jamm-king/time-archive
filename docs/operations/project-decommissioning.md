# Project Decommissioning

## Status

Time Archive infrastructure was decommissioned on 2026-07-27. The repository
is retained as an engineering and portfolio reference; no application
environment is currently deployed.

## Deleted AWS Stacks

The following CloudFormation stacks were deleted in AWS account `231851555445`
in `ap-northeast-2`:

- `time-archive-staging`
- `time-archive-production`

Deletion removed stack-managed application infrastructure, including EC2
instances, RDS DB instances, attached stack-managed EBS volumes, ECR
repositories and images, IAM roles, VPC resources, CloudWatch log groups and
alarms, SNS resources, and CloudFormation-managed SSM parameters.

The production and staging application URLs are intentionally unavailable.
Deployment and smoke workflows that require those environments are expected to
fail until a new environment is provisioned.

## Retained AWS Resources

CloudFormation created final RDS snapshots because the DB resources used a
`DeletionPolicy: Snapshot`. The following manual snapshots remain available:

| Environment | Snapshot | Allocated storage |
| --- | --- | --- |
| Production | `time-archive-production-snapshot-database-7bbyjjigjbzb` | 20 GiB |
| Staging | `time-archive-staging-snapshot-database-on0gmibqf39b` | 20 GiB |

These snapshots preserve a restoration option but continue to incur RDS backup
storage charges until deleted. The AWS pricing API returned USD 0.095 per
GB-month for additional PostgreSQL backup storage in `ap-northeast-2` on
2026-07-27. The actual charge depends on billed snapshot storage, not only the
allocated storage shown above.

The following manually provisioned SSM parameter namespaces also remain:

- `/time-archive/staging/`
- `/time-archive/production/`

They contain runtime configuration and secret values and must not be printed,
copied into documentation, or reused by another project without review.

## External Resources Outside CloudFormation

The following resources were intentionally outside the deleted stacks and were
not modified by the decommissioning action:

- Cloudflare R2 buckets and stored media objects.
- Cloudflare Tunnel configuration and published application routes.
- PayPal Sandbox and Live applications, webhooks, and credentials.
- GitHub Actions environments, repository variables, and secrets.
- The GitHub repository, source code, documentation, and CI workflows.

## Follow-Up Cleanup

To permanently retire the project and minimize ongoing cost, perform these
actions as separately approved destructive operations:

1. Delete the retained RDS snapshots after confirming that database recovery is
   no longer required.
2. Delete the staging and production SSM parameter namespaces, then revoke or
   rotate the corresponding R2, PayPal, and Cloudflare credentials at their
   providers.
3. Review and delete Cloudflare R2 objects and buckets that belong only to
   Time Archive.
4. Remove Cloudflare Tunnel routes and the tunnel when it is no longer used.
5. Remove PayPal webhooks and retire the associated applications after any
   required transaction records are retained elsewhere.
6. Remove or disable GitHub deployment secrets, variables, and environment
   approvals that reference the deleted AWS infrastructure.

Do not delete a resource merely because its name contains `time-archive` until
its ownership has been confirmed. In particular, do not delete shared
Cloudflare account resources or unrelated AWS resources.

## Future Reactivation

Reactivation is not a rollback of the deleted stacks. It requires a new
approved provisioning effort that should:

1. Review the archived architecture and release-readiness documentation.
2. Create new staging and production infrastructure with fresh IAM roles,
   databases, image repositories, and runtime parameters.
3. Create or rotate all external credentials instead of reusing archived
   secrets.
4. Restore an RDS snapshot only after validating its age, data retention
   obligations, and compatibility with the current application version.
5. Repeat deployment, smoke, backup, and security verification before exposing
   a public endpoint.

## Documentation Policy

Implementation plans, architecture documents, and historical operational
runbooks remain in the repository. Archived runbooks are retained to explain
the original engineering decisions, not as instructions for operating a live
environment.
