# Add Redis Rate Limiting

## Objective

Add a production-oriented, distributed API rate-limiting baseline for the
authentication, public read, purchase, media upload, and admin surfaces listed
in the release readiness checklist.

## Scope

- Define a core rate-limit port and provider-neutral decision model.
- Implement an atomic Redis fixed-window counter without adding dependencies.
- Add endpoint policies for sensitive and high-traffic API surfaces.
- Use authenticated user identity where available and client network identity
  for unauthenticated requests.
- Return stable `429` and Redis-unavailable API responses.
- Add configurable limits, windows, enablement, and trusted client IP header.
- Update OpenAPI, security, operations, README, and release readiness docs.
- Add focused behavior and adapter tests.

Out of scope:

- Cloudflare edge rate-limiting rules.
- Dynamic per-user plans or purchased quotas.
- Global concurrency limiting.
- CAPTCHA or bot scoring.
- Production Redis provisioning.

## Relevant Files Or Modules

- `apps/api/src/main/kotlin/com/timearchive/domain/port/RateLimitPort.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/ratelimit/RedisRateLimitAdapter.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/security/ApiRateLimitingFilter.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/RateLimitConfiguration.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/RateLimitProperties.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/SecurityConfiguration.kt`
- `apps/api/src/main/resources/application.yml`
- Focused tests under `apps/api/src/test/kotlin`
- `docs/api/openapi.yaml`
- `docs/architecture/security-and-operations.md`
- `docs/operations/release-readiness-checklist.md`
- `README.md`

## Key Design Decisions

- Use Redis because local and deployed API instances must share counters.
- Use a fixed-window counter implemented with an atomic Lua script that
  increments and sets expiration in one Redis operation.
- Keep the port in the core layer and Redis details in an outbound adapter.
- Identify authenticated purchase, upload, and admin requests by user ID.
- Identify unauthenticated auth and public read requests by client IP, but hash
  the subject with an environment-specific HMAC salt before building Redis
  keys.
- Use `request.remoteAddr` by default. Trust a forwarded client IP header only
  when `TIME_ARCHIVE_RATE_LIMIT_CLIENT_IP_HEADER` is explicitly configured and
  the deployment restricts direct origin access.
- Fail closed with `503 RATE_LIMIT_UNAVAILABLE` when Redis cannot evaluate a
  protected request. Spring Session already makes Redis a critical dependency.
- Return `429 RATE_LIMIT_EXCEEDED`, `Retry-After`, and rate-limit metadata when
  the configured limit is exceeded.
- Keep limits and windows environment-configurable with conservative defaults.

## Initial Policies

- Registration: 5 requests per 10 minutes per client IP.
- Login: 10 requests per minute per client IP.
- Public timeline and availability: 120 requests per minute per client IP.
- Purchase reservation and checkout: 30 requests per minute per authenticated
  user, with client IP fallback.
- Owned media mutation endpoints: 30 requests per minute per authenticated
  user, with client IP fallback.
- Admin media endpoints: 60 requests per minute per authenticated user, with
  client IP fallback.

## Step-By-Step Execution Plan

- [x] Confirm production safety guard is merged into latest `main`.
- [x] Create `feature/redis-rate-limiting` from latest `main`.
- [x] Inspect security filter ordering, endpoint routes, Redis dependencies, and
  current configuration.
- [x] Add this implementation plan.
- [x] Add core port and Redis adapter.
- [x] Add endpoint policy filter and security-chain ordering.
- [x] Add configurable policy properties.
- [x] Update API and operations documentation.
- [x] Add focused tests.
- [x] Run focused tests, full backend tests, build, OpenAPI validation, and
  Compose validation.
- [x] Record completion details.

## Risks And Rollback Strategy

- Risk: Incorrect identity extraction can group unrelated users or allow
  bypasses.
  - Mitigation: Prefer authenticated user ID, hash all subjects, and do not
    trust forwarded headers by default.
- Risk: Redis failure can make protected endpoints unavailable.
  - Mitigation: Return a stable `503` response and monitor Redis health; this is
    intentionally fail-closed for abuse-sensitive operations.
- Risk: Fixed windows permit short boundary bursts.
  - Mitigation: Use conservative limits now and consider token bucket or edge
    controls if production traffic requires smoother enforcement.
