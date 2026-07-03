# Document Observability Minimum

## Objective

Define the minimum observability and alerting baseline required before adding
PayPal and accepting paid production traffic.

## Scope

- Add an operations runbook for minimum observability.
- Define CloudWatch, Sentry, metrics, and alerting responsibilities.
- Define minimum alert surfaces for EC2, containers, RDS, deployment, storage,
  payment, and application errors.
- Update release readiness documentation.
- Link the runbook from existing operations documentation.

## Out Of Scope

- Adding Sentry SDKs.
- Creating CloudWatch alarms or metric filters.
- Changing CloudFormation.
- Adding OpenTelemetry or application metrics code.
- Implementing PayPal.

## Relevant Files Or Modules

- `docs/operations/observability-minimum.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/ec2-rds-deployment-architecture.md`
- `docs/operations/logging-policy.md`
- `docs/implementation-plan/2026-07-03/document-observability-minimum.md`

## Key Design Decisions

- Use CloudWatch as the minimum infrastructure, runtime log, and alarm baseline.
- Use Sentry Developer as the selected error grouping path for API and Web
  application errors, pending SDK implementation.
- Keep Sentry events scrubbed of credentials, cookies, CSRF tokens, payment
  payload secrets, and presigned URLs.
- Treat PayPal webhook failures and payment idempotency errors as explicit
  alert surfaces before real payment launch.
- Keep this step documentation-only so PayPal design can proceed with clear
  operating gates.

## Step-By-Step Execution Plan

1. Add this implementation plan.
2. Add the minimum observability runbook.
3. Update release readiness checklist rows for error tracking, metrics, and
   alerts.
4. Link the runbook from architecture/logging documentation.
5. Run documentation diff checks.

## Risks And Rollback Strategy

- Risk: The checklist could imply observability tools are already provisioned.
  - Mitigation: Keep live implementation rows at `Needs verification` or
    `Blocked` until integrations and alarms exist.
- Risk: Sentry could collect sensitive data if added later without scrubbing.
  - Mitigation: Document strict event filtering requirements before SDK
    integration.
- Rollback: Revert documentation changes and restore prior checklist text.

## Verification Plan

- Run `git diff --check`.
- Review release readiness rows for status accuracy.

## Open Questions

- None for this documentation step.

## Progress

- Created implementation plan.
- Added minimum observability operations runbook.
- Updated release readiness checklist.
- Linked the runbook from EC2/RDS architecture and logging policy.

## Completion Summary

The minimum observability baseline is now documented for paid production. The
runbook defines current visibility, remaining gaps, the CloudWatch/Sentry
tooling decision, Sentry filtering requirements, minimum metrics, minimum
alerts, PayPal-related alert surfaces, and verification gates before PayPal and
before paid production traffic.

The release readiness checklist now reflects that error tracking, metrics, and
alerts are no longer undefined blockers, but still require implementation and
target-environment verification.

## Files Changed

- `docs/operations/observability-minimum.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/ec2-rds-deployment-architecture.md`
- `docs/operations/logging-policy.md`
- `docs/implementation-plan/2026-07-03/document-observability-minimum.md`

## Tests Run And Results

- `git diff --check`: passed.

## Manual Verification Results

- Reviewed release readiness rows to ensure no unimplemented Sentry, metric, or
  alert capability is marked `Ready`.
- Reviewed PayPal-specific alert surfaces so the PayPal design can account for
  webhook signature, processing, duplicate delivery, and idempotency failures.

## Known Limitations

- No Sentry SDK is integrated.
- No CloudWatch metric filters or alarms were created.
- No live production alert route was tested.
- No application metrics code was added.

## Follow-Up Recommendations

- Proceed with PayPal integration design using the documented webhook failure
  and idempotency alert surfaces.
- Add Sentry and CloudWatch alert implementation before paid production
  cutover.
