# Add Web Docker Compose Service

## Objective

Add the frontend web app to the root Docker Compose stack so the local MVP can
run API, database, object storage, and web UI together.

## Scope

- Add a production-style Dockerfile for `apps/web`.
- Add a web service to the root `docker-compose.yml`.
- Configure the web container to proxy timeline API requests to the API service
  inside the Docker Compose network.
- Update local development and operations documentation.
- Verify Docker image build, Docker Compose startup, the web root page, and the
  web-to-api timeline proxy.

## Out of Scope

- Hot reload Docker development workflow.
- Frontend deployment configuration.
- Cloudflare Pages or Vercel setup.
- Frontend E2E browser automation.
- Backend behavior changes.

## Relevant Files or Modules

- `apps/web/Dockerfile`
- `apps/web/.dockerignore`
- `docker-compose.yml`
- `README.md`
- `apps/web/README.md`
- `docs/operations/ci-cd-and-testing-strategy.md`
- `docs/implementation-plan/2026-06-18/add-web-docker-compose-service.md`

## Key Design Decisions

- The root Docker Compose file remains the local full-stack orchestration entry
  point.
- The web Docker image uses a production-style `next build` and `next start`
  flow.
- The web service sets `TIME_ARCHIVE_API_BASE_URL=http://api:8080` so the
  Next.js same-origin route handler can call the backend through the Compose
  network.
- The web service exposes port `3000`.
- The web service depends on the API service starting, but readiness is still
  verified through smoke checks instead of complex Compose health wiring.

## Step-by-Step Execution Plan

- [x] Update `main` and create a dedicated feature branch.
- [x] Create this implementation plan.
- [x] Add `apps/web/Dockerfile`.
- [x] Add `apps/web/.dockerignore`.
- [x] Add `web` service to `docker-compose.yml`.
- [x] Update README and operations docs.
- [x] Run web lint/build.
- [x] Run web Docker image build.
- [x] Run Docker Compose config and full-stack smoke checks.
- [x] Commit and push the branch.

## Completion Summary

Added the frontend web app to the root Docker Compose stack.

The local full-stack command now builds and runs:

- PostgreSQL
- Redis
- MinIO
- MinIO bucket initialization
- Spring Boot API
- Next.js web app

The web container listens on `http://localhost:3000` and uses
`TIME_ARCHIVE_API_BASE_URL=http://api:8080` so its Next.js `/api/timeline`
route handler can call the backend API inside the Compose network.

## Files Changed

- `.github/workflows/ci.yml`
- `README.md`
- `apps/web/.dockerignore`
- `apps/web/Dockerfile`
- `apps/web/README.md`
- `docker-compose.yml`
- `docs/implementation-plan/2026-06-18/add-web-docker-compose-service.md`
- `docs/operations/ci-cd-and-testing-strategy.md`

## Tests Run and Results

- `npm.cmd run lint` under `apps/web` - passed
- `npm.cmd run build` under `apps/web` - passed
- `docker compose config` - passed
- `docker build -t time-archive-web:local apps/web` - passed
- `docker compose up -d --build` - passed
- `GET http://localhost:3000` - passed with HTTP 200
- `GET http://localhost:3000/api/timeline?from=0&to=1` - passed with
  `{"from":0,"to":1,"segments":[]}`
- `GET http://localhost:8080/actuator/health` - passed with API status `UP`
- `docker compose down` - passed
- `git diff --check` - passed
- `docker compose config` after CI readiness fix - passed
- `docker compose up -d --build` after CI readiness fix - passed
- Retried web root and timeline proxy smoke checks after CI readiness fix -
  passed with `{"from":0,"to":1,"segments":[]}`
- `docker compose down` after CI readiness fix - passed

## Manual Verification Results

Confirmed that the Dockerized web app serves the public player shell and that
the web container can proxy timeline reads to the API container through the
Compose network.

## Known Limitations

- The Compose web service is production-style and does not provide hot reload.
- Existing backend flow CI jobs intentionally start only the `api` service to
  avoid rebuilding web unnecessarily.
- The web image copies full production dependencies instead of using Next.js
  standalone output. This can be optimized later if image size becomes a
  concern.

## Follow-up Recommendations

- Add a future `docker-compose.dev.yml` for bind-mounted web hot reload if
  Docker-based frontend development becomes useful.
- Consider Next.js standalone output if deployment image size matters.

## Risks and Rollback Strategy

- Risk: Next.js runtime needs different environment variable timing than local
  dev.
  - Mitigation: Use server-side `TIME_ARCHIVE_API_BASE_URL`, consumed by the
    route handler at runtime.
- Risk: Container build time increases CI or local feedback time.
  - Mitigation: Keep the Dockerfile simple and cache npm install through
    package lock layers.
- Risk: API might not be ready when web starts.
  - Mitigation: The web server can start independently; smoke verification
    checks the proxy after the stack starts.
- Rollback: Revert the Dockerfile, compose service, and documentation changes.
  No schema or runtime API behavior changes are expected.

## Verification Plan

- `npm.cmd run lint` under `apps/web`
- `npm.cmd run build` under `apps/web`
- `docker build -t time-archive-web:local apps/web`
- `docker compose config`
- `docker compose up -d --build`
- `GET http://localhost:3000`
- `GET http://localhost:3000/api/timeline?from=0&to=1`
- `docker compose down`
- `git diff --check`

## Open Questions

- Should a future `docker-compose.dev.yml` add bind-mounted hot reload for web
  development?

## Progress Log

- 2026-06-18: Updated `main` from `origin/main`.
- 2026-06-18: Created `codex/add-web-docker-compose-service`.
- 2026-06-18: Created implementation plan.
- 2026-06-18: Added `apps/web/Dockerfile` and `.dockerignore`.
- 2026-06-18: Added Docker Compose `web` service on port `3000`.
- 2026-06-18: Updated README, web README, and operations documentation.
- 2026-06-18: Adjusted CI so backend flow jobs start only the `api` service,
  while a new `Local web smoke` job verifies the full stack including web.
- 2026-06-18: Verified web lint/build, Docker image build, Docker Compose
  config, full-stack startup, web root response, and web timeline proxy.
- 2026-06-18: Investigated GitHub Actions run `27709961075`; `Local web smoke`
  failed with `curl: (52) Empty reply from server` immediately after the web
  container reached the Docker `Started` state.
- 2026-06-18: Updated the web smoke verification step to wait and retry the web
  root page and timeline proxy before failing.
