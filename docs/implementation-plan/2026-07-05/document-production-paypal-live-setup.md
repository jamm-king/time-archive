# Document Production PayPal Live Setup

## Objective

Create a production PayPal Live setup runbook that defines the operator steps,
runtime parameters, first low-value payment drill, refund procedure, and
reconciliation checks required before Time Archive can collect real payments.

## Scope

- Add a production PayPal Live setup runbook.
- Connect the runbook from release readiness and production runtime parameter
  references.
- Verify the production SSM parameter example includes the PayPal production
  placeholders required by the runtime renderer.

Out of scope:

- Creating PayPal Live resources.
- Writing real PayPal credentials to SSM.
- Running a live payment or refund.
- Changing application code.
- Changing production infrastructure.

## Relevant Files or Modules

- `docs/operations/production-paypal-live-setup.md`
- `docs/operations/production-runtime-parameters.md`
- `docs/operations/release-readiness-checklist.md`
- `deploy/production/ssm-parameters.example.json`
- `docs/implementation-plan/2026-07-05/document-production-paypal-live-setup.md`

## Key Design Decisions

- Treat PayPal Live setup as a production launch gate, not a code feature.
- Keep Sandbox and Live resources strictly separate.
- Keep production payment collection disabled until production SSM parameters,
  Cloudflare routing, first low-value payment, webhook, refund, and dashboard
  reconciliation are verified.
- Use repository-safe placeholders only. No secrets or live identifiers should
  be committed unless they are intentionally public-safe references.

## Step-by-Step Execution Plan

- [x] Inspect existing PayPal integration, production runtime parameter, and
  release readiness docs.
- [x] Create this implementation plan.
- [x] Add production PayPal Live setup runbook.
- [x] Update production runtime and release readiness docs to reference the
  runbook.
- [x] Verify or update production SSM parameter example placeholders.
- [x] Run documentation checks.

## Risks and Rollback Strategy

- Risk: Documentation could imply production payments are enabled.
  Mitigation: keep all statuses blocked/pending and explicitly state that no
  live payment collection starts until the drill passes.
- Risk: Secret material could be accidentally committed.
  Mitigation: use placeholders and run targeted secret-term searches.
- Rollback: revert this documentation branch.

## Verification Plan

- Run `git diff --check`.
- Search changed docs for accidental secret-like values.
- Verify production SSM example contains PayPal parameter placeholders without
  real credentials.

## Open Questions

- Production PayPal Live app values, production Cloudflare hostname, and
  production R2 values will be provided by the operator in later execution
  steps.

## Progress

- 2026-07-05: Confirmed staging PayPal checkout, return UX, webhook, and
  idempotency are ready for staging; production PayPal Live setup remains the
  payment blocker.
- 2026-07-05: Added production PayPal Live setup runbook.
- 2026-07-05: Linked the runbook from production runtime parameters and release
  readiness checklist.
- 2026-07-05: Added production PayPal placeholder parameters to
  `deploy/production/ssm-parameters.example.json`.
- 2026-07-05: JSON validation, diff check, secret-like pattern search, and
  production deployment validator passed.

## Completion Summary

Production PayPal Live setup is now documented as an explicit launch gate. The
new runbook defines the required PayPal Live resources, production SSM
parameters, Cloudflare routing, first low-value payment drill, refund drill,
dashboard reconciliation, rollback/disablement steps, and pass criteria.

The release readiness checklist still keeps `Production PayPal live setup` as
`Blocked for production` until the runbook is executed and recorded.

## Files Changed

- `docs/operations/production-paypal-live-setup.md`
- `docs/operations/production-runtime-parameters.md`
- `docs/operations/release-readiness-checklist.md`
- `deploy/production/ssm-parameters.example.json`
- `docs/implementation-plan/2026-07-05/document-production-paypal-live-setup.md`

## Tests Run and Results

- `python -m json.tool deploy/production/ssm-parameters.example.json`: passed.
- `git diff --check`: passed.
- Secret-like pattern search across changed docs and SSM example: no matches.
- `C:\Program Files\Git\bin\bash.exe ./scripts/verify-production-deployment.sh`:
  passed.

## Manual Verification Results

- No live PayPal resources were created.
- No production SSM parameters were written.
- No live payment or refund was executed.

## Known Limitations

- Production PayPal Live setup remains blocked until the operator creates live
  resources and the first live payment/refund/reconciliation drill passes.
- Production Cloudflare, R2, database backup/restore, and observability gates
  remain separate release readiness blockers.

## Follow-up Recommendations

- Have the operator create the production PayPal Live app and webhook.
- Provision production PayPal SSM parameters using the documented names and
  types.
- Verify production Cloudflare direct API webhook routing before enabling live
  checkout.
