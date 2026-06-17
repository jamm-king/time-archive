# Add Docker Compose API Service

## Objective

Add the backend API application to Docker Compose so the local backend stack can run through one command for integration verification.

The current Compose file starts PostgreSQL and Redis only. Developers can run the API with `bootRun`, but the project also needs a reproducible containerized local stack for manual API verification.

## Scope

- Add an `api` service to `docker-compose.yml`.
- Build the API service from the existing `Dockerfile`.
- Configure the API container to connect to PostgreSQL through the Compose network.
- Wait for PostgreSQL health before starting the API container.
- Expose API port `8080`.
- Update README local development instructions.
- Verify the API health endpoint through Docker Compose.

## Out of Scope

- Production deployment configuration
- Kubernetes, ECS, or cloud infrastructure
- Docker Compose profiles
- Frontend service
- Object storage service
- Real payment provider configuration

## Relevant Files

- `docker-compose.yml`
- `README.md`
- `docs/implementation-plan/2026-06-17/add-docker-compose-api-service.md`

## Key Design Decisions

- Keep the existing `Dockerfile`.
- Add `api` service without changing the local JVM `bootRun` workflow.
- Set `TIME_ARCHIVE_DATABASE_URL` to `jdbc:postgresql://postgres:5432/time_archive` inside Compose.
- Use `depends_on.postgres.condition: service_healthy`.
- Keep Redis in Compose even though the current application does not use it yet.

## Verification Plan

- Run `docker compose up -d postgres redis`.
- Run `docker compose up -d --build api`.
- Run `docker compose ps`.
- Verify `time-archive-api` is running.
- Call `http://localhost:8080/actuator/health`.
- Call `http://localhost:8080/api/archive/availability?startSecond=0&endSecond=10`.

## Risks and Rollback Strategy

- Risk: API image build can be slow because Gradle runs inside Docker.
  - Mitigation: Keep local `bootRun` workflow documented for day-to-day development.
- Risk: API container can fail if PostgreSQL is unhealthy.
  - Mitigation: Use Compose health dependency and keep healthcheck visible.
- Rollback: Remove the `api` service from `docker-compose.yml` and restore README instructions.

## Progress

- [x] Latest `main` pulled.
- [x] Implementation branch created.
- [x] Implementation plan created.
- [x] Docker Compose `api` service added.
- [x] README local development instructions updated.
- [x] Docker Compose verification commands run.
- [x] Gradle verification commands run.
- [x] Completion details recorded.

## Implementation Notes

- Added an `api` service to `docker-compose.yml`.
- The API service builds from the existing `Dockerfile`.
- The API service uses `jdbc:postgresql://postgres:5432/time_archive` inside the Compose network.
- The API service waits for the PostgreSQL healthcheck before starting.
- README now separates local JVM development from full Docker stack verification.

## Verification Results

- `docker compose up -d postgres redis`: passed.
- `docker compose up -d --build api`: passed.
- `docker compose ps`: passed; `api`, `postgres`, and `redis` are running, and PostgreSQL/Redis are healthy.
- `GET http://localhost:8080/actuator/health`: passed with `UP`.
- `GET http://localhost:8080/api/archive/availability?startSecond=0&endSecond=10`: passed with `available = true`.
- `.\gradlew.bat test`: passed.
- `.\gradlew.bat build`: passed.

## Completion Summary

The Docker Compose API service was added and verified. The full local backend stack can now be started with Docker Compose, while the local JVM `bootRun` workflow remains available for day-to-day development.

## Files Changed

- `docker-compose.yml`
- `README.md`
- `docs/implementation-plan/2026-06-17/add-docker-compose-api-service.md`

## Known Limitations

- Docker image builds can still be slower than local `bootRun`.
- No frontend, object storage, or fake external payment provider service is included in Compose yet.
- The API service uses the same development-stage security posture as the application.

## Follow-Up Recommendations

- Add a local purchase flow verification script next.
- Consider Docker Compose profiles later if the local stack grows.
- Add frontend and storage services only when those parts exist.
