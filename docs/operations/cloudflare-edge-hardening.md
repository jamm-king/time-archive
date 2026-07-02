# Cloudflare Edge Hardening

## Purpose

This runbook defines the minimum Cloudflare edge hardening baseline for the
Time Archive MVP. It complements application-level Redis rate limiting and the
Cloudflare Tunnel ingress model.

Cloudflare dashboard configuration is operator-controlled and is not created by
this repository. This document defines the expected policy and verification
steps.

## Ingress Boundary

Cloudflare Tunnel is the only public application ingress.

Expected public traffic path:

```text
Browser -> Cloudflare edge -> Cloudflare Tunnel -> web:3000 -> api:8080
```

The EC2 host must not publish Web, API, Redis, or database ports. Public DNS
must route only through Cloudflare-managed hostnames. Direct origin access must
remain unavailable for normal browser traffic.

## Trusted Client IP

Use `CF-Connecting-IP` as the trusted client IP header for application rate
limiting after Cloudflare Tunnel is confirmed as the only public ingress.

Required runtime value:

```text
TIME_ARCHIVE_RATE_LIMIT_CLIENT_IP_HEADER=CF-Connecting-IP
```

Cloudflare documents that `CF-Connecting-IP` provides the client IP address on
traffic from Cloudflare edge to the origin. The application should not use
`X-Forwarded-For` for rate-limit identity because that header can contain a
proxy chain and is easier to misinterpret.

The Web proxy forwards only the reviewed Cloudflare request headers needed by
the API:

- `CF-Connecting-IP`
- `CF-Ray`
- `CF-Visitor`
- `CF-IPCountry`

Do not configure this trusted header if the application can be reached through a
public path that bypasses Cloudflare.

## Cache Policy

Configure Cloudflare Cache Rules so application responses bypass shared cache.

Minimum rule:

- Match hostname: staging or production application hostname.
- Match path: all application paths, or at minimum `/api/*`.
- Action: `Bypass cache`.

Reasons:

- API responses can contain session-derived state.
- Public timeline responses can contain short-lived presigned playback URLs.
- Auth, admin, purchase, upload, and CSRF responses must never be stored in a
  shared edge cache.

R2 object caching and media optimization are separate future tasks. Do not cache
presigned URL responses.

## WAF Baseline

Enable Cloudflare WAF managed protections for the staging and production
application hostnames.

Minimum MVP policy:

- Enable managed WAF protections available on the account plan.
- Block or challenge obvious automated abuse and known malicious traffic.
- Keep bypass rules narrow and documented.
- Do not disable WAF for `/api/*`.
- Review Cloudflare security events after smoke tests and before a release
  candidate.

## Edge Rate Limiting

Cloudflare Rate limiting rules should reduce automated abuse before traffic
reaches the application. They do not replace Redis application rate limiting.

Free plan accounts may have only one Rate limiting rule and may only offer a
`Block` action with a short fixed period. In that case, prioritize authentication
abuse before broader API throttling.

Recommended starting policy:

| Surface | Match | Suggested action |
| --- | --- | --- |
| Auth registration | `POST /api/auth/register` | Challenge or block high bursts per IP. |
| Auth login | `POST /api/auth/login` | Challenge or block repeated failures/high bursts per IP. |
| Public reads | `GET /api/timeline*`, `GET /api/archive/availability*` | Throttle high request rates per IP. |
| Purchase mutations | `POST /api/purchase/*` | Strict burst limit per IP. |
| Media mutations | `POST /api/owned-ranges/*/media*` | Strict burst limit per IP. |
| Admin routes | `/api/admin/*` | Low threshold and alert/review on activity. |

Tune thresholds after staging smoke tests and real traffic observations. Keep
application Redis limits active even when Cloudflare limits are configured.

## Staging Free Plan Configuration

The current staging hostname uses a Free plan-compatible minimum policy:

- Cache Rule: `http.host eq "staging.time-archive.com"` with `Bypass cache`.
- Rate limiting rule: `POST /api/auth/login` and `POST /api/auth/register` per
  IP, 10-second period, `Block` action.
- Custom rule: block suspicious non-application paths such as `.php`, `/wp-`,
  `/.env`, and `phpmyadmin`.
- Custom rule: block `/api/internal/payments/fake/*` at the edge.

The suspicious path expression uses `contains` as an operator, not as a
function:

```text
(http.host eq "staging.time-archive.com"
 and (
   ends_with(lower(http.request.uri.path), ".php")
   or lower(http.request.uri.path) contains "/wp-"
   or lower(http.request.uri.path) contains "/.env"
   or lower(http.request.uri.path) contains "phpmyadmin"
 ))
```

Keep unused Custom Rules available for future production or incident-specific
controls. Avoid admin geo-blocking while GitHub Actions smoke workflows run from
non-Korean runner IP ranges.

## Public Health Verification

After Cloudflare edge changes, run:

- `Smoke staging public`
- `Smoke staging request ID`
- `Smoke staging auth`
- `Smoke staging security headers`
- `Smoke staging media preview`
- `Smoke staging presigned upload CORS`

Also confirm from the Cloudflare dashboard:

- The application hostname is routed to the intended Tunnel.
- Cache Rules show bypass behavior for application/API responses.
- WAF events do not show smoke traffic being blocked unexpectedly.
- Rate limiting rules are present and scoped to the intended paths.

## Release Gate

The release readiness checklist may mark Cloudflare edge controls as `Ready`
only after:

- direct origin access remains unavailable;
- `TIME_ARCHIVE_RATE_LIMIT_CLIENT_IP_HEADER=CF-Connecting-IP` is present in the
  deployed runtime;
- Cloudflare cache bypass is configured for application/API responses;
- WAF and edge rate limiting are configured for the public hostname;
- the public smoke workflows pass after the edge changes.

For staging, the above release gate passed after applying the Free plan policy
and rerunning the staging smoke workflows.

## References

- [Cloudflare HTTP headers](https://developers.cloudflare.com/fundamentals/reference/http-headers/)
- [Cloudflare Cache Rules settings](https://developers.cloudflare.com/cache/how-to/cache-rules/settings/)
- [Cloudflare rate limiting rules](https://developers.cloudflare.com/waf/rate-limiting-rules/)
