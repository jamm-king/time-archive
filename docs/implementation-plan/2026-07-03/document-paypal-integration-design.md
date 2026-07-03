# Document PayPal Integration Design

## Objective

Define the production PayPal integration design before implementing real payment
provider code. The document should make checkout, capture, webhook
verification, idempotency, runtime parameters, verification, and rollback
boundaries explicit enough for the next implementation branch.

## Scope

- Add a PayPal integration operations design document.
- Update production runtime parameter documentation with the PayPal parameter
  contract.
- Update the release readiness checklist to reference the PayPal design while
  keeping production payment blockers unresolved until implementation and
  verification pass.

## Out Of Scope

- Implementing PayPal API calls.
- Creating PayPal applications, webhooks, credentials, or SSM parameters.
- Changing public API behavior.
- Running PayPal Sandbox or live payment verification.
- Modifying AWS, Cloudflare, GitHub, or PayPal external resources.

## Relevant Files Or Modules

- `docs/operations/paypal-integration-design.md`
- `docs/operations/production-runtime-parameters.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/architecture/transaction-boundaries.md`
- `apps/api/src/main/kotlin/com/timearchive/domain/port/PaymentPort.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/CreateCheckout.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/CompletePrimaryPurchase.kt`

## Key Design Decisions

- Browser redirects are never payment confirmation.
- PayPal approval, server-side capture, and verified webhook finalization are
  separate steps.
- Ownership is granted only from a verified provider event processed through the
  existing `CompletePrimaryPurchase` transaction boundary.
- The real provider implementation must not perform slow PayPal network calls
  while holding reservation row locks.
- Staging uses PayPal Sandbox. Production uses PayPal live resources. The two
  environments must never share credentials, webhook IDs, SSM paths, or R2/DB
  resources.
- PayPal raw payloads, signatures, credentials, approval URLs, and presigned
  URLs must not be logged.

## Step-By-Step Execution Plan

- [x] Inspect current payment, checkout, release, and runtime parameter
  documentation.
- [x] Inspect the current `PaymentPort`, checkout use case, and fake webhook
  controller to align the design with existing boundaries.
- [x] Add a PayPal integration design document.
- [x] Update production runtime parameters with PayPal-specific names and
  verification rules.
- [x] Update the release readiness checklist to link the design and keep real
  payment implementation blocked.
- [x] Run documentation checks.

## Risks And Rollback Strategy

- Risk: The design might accidentally imply that browser return means payment
  completion.
  - Mitigation: State repeatedly that ownership is granted only by verified
    webhook processing.
- Risk: Runtime parameter names may need adjustment during implementation.
  - Mitigation: Treat this document as the initial contract and update it in the
    implementation branch if code requires a safer naming model.
- Risk: PayPal exact API details may change.
  - Mitigation: The design describes provider boundaries and uses provider
    documentation as the source for exact endpoints during implementation.

Rollback is documentation-only: revert the changed docs if the chosen provider
or payment flow changes before implementation.

## Verification Plan

- Run `git diff --check`.
- Review changed documentation for secret leakage.
- Confirm no code, local config, or generated files were modified.

## Open Questions

- Should production launch require a fully automated PayPal Sandbox smoke flow,
  or is a documented manual Sandbox buyer approval test acceptable for the first
  paid launch gate?
- Should the first implementation persist checkout attempts in a new table, or
  extend the current reservation state with provider order metadata? The design
  prefers a separate checkout attempt because it keeps retries auditable.

## Progress Log

- 2026-07-03: Created the plan after reviewing release, runtime, transaction,
  and current payment code boundaries.
- 2026-07-03: Added the PayPal integration design, updated production runtime
  parameter documentation, and linked the design from the release checklist.

## Completion Summary

The PayPal integration design is now documented as a production payment
implementation contract. The design keeps browser redirects separate from
payment finalization, requires server-side capture after buyer approval, and
grants ownership only through verified PayPal webhook processing.

## Files Changed

- `docs/operations/paypal-integration-design.md`
- `docs/operations/production-runtime-parameters.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/implementation-plan/2026-07-03/document-paypal-integration-design.md`

## Tests Run And Results

- `git diff --check` passed.

## Manual Verification Results

- Confirmed the changes are documentation-only.
- Confirmed no real credentials, local configuration files, generated files, or
  runtime secret values were added.

## Known Limitations

- PayPal API code is not implemented yet.
- PayPal Sandbox and live resources are not configured by this change.
- Exact PayPal endpoint and SDK details must be confirmed against official
  provider documentation during implementation.

## Follow-Up Recommendations

- Implement persisted checkout attempts before adding real PayPal network calls.
- Add PayPal webhook signature verification and duplicate-event tests.
- Add staging PayPal Sandbox verification before any live payment collection.