- Risk: Local verification scripts may exceed limits when run repeatedly.
  - Mitigation: Use per-user keys for authenticated mutation flows and
    configurable local limits if CI demonstrates contention.

Rollback:

- Remove the filter and rate-limit configuration, then remove the port, Redis
  adapter, tests, and documentation updates. No database rollback is required.

## Verification Plan

- Unit test endpoint policy matching and subject selection.
- Test allow, deny, response headers, and Redis-unavailable behavior.
- Test Redis adapter key/window calculations and atomic result mapping.
- Run the full backend test suite and build.
- Validate OpenAPI and Docker Compose.
- Run `git diff --check`.

## Open Questions

- Exact production limits should be tuned from observed traffic and abuse data.
- Cloudflare edge rate limits remain an owner-side deployment task.

## Progress Log

- 2026-06-22: Confirmed PR #57 is merged and `main` includes production fake
  payment safety guards.
- 2026-06-22: Created `feature/redis-rate-limiting`.
- 2026-06-22: Selected Redis fixed-window limiting with explicit trusted proxy
  configuration and fail-closed behavior.
- 2026-06-22: Replaced plain subject hashing with HMAC-SHA256 so Redis keys do
  not expose dictionary-reversible client IP hashes. Each environment must
  provide one shared secret salt across API instances.
- 2026-06-22: Added endpoint policy matching after session authentication,
  excluded CORS preflight requests, and added stable `429` and
  `503` responses.
- 2026-06-22: Added unit tests for policy, identity, response, and adapter
  behavior plus a Testcontainers Redis test for the atomic Lua counter and
  fixed-window rollover.
- 2026-06-22: Updated OpenAPI, README, security guidance, Compose local
  overrides, and release readiness status.

## Completion Summary

Added distributed Redis-backed fixed-window rate limiting for authentication,
public timeline and availability reads, purchase operations, owned media
mutations, and admin moderation. Authenticated operations use HMAC-protected
user IDs, while unauthenticated operations use HMAC-protected client network
identities. Redis increments and expiration are atomic through Lua.

Protected requests return `429 RATE_LIMIT_EXCEEDED` with retry
metadata when denied and fail closed with
`503 RATE_LIMIT_UNAVAILABLE` when Redis cannot evaluate a counter.
The default local Compose stack raises only the registration threshold so all
verification scripts can run sequentially.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/domain/port/RateLimitPort.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/ratelimit/RedisRateLimitAdapter.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/security/ApiRateLimitingFilter.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/RateLimitConfiguration.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/RateLimitProperties.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/SecurityConfiguration.kt`
- `apps/api/src/main/resources/application.yml`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/security/ApiRateLimitingFilterTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/outbound/ratelimit/RedisRateLimitAdapterTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/outbound/ratelimit/RedisRateLimitAdapterIntegrationTest.kt`
- `docker-compose.yml`
- `docs/api/openapi.yaml`
- `docs/architecture/security-and-operations.md`
- `docs/operations/release-readiness-checklist.md`
- `README.md`
- `docs/implementation-plan/2026-06-22/add-redis-rate-limiting.md`

## Tests Run And Results

- Focused filter and Redis adapter unit tests: passed.
- Redis Testcontainers integration test: passed.
- Full `./gradlew test --max-workers=2`: passed.
- `./gradlew build --max-workers=2`: passed.
- `./scripts/verify-openapi.sh`: passed.
- Base and R2 override `docker compose config --quiet`: passed.
- `git diff --check`: passed.

## Manual Verification Results

- No browser verification was required for the filter implementation.
- Real Redis Lua behavior and fixed-window rollover were verified through a
  Redis 8 Testcontainers integration test.

## Known Limitations

- Fixed windows permit short bursts around window boundaries.
- Application rate limiting does not replace Cloudflare edge abuse controls.
- Production client IP attribution depends on trusted proxy configuration and
  blocked direct origin access.
- Initial limits are defaults and require production tuning from observed
  traffic.

## Follow-Up Recommendations

- Configure one strong shared
  `TIME_ARCHIVE_RATE_LIMIT_KEY_SALT` secret per environment.
- Configure `TIME_ARCHIVE_RATE_LIMIT_CLIENT_IP_HEADER` only after
  direct origin access is restricted.
- Add Cloudflare edge rate-limit rules for public and authentication paths.
- Continue with file signature validation as the next code-owned release
  blocker after this branch is merged.
