# Add Server Session Auth

## Objective

Introduce the first server-side identity foundation for Time Archive using
Spring Security sessions backed by Redis.

This step should create the minimal authenticated user surface needed before
purchase and upload flows stop accepting development-stage client-provided
identity.

## Scope

- Add user persistence for local MVP accounts.
- Add session-backed login, logout, and current-user APIs.
- Store sessions server-side through Spring Session Redis.
- Configure Docker Compose API to use the existing Redis service.
- Keep existing public and development-stage purchase/media APIs compatible for
  this step.
- Document that replacing `buyerId` and `X-User-Id` is a follow-up.

## Out of Scope

- OAuth or social login.
- Email verification.
- Password reset.
- MFA.
- Replacing `buyerId` request fields.
- Replacing `X-User-Id` headers in owned media APIs.
- Admin role authorization.
- Frontend login UI.

## Relevant Files or Modules

- `apps/api/build.gradle.kts`
- `apps/api/src/main/resources/application.yml`
- `apps/api/src/main/resources/db/migration`
- `apps/api/src/main/kotlin/com/timearchive/configuration/SecurityConfiguration.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest`
- `apps/api/src/main/kotlin/com/timearchive/domain/model`
- `apps/api/src/main/kotlin/com/timearchive/domain/port`
- `apps/api/src/main/kotlin/com/timearchive/application`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/persistence`
- `docker-compose.yml`

## Key Design Decisions

- Use server-side sessions rather than JWT because the MVP is a browser-first
  web app and needs straightforward logout and session invalidation.
- Use Redis for session storage because Redis already exists in the local
  Docker Compose stack and avoids tying session lifecycle to primary ownership
  data.
- Keep account authentication simple with email and password for local MVP
  development. OAuth can be added later behind the same server-side session
  boundary.
- Store only password hashes, never plaintext passwords.
- Keep user domain and repository ports independent from Spring Security.
- Limit Spring-specific authentication code to configuration and inbound adapter
  glue.

## Dependency Justification

- `org.springframework.session:spring-session-data-redis`
  - Needed to store Spring Security sessions in Redis instead of local JVM
    memory.
  - Alternative: JDBC session storage. Rejected for this step because Redis is
    already in the stack and keeps volatile session state outside primary
    business tables.
  - License: Apache License 2.0 through the Spring project, compatible with MIT
    project distribution.
  - Maintenance: actively maintained by Spring.
- `org.springframework.boot:spring-boot-starter-data-redis`
  - Needed for Spring Boot Redis connection auto-configuration used by Spring
    Session.
  - Alternative: manual Redis client configuration. Rejected because Spring Boot
    provides the standard integration and the project already uses Spring Boot
    starters.
  - License: Apache License 2.0, compatible with MIT project distribution.
  - Maintenance: actively maintained by Spring.

## Step-by-Step Execution Plan

- [x] Update `main` and create a dedicated feature branch.
- [x] Create this implementation plan.
- [x] Add Redis session dependencies and application configuration.
- [x] Add `users` table migration.
- [x] Add user domain model and repository port.
- [x] Add JDBC user repository.
- [x] Add register/login/logout/current-user REST API.
- [x] Update Spring Security to use sessions and protect `/api/me`.
- [x] Add focused tests for auth controller and user repository.
- [x] Update docs/OpenAPI for the new auth endpoints.
- [x] Run backend tests and build.
- [x] Update this plan with completion details.
- [ ] Commit and push the branch.

## Risks and Rollback Strategy

- Risk: Adding session security could accidentally lock existing CI flow APIs.
  - Mitigation: Keep existing development-stage APIs permitted in this step and
    add only the auth endpoints plus `/api/me` authentication requirement.
- Risk: Redis session dependency could make local API startup depend on Redis.
  - Mitigation: Docker Compose already runs Redis. Local non-Docker runs must
    start Redis or override session storage in a future profile.
- Risk: Password-based MVP auth is not a final product authentication strategy.
  - Mitigation: Keep the session boundary stable so OAuth can later replace only
    the credential acquisition path.
- Rollback: Revert dependency, configuration, migration, auth endpoints, and
  security configuration changes. Existing ownership and purchase data are not
  modified.

## Verification Plan

- `.\gradlew.bat test --max-workers=2` under `apps/api`
- `.\gradlew.bat build` under `apps/api`
- `docker compose config`
- Local HTTP check for register/login/me/logout if Docker is available
- `git diff --check`

## Open Questions

- None for this scoped foundation. The follow-up identity migration for
  purchase and upload APIs will require separate API compatibility decisions.

## Progress Log

- 2026-06-18: Confirmed `main` was up to date.
- 2026-06-18: Created `codex/add-server-session-auth`.
- 2026-06-18: Created implementation plan.
- 2026-06-18: Added Spring Session Redis and Spring Data Redis dependencies.
- 2026-06-18: Added Redis connection settings and Docker Compose API
  dependency on Redis health.
- 2026-06-18: Added `users` table migration.
- 2026-06-18: Added user domain model, repository port, password hashing port,
  JDBC user repository, and BCrypt hashing adapter.
- 2026-06-18: Added register, login, logout, and current-user APIs.
- 2026-06-18: Updated Spring Security from stateless mode to server-side
  sessions and protected `/api/me`.
- 2026-06-18: Added focused auth use case, controller, and repository tests.
- 2026-06-18: Updated OpenAPI, README, and security architecture docs.
- 2026-06-18: Verified backend tests, backend build, Docker Compose config, and
  local HTTP session auth flow.

## Completion Summary

Added the first server-side session authentication foundation.

The API now supports:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/me`

