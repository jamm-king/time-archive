# Fix PayPal Webhook Web Proxy

## Objective

Fix PayPal Sandbox webhook delivery staying pending because
`https://staging.time-archive.com/api/payments/paypal/webhooks` is handled by
the Web container and currently returns a Next.js 404 instead of proxying to the
API container.

## Scope

- Add a Next.js API route for `/api/payments/paypal/webhooks`.
- Preserve the raw JSON body and required PayPal verification headers.
- Forward the request to the API container endpoint.
- Verify the public staging endpoint no longer returns the Web 404 after
  deployment.

## Relevant Files Or Modules

- `apps/web/src/app/api/payments/paypal/webhooks/route.ts`
- `apps/web/src/lib/backend-proxy.ts`

## Key Design Decisions

- Use a dedicated route instead of the generic JSON proxy because PayPal
  webhook verification depends on provider headers.
- Do not add cookies or CSRF headers because this is a provider-to-server
  webhook.
- Preserve `X-Request-Id` and Cloudflare forwarding headers for correlation and
  rate-limit behavior.

## Step-By-Step Execution Plan

- [x] Confirm the public webhook URL returns Next.js 404.
- [x] Add Web proxy route for PayPal webhooks.
- [x] Run Web lint/build.
- [ ] Run a public endpoint probe after deployment.

## Risks And Rollback Strategy

- Risk: Missing provider headers would still fail signature verification.
  - Mitigation: Explicitly forward all PayPal signature headers.
- Risk: Proxy route could mask API errors.
  - Mitigation: Return upstream status and response body directly.

Rollback is a normal code rollback. No database changes are involved.

## Verification Plan

- Run `npm.cmd run lint`.
- Run `npm.cmd run build`.
- Run `git diff --check`.
- After deployment, POST a probe to the public webhook URL and expect an API
  error response, not a Next.js 404 page.

## Open Questions

None.

## Progress Log

- 2026-07-04: Public staging webhook URL returned a Next.js 404, confirming the
  Web proxy route is missing.
- 2026-07-04: Added the Web proxy route and verified it appears in the Next.js
  production build route list.

## Completion Summary

Added a Next.js API route that forwards PayPal webhook requests from the public
staging hostname to the API container. The route preserves the raw request body,
PayPal signature headers, request correlation header, and Cloudflare forwarding
headers.

## Files Changed

- `apps/web/src/app/api/payments/paypal/webhooks/route.ts`
- `docs/implementation-plan/2026-07-04/fix-paypal-webhook-web-proxy.md`

## Tests Run And Results

- `npm.cmd run lint` from `apps/web`: passed.
- `npm.cmd run build` from `apps/web`: passed.
- `git diff --check`: passed.

## Manual Verification Results

Before the fix, a public POST probe to
`https://staging.time-archive.com/api/payments/paypal/webhooks` returned a
Next.js 404 page. Post-deployment verification is still required.

## Known Limitations

The fix is not deployed until this branch is merged, staging images are
published, and staging is redeployed.

## Follow-Up Recommendations

- Merge this branch.
- Publish staging images and deploy staging.
- Re-run the public POST probe and expect an API error response instead of a
  Next.js 404.
- Re-run a PayPal Sandbox purchase so PayPal sends a fresh webhook delivery.
