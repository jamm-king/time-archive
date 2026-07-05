# Document PayPal Idempotency Staging Drill

## Objective

Create a safe staging runbook for PayPal idempotency verification so the project
owner can replay a PayPal Sandbox webhook and record whether Time Archive
handles the duplicate event without duplicate ownership side effects.

## Scope

- Add a PayPal staging idempotency drill runbook.
- Document operator steps, CloudWatch checks, database checks, pass/fail
  criteria, rollback/escalation, and a result record template.
- Update the release readiness checklist to reference the runbook while keeping
  the item pending until the drill is actually executed.

Out of scope:

- Executing the PayPal Dashboard resend action.
- Running live AWS or database queries during this documentation task.
- Production PayPal live setup.
- New application code or schema changes.

## Relevant Files or Modules

- `docs/operations/paypal-staging-idempotency-drill.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-05/document-paypal-idempotency-staging-drill.md`

## Key Design Decisions

- The runbook separates what the project owner must do in PayPal Dashboard from
  what can be verified through AWS/CloudWatch/database checks.
- Duplicate webhook replay is the primary staging drill because it directly
  exercises the provider resend path against deployed code.
- Capture failure retry remains primarily covered by backend automated tests
  because creating a real PayPal Sandbox capture failure on demand is not
  reliable or desirable.
- The checklist will remain `Needs staging verification` until a real resend
  drill result is recorded.

## Step-by-Step Execution Plan

- [x] Inspect existing PayPal integration and CloudWatch operation docs.
- [x] Create this implementation plan.
- [x] Add the PayPal staging idempotency drill runbook.
- [x] Update the release readiness checklist reference.
- [x] Run documentation checks.

## Risks and Rollback Strategy

- Risk: The runbook could encourage unsafe database mutation.
  Mitigation: use read-only SQL checks and clearly prohibit manual updates.
- Risk: The checklist could overstate readiness.
  Mitigation: keep the payment idempotency row pending until the drill result is
  actually recorded.
- Rollback: revert this documentation branch.

## Verification Plan

- Run `git diff --check`.
- Search for stale wording that implies the drill has already passed.

## Open Questions

- The exact PayPal Dashboard event ID will be provided by the operator during
  the actual drill.

## Progress

- 2026-07-05: Confirmed the checklist still requires staging verification for
  PayPal idempotency after backend automated tests were added.
- 2026-07-05: Added the PayPal staging idempotency drill runbook with
  preconditions, safety rules, PayPal Dashboard resend steps, CloudWatch checks,
  read-only SQL checks, pass criteria, failure handling, and result template.
- 2026-07-05: Updated the release readiness checklist to point payment
  idempotency verification at the new runbook.
- 2026-07-05: `git diff --check` passed. Readiness overstatement search found
  only the runbook result template placeholder `Outcome: PASS | FAIL`.

## Completion Summary

Added a staging PayPal idempotency drill runbook and connected it from the
release readiness checklist. The checklist still keeps `Payment idempotency` as
`Needs staging verification` until the PayPal Sandbox resend drill is actually
executed and recorded.

## Files Changed

- `docs/operations/paypal-staging-idempotency-drill.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-05/document-paypal-idempotency-staging-drill.md`

## Tests Run and Results

- `git diff --check`: passed.
- Readiness overstatement search for `Payment idempotency | Ready`,
  `idempotency.*passed`, and `drill passed`: no matches.
- The only `Outcome: PASS` match is the runbook's result template placeholder.

## Manual Verification Results

- Not executed in this task. The PayPal Dashboard resend action must be
  performed by the operator in the PayPal Developer Dashboard.

## Known Limitations

- The drill is documented but not yet executed.
- Capture failure retry remains primarily covered by backend automated tests
  because forcing a real PayPal Sandbox capture failure is not reliable.

## Follow-up Recommendations

- Execute the PayPal Sandbox webhook resend drill.
- Record the event ID, request ID, CloudWatch result, and database row counts in
  a follow-up documentation update.
