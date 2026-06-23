# Cloudflare Tunnel HTTPS

## Decision

Staging and production use Cloudflare Tunnel as the only public application
ingress. Cloudflare terminates browser-facing HTTPS with a Cloudflare-managed
edge certificate. Time Archive does not terminate public TLS on EC2 and does
not use an Application Load Balancer, AWS Certificate Manager certificate,
Nginx, Certbot, or a host-managed public certificate for the MVP.

This decision applies to the application hostname. RDS connection TLS and R2
hostname TLS remain separate security boundaries.

## Traffic And Trust Boundaries

```text
Browser
  -> HTTPS and Cloudflare-managed edge certificate
Cloudflare edge
  -> encrypted outbound Cloudflare Tunnel connection
cloudflared container
  -> http://web:3000 on the private Docker network
Next.js Web
  -> http://api:8080 on the private Docker network
Spring Boot API
```

The private HTTP hop from `cloudflared` to Web is accepted because both
containers run on the same EC2 host and communicate only through the private
Compose network. If the connector and origin move to different hosts or an
untrusted network, that hop must use authenticated TLS.

No application container publishes a host port. The EC2 security group has no
inbound rule for ports `80`, `443`, `3000`, or `8080`, and SSH remains disabled.
Operators use SSM Session Manager. The EC2 host still requires outbound network
access for Cloudflare Tunnel, ECR, SSM, CloudWatch, R2, and software updates;
Tunnel does not replace outbound connectivity.

## Certificate Responsibilities

Cloudflare is responsible for issuing and renewing the public edge certificate
for each configured application hostname. Before traffic is enabled:

- The domain must be active in the intended Cloudflare account.
- Staging and production must use distinct hostnames and Tunnel credentials.
- The hostname must route to the environment-specific named Tunnel.
- The Tunnel public hostname must target `http://web:3000`.
- The edge certificate must be active and cover the exact hostname.
- HTTP requests must redirect to HTTPS at Cloudflare.

Do not provision an ACM certificate or expose EC2 port `443` as a fallback.
Emergency origin access requires a separate, time-bounded, explicitly approved
procedure rather than a permanently open bypass around Cloudflare controls.

The R2 media hostname is not the application ingress hostname. Its certificate,
CORS policy, bucket access, and presigned URL behavior are managed separately.

## Edge And Application Controls

Required Cloudflare controls:

- Redirect HTTP to HTTPS.
- Use a minimum TLS version approved for the release, initially TLS 1.2.
- Bypass shared caching for `/api/*`, authentication responses, and responses
  containing presigned URLs.
- Add environment-appropriate WAF and rate-limit rules before public launch.
- Enable HSTS only after staging verifies HTTPS, redirects, cookies, and the
  recovery procedure. Increase HSTS lifetime gradually.

Required application controls:

- Session cookies remain `Secure`, `HttpOnly`, and use the intended `SameSite`
  policy.
- Spring honors the trusted forwarded protocol so HTTPS requests do not cause
  insecure redirects or incorrect cookie behavior.
- Web proxies API requests only to the private API service.
- Direct origin access remains impossible before any Cloudflare client-address
  header is trusted.
- The Web proxy overwrites, rather than appends, the trusted client-address
  value before `TIME_ARCHIVE_RATE_LIMIT_CLIENT_IP_HEADER` is enabled.
- PayPal webhook signatures are verified independently of Tunnel ingress.

## Provisioning Inputs

The project owner must provide:

- Staging and production application hostnames.
- The Cloudflare account and zone where those hostnames are managed.
- Separate staging and production named Tunnels.
- Environment-specific Tunnel tokens stored as SSM `SecureString` parameters.
- Approved HTTP redirect, TLS, WAF, rate-limit, and cache rules.

Tunnel tokens are credentials. They must not appear in CloudFormation
parameters, GitHub Actions logs, Docker image metadata, repository files, or
application logs.

## Staging Verification

Before production configuration:

1. Confirm HTTP redirects to the expected HTTPS hostname.
2. Inspect the browser-visible certificate and hostname coverage.
3. Confirm EC2 has no public application or SSH ingress rules.
4. Confirm the public hostname reaches Web and `/api/timeline` through the
   private proxy path.
5. Verify registration, login, logout, CSRF-protected mutations, and secure
   cookie attributes.
6. Verify upload, admin preview, and public playback presigned URLs are not
   cached by shared proxies.
7. Verify direct requests cannot bypass Cloudflare to reach Web or API.
8. Verify the configured client address cannot be spoofed before enabling it
   for rate limiting.
9. Verify a PayPal Sandbox webhook reaches the callback and still requires a
   valid provider signature.
10. Stop `cloudflared` and confirm monitoring detects public unavailability.

## Failure And Rollback

A Tunnel, Cloudflare edge, DNS, or single EC2 failure can make the application
unavailable. The first MVP accepts this single-host availability tradeoff but
must alert on `cloudflared` restarts and failed public health checks.

Rollback should restore the previous Tunnel route or application image while
keeping origin ports closed. Do not disable HTTPS, weaken certificate checks,
or expose a public origin during routine rollback. Keep HSTS disabled until the
staging rollback path has been exercised successfully.

## References

- [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/)
- [Cloudflare Universal SSL](https://developers.cloudflare.com/ssl/edge-certificates/universal-ssl/)
- [Always Use HTTPS](https://developers.cloudflare.com/ssl/edge-certificates/additional-options/always-use-https/)
- [HTTP Strict Transport Security](https://developers.cloudflare.com/ssl/edge-certificates/additional-options/http-strict-transport-security/)
