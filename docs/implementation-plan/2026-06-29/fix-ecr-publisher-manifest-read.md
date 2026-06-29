# Fix ECR Publisher Manifest Read Permission

## Objective

Allow the staging image publication workflow to complete Docker Buildx push
verification against private Amazon ECR repositories.

## Scope

- Update the staging CloudFormation publisher role policy.
- Extend local CloudFormation policy verification.
- Update staging image publication documentation with the observed failure and
  recovery path.

Out of scope:

- Changing repository names, image tags, or publication workflow behavior.
- Deploying application containers to EC2.
- Adding broader ECR or administrator permissions.

## Relevant Files

- `.github/workflows/publish-staging-images.yml`
- `infra/cloudformation/staging.yml`
- `scripts/verify-staging-cloudformation.sh`
- `docs/operations/staging-image-publication.md`

## Key Design Decisions

- Keep the GitHub image publisher role scoped to the two staging ECR
  repositories.
- Add only the ECR read action needed for manifest retrieval after push.
- Preserve immutable SHA-tag publication and existing digest verification.

## Execution Plan

1. Create a dedicated `fix/ecr-publisher-manifest-read` branch from `main`.
2. Add `ecr:BatchGetImage` to the GitHub image publisher role policy.
3. Update the CloudFormation verifier so the missing permission is caught
   locally.
4. Document the failure mode and the required stack update.
5. Run focused verification.
6. Commit and push for review.

## Risks And Rollback Strategy

- Risk: granting an overly broad ECR permission. Mitigation: scope
  `ecr:BatchGetImage` to the existing API and Web repository ARNs only.
- Risk: the already-created IAM role is not updated by code changes alone.
  Mitigation: run a CloudFormation stack update after the PR is merged.
- Rollback: revert the commit and update the stack back to the previous
  template if the permission change causes an unexpected issue.

## Verification Plan

- Run `scripts/verify-staging-cloudformation.sh`.
- Run shell syntax validation for changed shell scripts.
- Run `git diff --check`.
- After merge and stack update, rerun the GitHub Actions
  `Publish staging images` workflow from `main`.

## Open Questions

- None.

## Progress

- Created the dedicated fix branch.
- Confirmed the workflow failed during Buildx/ECR manifest verification with a
  `403 Forbidden` response.
- Added `ecr:BatchGetImage` to the GitHub image publisher role while keeping
  the permission scoped to the two staging ECR repositories.
- Extended CloudFormation verification to reject a publisher role that cannot
  read pushed image manifests.
- Updated the staging image publication runbook with the failure mode and stack
  update requirement.

## Completion Summary

The staging image publisher role now includes the ECR manifest read permission
needed by Docker Buildx after pushing an image. The local verifier now checks
for that permission so the issue is caught before another stack update.

## Files Changed

- `infra/cloudformation/staging.yml`
- `scripts/verify-staging-cloudformation.sh`
- `docs/operations/staging-image-publication.md`
- `docs/implementation-plan/2026-06-29/fix-ecr-publisher-manifest-read.md`

## Tests Run And Results

- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-staging-cloudformation.sh`
  - Passed.
- `CFN_LINT_BIN=D:/develop/time-archive/temp/cfn-lint/bin/cfn-lint.exe`
  `PYTHONPATH=D:/develop/time-archive/temp/cfn-lint`
  `C:\Program Files\Git\bin\bash.exe -lc "scripts/verify-staging-cloudformation.sh"`
  - Passed.
- `git diff --check`
  - Passed.

## Manual Verification Results

The existing workflow failure was reviewed against the current CloudFormation
publisher role policy. The failing Buildx request required manifest read access,
and the new permission is limited to the existing staging API and Web ECR
repository ARNs.

## Known Limitations

- The live AWS IAM role is unchanged until the merged CloudFormation template is
  applied with a staging stack update.
- The GitHub Actions image publication workflow must be rerun after the stack
  update to confirm the fix against real ECR.

## Follow-up Recommendations

- Merge this fix, update the staging CloudFormation stack, and rerun
  `Publish staging images` from `main`.
- If ECR still rejects manifest verification, inspect whether Buildx provenance
  and SBOM attestations require an additional narrow ECR read action.
