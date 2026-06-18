# Use Session Admin Authorization

## Objective

Remove the temporary `X-Admin-Id` header from admin moderation APIs and require
authenticated server-side session identity with an `ADMIN` user role.

## Scope

- Add a persisted user role to `UserAccount`.
- Assign `USER` by default during registration.
- Assign `ADMIN` only when the normalized email matches configured initial
  admin emails.
- Update admin moderation REST endpoints to derive the admin actor from the
  current session and reject non-admin users.
- Update local public timeline verification to register and use an admin
  session instead of `X-Admin-Id`.
- Update OpenAPI, README, and security documentation.

## Out of Scope

- Full admin user management UI.
- Multiple roles or fine-grained permissions beyond `USER` and `ADMIN`.
- CSRF protection changes.
- Production-grade admin invitation or approval flows.
- Audit log schema changes.

## Relevant Files and Modules

- `apps/api/src/main/kotlin/com/timearchive/domain/model/UserAccount.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/RegisterUser.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcUserAccountRepository.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/AdminMediaModerationController.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest/AdminMediaModerationControllerTest.kt`
- `apps/api/src/main/resources/db/migration`
- `scripts/verify-local-public-timeline-flow.sh`
- `docs/api/openapi.yaml`
- `docs/architecture/security-and-operations.md`
- `README.md`

## Key Design Decisions

- Store a simple `role` column on `users` because admin authorization must be
  tied to server-owned identity, not client-supplied headers.
- Keep only two roles for the MVP: `USER` and `ADMIN`.
- Use `time-archive.security.initial-admin-emails` as a bootstrap mechanism for
  local and early deployment environments.
- Keep authorization checks inside the REST adapter for this step so the admin
  API can return the same structured API error responses as other session
  enforced endpoints.
- Do not add dependencies.

## Execution Plan

- [x] Create a dedicated feature branch from latest `main`.
- [x] Document this implementation plan.
- [x] Add user role domain model and persistence migration.
- [x] Update registration to assign configured initial admins.
- [x] Update session authentication principal with user role.
- [x] Update admin moderation controller and tests to require session admin.
- [x] Update local verification script and Docker Compose configuration.
- [x] Update OpenAPI and security documentation.
- [x] Run focused and full verification.
- [x] Record completion details in this plan.

## Risks and Rollback Strategy

- Risk: Existing users need a role during migration.
  - Mitigation: Add the role column with `not null default 'USER'`.
- Risk: Local public timeline verification could fail if no admin account is
  bootstrapped.
  - Mitigation: Configure a deterministic local admin email in Docker Compose
    and register that email in the script before moderation.
- Risk: Initial admin email bootstrap is not a complete production admin
  lifecycle.
  - Mitigation: Document it as an MVP bootstrap path and keep full admin
    management as follow-up.
- Rollback: Revert the migration and controller/script changes before production
  data depends on the `role` column. For existing local databases, the added
  column is non-destructive and can remain unused if the feature is reverted.

## Verification Plan

- Run API tests with `./gradlew test --max-workers=2`.
- Run API build with `./gradlew build`.
- Syntax-check shell scripts.
- Run Docker Compose API stack and verify the local public timeline flow.
- Run `git diff --check`.

## Open Questions

- None.

## Progress Log

- 2026-06-18: Confirmed `main` includes session identity for purchase and owned
  media APIs.
- 2026-06-18: Created `feature/session-admin-authorization`.
- 2026-06-18: Added `UserRole`, `users.role`, initial admin email bootstrap,
  and role-aware session principal creation.
- 2026-06-18: Updated admin moderation endpoints to require an authenticated
  `ADMIN` session and removed `X-Admin-Id` from scripts and API docs.
- 2026-06-18: Verified tests, build, shell syntax, Docker Compose public
  timeline flow, and diff whitespace.

## Completion Summary

Admin moderation APIs no longer accept `X-Admin-Id`. They now require an
authenticated server-side session whose user has the persisted `ADMIN` role.
The admin actor passed into moderation use cases is derived from the session
user.

The MVP now supports initial admin bootstrap through
`TIME_ARCHIVE_INITIAL_ADMIN_EMAILS`. Registration assigns `ADMIN` only when the
normalized email matches that configured set; all other registered users remain
`USER`.

## Files Changed

- `apps/api/src/main/kotlin/com/timearchive/domain/model/UserAccount.kt`
- `apps/api/src/main/kotlin/com/timearchive/application/RegisterUser.kt`
- `apps/api/src/main/kotlin/com/timearchive/configuration/ApplicationUseCaseConfiguration.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/outbound/persistence/JdbcUserAccountRepository.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/security/SessionAuthenticationFilter.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/AdminMediaModerationController.kt`
- `apps/api/src/main/kotlin/com/timearchive/adapter/inbound/rest/ApiExceptionHandler.kt`
- `apps/api/src/main/resources/application.yml`
- `apps/api/src/main/resources/db/migration/V8__add_user_roles.sql`
- `apps/api/src/test/kotlin/com/timearchive/application/RegisterUserTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/outbound/persistence/JdbcUserAccountRepositoryIntegrationTest.kt`
- `apps/api/src/test/kotlin/com/timearchive/adapter/inbound/rest/AdminMediaModerationControllerTest.kt`
- `scripts/verify-local-public-timeline-flow.sh`
- `docker-compose.yml`
- `docs/api/openapi.yaml`
- `docs/architecture/security-and-operations.md`
- `docs/manual-verification/local-public-timeline-flow.md`
- `README.md`
- `docs/implementation-plan/2026-06-18/use-session-admin-authorization.md`

## Tests Run and Results

- `./gradlew test --max-workers=2` in `apps/api`: passed after increasing the
  command timeout for the first run.
- `./gradlew build` in `apps/api`: passed.
- `bash -n scripts/verify-local-public-timeline-flow.sh`: passed.
- `START_SECOND=42200 END_SECOND=42201 ./scripts/verify-local-public-timeline-flow.sh`: passed.
- `git diff --check`: passed.

## Manual Verification Results

Docker Compose local API stack was rebuilt and started with:

```text
docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d --build api
```

The public timeline flow completed successfully. The script registered a regular
user for purchase/upload, authenticated an admin user through the session auth
API, approved the media asset without `X-Admin-Id`, and verified that approved
media appeared in the public timeline.

The stack was stopped with:

```text
docker compose down
```

## Known Limitations

- Initial admin email bootstrap is an MVP provisioning mechanism, not a complete
  production admin lifecycle.
- CSRF protection is still disabled and should be addressed next for
  cookie-authenticated write APIs.
- Admin moderation actions still need stronger audit coverage before production.

## Follow-up Recommendations

- Add explicit admin provisioning or invitation workflow before production.
- Add CSRF protection for cookie-authenticated mutation endpoints.
- Expose user role through a deliberate frontend-facing authorization model if
  the admin UI needs it.
