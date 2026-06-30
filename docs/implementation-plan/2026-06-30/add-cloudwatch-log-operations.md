# Add CloudWatch Log Operations

## Objective

Add a verified CloudWatch log operations baseline for staging and production
readiness by documenting log groups, retention, request ID search, and sensitive
log checks.

## Scope

- Add a CloudWatch log operations runbook.
- Add a static validation script for CloudWatch log group naming, retention, and
  Docker `awslogs` usage.
- Add the validation script to CI.
- Align logging policy and release readiness notes with the current CloudWatch
  baseline.

Out of scope:

- Creating CloudWatch metric filters or alarms.
- Running live AWS log searches from CI.
- Adding structured request completion logs.
- Adding Sentry or another error tracking product.

## Relevant Files Or Modules

- `scripts/verify-cloudwatch-log-operations.sh`
- `.github/workflows/ci.yml`
- `docs/operations/cloudwatch-log-operations.md`
- `docs/operations/logging-policy.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-06-30/add-cloudwatch-log-operations.md`

## Key Design Decisions

- Keep the CI check static and deterministic. It validates the CloudFormation
  template and deployment compose logging contract, not the live AWS account.
- Use 14 days as the current staging and production MVP retention target because
  the existing CloudFormation template already provisions staging log groups
  with 14-day retention.
- Keep live CloudWatch search as a manual operational verification step because
  it depends on deployed traffic and operator AWS access.

## Step-by-step Execution Plan

1. Create this implementation plan.
2. Add a static CloudWatch log operations validator.
3. Add the validator to CI.
4. Add the CloudWatch log operations runbook.
5. Update logging policy and release readiness.
6. Run shell syntax, policy validation, and diff checks.

## Risks And Rollback Strategy

- Risk: static validation may create false confidence about live AWS state.
  Mitigation: document separate live verification steps for request ID search
  and retention checks.
- Risk: retention expectations could drift from cost decisions. Mitigation: keep
  14 days explicit and easy to update in one validator.
- Rollback: remove the validator, CI step, and runbook if the logging strategy
  changes.

## Verification Plan

- Run `bash -n scripts/verify-cloudwatch-log-operations.sh`.
- Run `scripts/verify-cloudwatch-log-operations.sh`.
- Run `git diff --check`.

## Open Questions

- None.

## Progress

- Created `feature/cloudwatch-log-operations` from latest `main`.
- Added the static CloudWatch log operations validator.
- Added the CloudWatch log operations runbook.
- Updated logging policy, release readiness, and CI wiring.

## Completion Summary

Added a CloudWatch log operations baseline covering staging log groups, 14-day
retention, request ID search, sensitive-log sampling, and static CI validation
for the CloudFormation and Docker Compose logging contract.

## Files Changed

- `.github/workflows/ci.yml`
- `scripts/verify-cloudwatch-log-operations.sh`
- `docs/operations/cloudwatch-log-operations.md`
- `docs/operations/logging-policy.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-06-30/add-cloudwatch-log-operations.md`

## Tests Run And Results

- `C:\Program Files\Git\bin\bash.exe -n scripts/verify-cloudwatch-log-operations.sh`: passed.
- `PYTHONPATH=D:\develop\time-archive\temp\cfn-lint C:\Program Files\Git\bin\bash.exe -lc './scripts/verify-cloudwatch-log-operations.sh'`: passed.
- `PYTHONPATH=D:\develop\time-archive\temp\cfn-lint CFN_LINT_BIN=D:\develop\time-archive\temp\cfn-lint\bin\cfn-lint C:\Program Files\Git\bin\bash.exe -lc './scripts/verify-staging-cloudformation.sh'`: passed.
- `git diff --check`: passed.

## Manual Verification Results

- Live CloudWatch retention and request ID searches were not executed in this
  implementation. They remain documented manual staging verification steps.

## Known Limitations

- No CloudWatch metric filters or alarms were added.
- Request duration access logs are still a follow-up.
- The static validator does not prove live AWS log group retention; it verifies
  the reviewed IaC and deployment contract.

## Follow-up Recommendations

- Run live staging retention checks with `aws logs describe-log-groups`.
- Generate a staging request with a known `X-Request-Id` and verify CloudWatch
  searchability.
- Add alerting after log fields and failure patterns are confirmed.
