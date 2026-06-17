# Fix PostgreSQL 18 Compose Volume

## Objective

Fix local Docker Compose PostgreSQL startup for the `postgres:18-alpine` image.

PostgreSQL 18 Docker images expect the mounted volume to be placed at `/var/lib/postgresql`, not directly at `/var/lib/postgresql/data`. The current compose file mounts the named volume at `/var/lib/postgresql/data`, causing PostgreSQL 18 to reject startup.

## Scope

- Update `docker-compose.yml` PostgreSQL volume mount for PostgreSQL 18.
- Avoid deleting the existing local named volume.
- Use a new named volume so local startup works without destructive cleanup.
- Document the local recovery command.

## Out of Scope

- Production database migration
- `pg_upgrade`
- Changing application datasource settings
- Adding the API service to compose
- Redis changes

## Relevant Files

- `docker-compose.yml`
- `README.md`
- `docs/implementation-plan/2026-06-17/fix-postgres18-compose-volume.md`

## Key Design Decisions

- Keep `postgres:18-alpine`.
- Mount the PostgreSQL volume at `/var/lib/postgresql`.
- Use a new named volume `postgres-18-data` instead of reusing `postgres-data`.
- Do not run `docker compose down -v` automatically because it is destructive for local database data.

## Verification Plan

- Run `docker compose up -d postgres`.
- Run `docker compose ps`.
- Confirm `time-archive-postgres` is healthy.
- Run `docker compose logs postgres --tail=50` if startup fails.

## Risks and Rollback Strategy

- Risk: Existing local data in `postgres-data` will not be used after the change.
  - Mitigation: This is safer than deleting the old volume. Developers can manually migrate or delete old local data if needed.
- Rollback: Revert the compose change and restore the previous volume mount.

## Progress

- [x] Latest `main` pulled.
- [x] Implementation branch created.
- [x] Implementation plan created.
- [x] Compose volume mount updated.
- [x] README local development note updated.
- [x] Verification commands run.
- [x] Completion details recorded.

## Completion Summary

Updated Docker Compose to use a PostgreSQL 18-compatible volume mount. PostgreSQL now starts successfully with `postgres:18-alpine`.

## Files Changed

- `docker-compose.yml`
- `README.md`
- `docs/implementation-plan/2026-06-17/fix-postgres18-compose-volume.md`

## Tests Run and Results

- `docker compose up -d postgres`: passed.
- `docker compose ps`: passed; `time-archive-postgres` is healthy.
- `docker compose logs postgres --tail=80`: confirmed PostgreSQL initialized and is ready to accept connections.

## Manual Verification Results

- Confirmed a new `time-archive_postgres-18-data` volume was created.
- Confirmed `time-archive-postgres` recreated and started.
- Confirmed PostgreSQL healthcheck reports healthy.

## Known Limitations

- Existing data in the old `postgres-data` volume is not migrated.
- This is local development infrastructure only.

## Follow-Up Recommendations

- If old local data is no longer needed, remove the unused `postgres-data` volume manually after confirming no important data is there.
- Add an `api` service to Docker Compose later for full local stack verification.
