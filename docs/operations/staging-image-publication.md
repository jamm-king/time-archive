# Staging Image Publication

## Purpose

This document defines how Time Archive publishes immutable staging API and Web
images to Amazon ECR. The repository contains the workflow, but no image has
been published and no AWS resource has been created by this implementation.

The workflow is `.github/workflows/publish-staging-images.yml` and is manually
triggered through GitHub Actions. It builds only `linux/arm64`, matching the
selected Graviton EC2 host.

## Publication Flow

```text
Manual workflow dispatch from main
  -> validate repository variables and full Git SHA
  -> request GitHub OIDC token
  -> assume staging image-publisher IAM role
  -> log in to private ECR
  -> inspect immutable API and Web SHA tags
  -> build and push API linux/arm64 image
  -> build and push Web linux/arm64 image
  -> compare ECR digests with Buildx outputs
  -> record immutable digest references in the job summary
```

The workflow does not deploy either image. Deployment remains a separate SSM
Run Command workflow and must consume the exact published SHA references.

## Security Boundary

The workflow has only these GitHub permissions:

- `contents: read`
- `id-token: write`

No AWS access key or ECR password is stored in GitHub. GitHub exchanges its OIDC
identity for the CloudFormation-created image-publisher role. IAM trust permits
only this repository's `main` branch subject, and the workflow also refuses to
run its publication job from another ref.

All third-party actions are pinned to reviewed full commit SHAs. The official
AWS actions handle OIDC credential cleanup, expected-account validation, and
masked ECR login. Docker actions build ARM64 images with BuildKit provenance
and SBOM attestations.

## Required Repository Variables

Configure these non-secret GitHub repository variables after the staging stack
exists:

| Variable | Source |
| --- | --- |
| `AWS_ACCOUNT_ID` | The 12-digit staging AWS account ID. |
| `AWS_REGION` | `ap-northeast-2`, matching the staging template. |
| `AWS_STAGING_IMAGE_PUBLISH_ROLE_ARN` | CloudFormation output `GitHubImagePublisherRoleArn`. |

Repository names are not variables. The workflow is fixed to:

```text
time-archive-staging-api
time-archive-staging-web
```

The workflow constructs each registry URI from the validated account, region,
and fixed repository name. It cannot accept a user-provided repository URI or
image tag.

## Immutable Tag And Digest Policy

Both images use the exact 40-character workflow commit SHA:

```text
{account}.dkr.ecr.ap-northeast-2.amazonaws.com/time-archive-staging-api:{git-sha}
{account}.dkr.ecr.ap-northeast-2.amazonaws.com/time-archive-staging-web:{git-sha}
```

The workflow never publishes `latest`, branch, staging, or release aliases.
ECR rejects tag replacement because the repositories are immutable.

After each push, the workflow reads the ECR image digest and compares it with
the Buildx output. A successful job summary records digest-qualified references
that can be audited independently of tags.

## Re-Run And Partial Publication

Before building, the workflow checks both SHA tags:

- Neither tag exists: build and publish both images.
- Both tags exist: verify them and complete as a no-op.
- Only one tag exists: fail before building and require operator review.

ECR does not provide a transaction spanning two repositories, so API success
followed by Web failure can leave a partial publication. Do not automatically
delete or overwrite the existing image. Review the failed build and the
published image first. Recovery normally requires explicit approval to delete
the orphan SHA image, followed by a clean workflow rerun. Manual publication of
the missing image is discouraged because it bypasses the paired workflow audit.

## Running The Workflow

Prerequisites:

1. Apply and verify the staging CloudFormation stack through an approved change
   set.
2. Create the account-level GitHub OIDC provider used by the stack role.
3. Configure the three repository variables.
4. Merge the publication workflow to `main`.
5. Confirm the target commit passed all required CI checks.

Then select **Publish staging images** in GitHub Actions and run it from the
`main` branch. No workflow input is accepted; the selected `main` commit is the
image source and tag.

## Verification And Handoff

After a real publication:

- Confirm both ECR repositories contain the same full Git SHA tag.
- Confirm the job summary contains valid `sha256` digest references.
- Confirm ECR scan-on-push completes and review findings before deployment.
- Confirm provenance and SBOM artifacts are present and associated with the
  expected image.
- Pass the exact API and Web SHA references to the staging deployment workflow.
- Record actor, workflow run, commit, tag, digest, scan result, and deployment
  decision.

## Cost And Retention

GitHub-hosted runner use, ECR storage, ECR scanning, attestations, and data
transfer can incur usage or cost. The staging repositories retain at most the
30 newest image groups through their lifecycle policy. Review actual ECR
artifact behavior and storage after the first publication because provenance
and SBOM manifests add registry objects.

## Current Limitations

- The workflow has not exchanged a real OIDC token or pushed to ECR.
- Repository variables and the account-level OIDC provider are not provisioned.
- ECR scan findings are not yet an automated release gate.
- No staging deployment workflow consumes the published references yet.
