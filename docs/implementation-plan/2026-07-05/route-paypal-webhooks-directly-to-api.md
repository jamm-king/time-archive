# Route PayPal Webhooks Directly To API

## Objective

Reduce PayPal webhook signature verification risk by documenting and preparing
the staging/production ingress model where PayPal webhook requests bypass the
Next.js Web proxy and reach the Spring API directly through Cloudflare Tunnel.

## Scope

- Document the required Cloudflare Tunnel path route for PayPal webhooks.
- Update staging deployment guidance and PayPal integration design.
- Keep the existing Web proxy route as a fallback, but preserve request bytes
  when forwarding webhook traffic.
- Do not change PayPal API contracts, SSM parameter names, database schema, or
  payment finalization logic.

## Relevant Files

- `apps/web/src/app/api/payments/paypal/webhooks/route.ts`
- `docs/operations/cloudflare-tunnel-https.md`
- `docs/operations/staging-deployment.md`
- `docs/operations/paypal-integration-design.md`

## Key Design Decisions

- Cloudflare Tunnel remains the only public ingress.
- Browser and normal same-origin API traffic continue to target `http://web:3000`.
- PayPal webhook traffic should target `http://api:8080` directly for the exact
  webhook path.
- The Web proxy remains as a non-preferred fallback because existing deployments
  or local routing can still hit it.
- The fallback proxy forwards raw bytes instead of decoding to text and
  re-encoding the payload.

## Step-by-Step Execution Plan

1. Create a dedicated fix branch from latest `main`.
2. Add this implementation plan.
3. Change the fallback Web proxy to forward `arrayBuffer()` bytes.
4. Update Cloudflare Tunnel HTTPS operations documentation.
5. Update staging deployment runbook with Dashboard routing instructions.
6. Update PayPal integration design to require direct API ingress for webhooks.
7. Run focused Web verification if available.
8. Run diff checks and update this plan with completion details.

## Risks and Rollback Strategy

- Risk: Cloudflare Dashboard path routing is configured with lower priority than
  the Web route, so traffic still reaches Web.
  - Mitigation: document route ordering and verify with PayPal Sandbox after
    configuration.
- Risk: removing the Web proxy before Cloudflare routing is applied would break
  webhooks.
  - Mitigation: keep the fallback route for now.
- Rollback: restore the previous Tunnel route and application image. No data
  migration or schema rollback is required.

## Verification Plan

- Run Web lint/type/test command if available from existing scripts.
- Run `git diff --check`.
- After merge and deployment, configure Cloudflare Tunnel path routing:
  `/api/payments/paypal/webhooks -> http://api:8080`.
- Trigger or resend a PayPal Sandbox webhook and confirm CloudWatch no longer
  logs `paypalWebhookReason=SIGNATURE_VERIFICATION_FAILED`.

## Open Questions

- Cloudflare Dashboard configuration cannot be applied from this repository
  without Cloudflare API credentials, so the operator must apply the route.

## Progress

- [x] Created implementation plan.
- [x] Updated fallback Web proxy.
- [x] Updated operations documentation.
- [x] Ran verification.

## Completion Summary

The repository now defines PayPal webhook ingress as a direct Cloudflare Tunnel
path route to the API container while keeping the existing Web route as a
fallback. The fallback Next.js route now forwards raw request bytes with
`arrayBuffer()` instead of decoding the body as text.

## Files Changed

- `apps/web/src/app/api/payments/paypal/webhooks/route.ts`
- `docs/operations/cloudflare-tunnel-https.md`
- `docs/operations/staging-deployment.md`
- `docs/operations/paypal-integration-design.md`
- `docs/implementation-plan/2026-07-05/route-paypal-webhooks-directly-to-api.md`

## Tests Run and Results

- `npm.cmd run lint` from `apps/web`: passed.
- `npm.cmd run build` from `apps/web`: passed.
- `git diff --check`: passed.

## Manual Verification Results

- Cloudflare Dashboard routing was not changed from this repository. The
  operator must add the Published Application path route:
  `/api/payments/paypal/webhooks -> http://api:8080`.

## Known Limitations

- This change does not prove the PayPal Sandbox webhook succeeds until the
  Cloudflare Tunnel route is applied and the application is redeployed.
- The Web fallback route remains available for compatibility, but it is not the
  preferred staging or production ingress path.

## Follow-up Recommendations

- Merge and redeploy the Web image so the byte-preserving fallback is active.
- In Cloudflare Dashboard, order the exact PayPal webhook route before the
  general `staging.time-archive.com -> http://web:3000` route.
- Resend or trigger a PayPal Sandbox webhook and confirm CloudWatch does not log
  `paypalWebhookReason=SIGNATURE_VERIFICATION_FAILED`.
