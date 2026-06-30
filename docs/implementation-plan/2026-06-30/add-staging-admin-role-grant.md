# Add Staging Admin Role Grant

## Objective

Add a controlled staging operator script that grants the `ADMIN` role to an
existing staging user through SSM Run Command without SSH, direct database
network access, or committed secrets.

## Scope

- Add a staging-only admin role grant script.
- Add static validation for the script contract.
- Add CI coverage for the static validation.
- Document the staging admin provisioning process and production boundary.

Out of scope:

- Actually granting an admin role in staging from this branch.
- Adding an admin invitation UI or API.
- Changing production admin provisioning.
- Implementing admin authorization smoke tests.

## Relevant Files Or Modules

- `scripts/grant-staging-admin-role.sh`
- `scripts/verify-staging-admin-role-grant.sh`
- `.github/workflows/ci.yml`
- `docs/operations/staging-admin-provisioning.md`
- `docs/operations/staging-deployment.md`
- `docs/operations/release-readiness-checklist.md`

## Key Design Decisions

- The script is staging-only and requires `--expected-account-id`.
- The target user must already exist through normal registration.
- The script accepts only an email address, not a password.
- The EC2 instance reads existing staging runtime DB credentials from SSM.
- The database update is idempotent: existing `ADMIN` remains `ADMIN`; existing
  `USER` is promoted to `ADMIN`; missing users fail.
- No RDS master password or temporary IAM policy is required.
- Actual execution is a high-impact staging data change and must be explicitly
  approved before running without `--dry-run`.

## Step-by-step Execution Plan

1. Inspect current user role schema and SSM scripting patterns.
2. Add the implementation plan.
3. Add the staging admin role grant script.
4. Add static validation for the script.
5. Add CI validation.
6. Update operations documentation.
7. Run local validation and dry-run.

## Risks And Rollback Strategy

- Risk: granting admin to the wrong user. Mitigation: require exact email,
  normalize to lowercase, verify the user exists, and print only the selected
  user id/email/role transition.
- Risk: credential exposure. Mitigation: read DB credentials on EC2 from SSM and
  never print passwords.
- Risk: accidental production use. Mitigation: hard-code staging stack defaults,
  require account and region checks, and validate the runtime parameter path is
  `/time-archive/staging/`.
- Rollback: run a reviewed demotion SQL command to set the same user's role
  back to `USER`, then record the operator action.

## Verification Plan

- Run shell syntax validation.
- Run `scripts/verify-staging-admin-role-grant.sh`.
- Run `scripts/grant-staging-admin-role.sh --dry-run` with staging account
  inputs.
- Run `git diff --check`.

## Open Questions

- None.

## Progress

- Created the dedicated branch from latest `main`.
- Added the staging admin role grant script.
- Added static validation and CI coverage for the script contract.
- Documented the staging admin provisioning runbook and production boundary.

## Completion Summary

Added a staging-only operator script that promotes an existing registered user
to `ADMIN` through SSM Run Command. The script uses the staging CloudFormation
outputs to resolve the EC2 instance and database endpoint, validates the AWS
account and staging runtime parameter path, reads only existing staging
application database credentials on EC2, and performs an idempotent role update.

The implementation does not use the RDS master password, does not add a web
admin bootstrap endpoint, and does not accept user passwords.

## Files Changed

- `scripts/grant-staging-admin-role.sh`
- `scripts/verify-staging-admin-role-grant.sh`
- `.github/workflows/ci.yml`
- `docs/operations/staging-admin-provisioning.md`
- `docs/operations/staging-deployment.md`
- `docs/operations/release-readiness-checklist.md`

## Tests Run And Results

- `bash -n scripts/grant-staging-admin-role.sh`: passed.
- `bash -n scripts/verify-staging-admin-role-grant.sh`: passed.
- `scripts/verify-staging-admin-role-grant.sh`: passed.
- `scripts/grant-staging-admin-role.sh --expected-account-id 231851555445 --profile time-archive-staging-admin --email staging-admin-dry-run@example.com --dry-run`: passed.
- `git diff --check`: passed.

## Manual Verification Results

After `aws sso login --profile time-archive-staging-admin`, the staging dry-run
resolved the expected account, stack outputs, EC2 instance, and target email,
then rendered the SSM command payload without sending it.

No SSM command was sent and no database changes were made.

## Known Limitations

- The script promotes users only; demotion remains a separate reviewed
  operation.
- Actual staging admin role grants require explicit approval because they mutate
  staging data.
- Production admin provisioning remains blocked until a stricter approval and
  audit process is defined.

## Follow-up Recommendations

- After AWS SSO login, run the script with `--dry-run` against the intended
  admin email.
- Register or select the staging admin user, then run the non-dry-run command
  only after explicit approval.
- Add staging admin authorization smoke verification after an admin user exists.
