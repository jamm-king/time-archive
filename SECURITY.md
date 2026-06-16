# Security Policy

## Supported Versions

Time Archive is currently in the planning phase and has no production release.

Security expectations will be updated when the first runnable version is published.

## Reporting a Vulnerability

Please do not report security vulnerabilities through public GitHub issues.

Use GitHub Security Advisories for this repository when available. If a dedicated security contact is added later, this document will be updated with the preferred reporting channel.

## Security Priorities

The project treats the following areas as high risk:

- Payment webhook verification
- Ownership and transaction integrity
- User-uploaded media processing
- Admin authorization
- Secret management
- Audit logging
- Rate limiting

Security-sensitive changes should include tests and clear verification steps.
