# Document Cloudflare Tunnel HTTPS

## Objective

Record Cloudflare Tunnel as the selected HTTPS ingress for staging and
production, including certificate ownership, network boundaries, application
requirements, verification gates, and operational limitations.

## Scope

- Add an operations document for Cloudflare-managed public HTTPS through
  Cloudflare Tunnel.
- Align the EC2 and RDS deployment architecture with the confirmed decision.
- Update the production deployment foundation, security guidance, CI/CD
  strategy, release-readiness checklist, and README owner tasks.
- Clarify that ALB, ACM, Certbot, and public EC2 application ports are not part
  of the selected MVP architecture.

Out of scope:

- Creating a Cloudflare Tunnel, DNS record, certificate, or edge rule.
- Changing Docker Compose or application code.
- Provisioning AWS resources or changing security groups.
- Selecting final staging and production application hostnames.

## Relevant Files Or Modules

- `docs/operations/cloudflare-tunnel-https.md`
- `docs/operations/ec2-rds-deployment-architecture.md`
- `docs/operations/production-deployment-foundation.md`
- `docs/operations/release-readiness-checklist.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `docs/architecture/security-and-operations.md`
- `README.md`

## Key Design Decisions

- Cloudflare terminates browser-facing TLS with a Cloudflare-managed edge
  certificate.
- `cloudflared` establishes the only application ingress as an outbound
  encrypted Tunnel connection.
- `cloudflared` routes to `http://web:3000` on the private Docker network. The
  same-host private hop does not require a separate origin certificate for the
  MVP.
- EC2 exposes no application or administration ingress ports. SSM remains the
  administration path.
- ALB, ACM, Nginx, Certbot, and host-managed public certificates are excluded
  from the initial architecture.
- RDS connection TLS and R2 hostname TLS are separate security boundaries and
  are not replaced by the application Tunnel.
- HSTS is enabled only after staging validates HTTPS, redirects, cookie
  behavior, and rollback access.

## Step-By-Step Execution Plan

1. [Completed] Inspect existing ingress, TLS, security, release, and owner-task
   documentation.
2. [Completed] Add the Cloudflare Tunnel HTTPS operations decision.
3. [Completed] Align related architecture and operational documents.
4. [Completed] Verify terminology, links, checklist status, and repository diff.

## Risks And Rollback Strategy

- Ambiguous TLS boundaries could lead to unnecessary ALB or origin-certificate
  resources. The documentation explicitly assigns each transport boundary.
- Enabling HSTS too early can make recovery difficult. The runbook requires
  staging validation before HSTS rollout.
- Trusting forwarded client headers without blocking direct origin traffic can
  allow spoofing. The design keeps origin ports closed and requires overwrite
  semantics before enabling client-IP attribution.
- Documentation rollback is a normal Git revert; no external state is changed.

## Verification Plan

- Search for conflicting HTTPS, certificate, ALB, ACM, and ingress guidance.
- Confirm all updated Markdown files use consistent terminology.
- Run `git diff --check`.
- Review the release-readiness status against the distinction between selected
  architecture and target-environment verification.

## Open Questions

- Final staging and production application hostnames remain owner inputs.
- Exact Cloudflare edge rate-limit and WAF rules remain a later operational
  security task.

## Progress

- Confirmed that the existing deployment Compose already runs `cloudflared`
  without publishing API or Web ports.
- Identified stale release-checklist language that treated HTTPS termination as
  undecided even though Tunnel ingress was already selected.

## Completion Summary

Cloudflare Tunnel is now documented as the only staging and production
application ingress. Cloudflare owns public edge certificate issuance and
renewal, while EC2 keeps all application and administration ingress ports
closed. The same-host connector-to-Web hop remains private HTTP for the MVP.

The documentation also separates application ingress TLS from RDS and R2 TLS,
defines forwarded-header and cache requirements, delays HSTS until staging
validation, and excludes ALB, ACM, Nginx, Certbot, and host-managed public
certificates from the selected MVP architecture.

## Files Changed

- Added `docs/operations/cloudflare-tunnel-https.md`.
- Updated the EC2 and RDS deployment architecture and production deployment
  foundation.
- Updated security, CI/CD, release-readiness, and README guidance.
- Added this implementation plan.

## Tests Run And Results

- Searched repository documentation for conflicting HTTPS, TLS, certificate,
  ACM, ALB, and ingress statements.
- Confirmed the stale undecided HTTPS release gate was replaced by a selected
  architecture with target-environment verification remaining.
- Local Markdown link validation: passed.
- `git diff --check`: passed.

## Manual Verification Results

No Cloudflare, DNS, certificate, Tunnel, or AWS resource was created or
modified. Target-environment verification remains pending until staging is
provisioned.

## Known Limitations

- Staging and production application hostnames are not selected yet.
- Named Tunnels, edge certificates, redirect rules, cache rules, WAF rules, and
  rate limits are not provisioned.
- Trusted client-address propagation through Web is not implemented or
  verified.

## Follow-Up Recommendations

1. Carry this decision into the staging CloudFormation and provisioning plan:
   no ALB, ACM, public application ingress, or SSH resources.
2. Provision a staging named Tunnel and application hostname outside the
   repository after explicit approval.
3. Run the documented staging HTTPS, cookie, cache, direct-origin, webhook, and
   failure checks before production configuration.