Sessions are stored server-side through Spring Session Redis. The client only
receives the session cookie. User passwords are stored as BCrypt hashes.

Existing purchase and owned media APIs remain development-stage compatible for
this step. They still accept `buyerId` and `X-User-Id`; migrating those
endpoints to authenticated server-side identity is the next auth follow-up.

## Files Changed

- `README.md`
- `docker-compose.yml`
- `docs/api/openapi.yaml`
- `docs/architecture/security-and-operations.md`
- `docs/architecture/time-archive-architecture.md`
- `docs/implementation-plan/2026-06-18/add-server-session-auth.md`
- `apps/api/build.gradle.kts`
- `apps/api/src/main/resources/application.yml`
- `apps/api/src/main/resources/db/migration/V7__create_users.sql`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/AuthController.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/AuthDtos.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/CurrentUserSession.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/security/SessionAuthenticationFilter.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcUserAccountRepository.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/security/BCryptPasswordHasherAdapter.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/AuthenticateUser.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/GetCurrentUser.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/RegisterUser.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/SecurityConfiguration.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/model/UserAccount.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/port/PasswordHasherPort.kt`
- `apps/api/src/main/kotlin/com/timearchive/domain/port/UserAccountRepository.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest/AuthControllerTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/outbound/persistence/JdbcUserAccountRepositoryIntegrationTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/AuthenticateUserTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/application/RegisterUserTest.kt`

## Tests Run and Results

- `.\gradlew.bat test --max-workers=2` under `apps/api` - passed
- `.\gradlew.bat build` under `apps/api` - passed
- `docker compose config` - passed
- `docker compose up -d --build api` - passed
- Local HTTP auth flow - passed:
  - register returned HTTP 201
  - current user returned HTTP 200
  - logout returned HTTP 204
  - current user after logout returned HTTP 401
- `docker compose down` - passed
- `git diff --check` - passed

## Manual Verification Results

Verified that a locally registered user receives a session cookie, that
`GET /api/me` returns the current user while the session is active, and that
logout invalidates the session.

## Known Limitations

- Purchase APIs still accept request-body `buyerId`.
- Owned media APIs still accept `X-User-Id`.
- There is no frontend login UI yet.
- There is no email verification, password reset, OAuth, MFA, admin role model,
  or CSRF protection for cookie-authenticated browser mutations yet.

## Follow-up Recommendations

- Migrate purchase reservation creation from `buyerId` to session-derived
  current user identity.
- Migrate owned media APIs from `X-User-Id` to session-derived current user
  identity.
- Add frontend login/register UI before purchase/upload UI.
- Add CSRF protection before exposing cookie-authenticated mutations beyond
  local MVP verification.
