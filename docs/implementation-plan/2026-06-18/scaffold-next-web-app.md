# Scaffold Next Web App

## Objective

Add the initial frontend scaffold under `apps/web` so Time Archive can start
building the public fullscreen player on top of the existing public timeline
API.

## Scope

- Add a Next.js web app under `apps/web`.
- Use TypeScript, React, App Router, Tailwind CSS, and ESLint.
- Keep the initial UI minimal and focused on the public timeline player entry
  point.
- Add frontend CI checks.
- Update repository documentation for local frontend development.
- Do not change backend behavior.

## Out of Scope

- Full public timeline player implementation.
- Admin moderation UI.
- Authentication.
- Payment UI.
- API client generation from OpenAPI.
- Dockerizing the frontend.
- Deployment configuration.

## Relevant Files or Modules

- `apps/web`
- `.github/workflows/ci.yml`
- `.gitignore`
- `README.md`
- `docs/architecture/time-archive-architecture.md`
- `docs/implementation-plan/2026-06-18/scaffold-next-web-app.md`

## Dependency Decisions

- Next.js: chosen for a frontend that can stay CSR-first for the player while
  still supporting future static or server-rendered pages.
- React: Next.js' primary UI runtime and appropriate for player state.
- TypeScript: required for API response typing and safer UI iteration.
- Tailwind CSS: minimal styling with low component overhead.
- ESLint: baseline static checks for CI.

Alternatives considered:

- Vite + React: simpler for a pure SPA, but less flexible for future share,
  static, and marketing/legal pages.
- Redux: unnecessary for the scaffold and early player state.
- TanStack Query: useful later when API integration starts, but intentionally
  deferred until a concrete data-fetching implementation is added.

License compatibility:

- These dependencies are commonly MIT-licensed or permissively licensed in the
  React/Next.js ecosystem. The exact installed package metadata should be
  reviewed if additional dependencies are added later.

## Key Design Decisions

- Use `apps/web` as a standalone npm project.
- Keep root-level Docker Compose focused on backend and local verification for
  now.
- Use CSR-friendly client components for the future player, but keep the initial
  scaffold simple.
- Avoid adding a root package manager workspace until there is real shared
  tooling between `apps/api` and `apps/web`.
- Keep the initial page as the real product entry point, not a marketing landing
  page.

## Step-by-Step Execution Plan

- [x] Update `main` and create a dedicated feature branch.
- [x] Create this implementation plan.
- [x] Confirm current Next.js scaffold command and package versions.
- [x] Generate the Next.js app under `apps/web`.
- [x] Replace starter UI with a minimal Time Archive player shell.
- [x] Add or adjust frontend CI checks.
- [x] Update README and architecture documentation.
- [x] Run frontend lint and build.
- [x] Run backend test/build smoke checks if touched indirectly.
- [x] Commit and push the branch.

## Risks and Rollback Strategy

- Risk: `create-next-app` output may differ by current version.
  - Mitigation: Inspect generated files and keep the committed scaffold small.
- Risk: Frontend CI adds runtime cost.
  - Mitigation: Run only install, lint, and build for `apps/web`.
- Risk: Generated starter UI may conflict with the intended minimal design.
  - Mitigation: Replace it with a restrained player shell immediately.
- Rollback: Revert the scaffold commit. No backend or database behavior changes
  are expected.

## Verification Plan

- `npm.cmd install` or scaffold-generated install under `apps/web`
- `npm.cmd run lint` under `apps/web`
- `npm.cmd run build` under `apps/web`
- `.\gradlew.bat test --max-workers=2` under `apps/api` if backend files are
  touched indirectly
- `git diff --check`

## Open Questions

- Should a root-level package workspace be introduced after the frontend starts
  sharing scripts or generated OpenAPI types?

## Progress Log

- 2026-06-18: Updated `main` from `origin/main`.
- 2026-06-18: Created `codex/scaffold-next-web-app`.
- 2026-06-18: Created implementation plan.
- 2026-06-18: Confirmed `create-next-app@16.2.9` options.
- 2026-06-18: Scaffolded `apps/web` with Next.js, React, TypeScript,
  Tailwind CSS, ESLint, and npm.
- 2026-06-18: Added npm `overrides.postcss=8.5.10` after `npm audit`
  reported a moderate transitive PostCSS finding through Next.js.
- 2026-06-18: Replaced starter page with a minimal Time Archive player shell.
- 2026-06-18: Added frontend CI and updated repository documentation.
- 2026-06-18: Verified `npm ci`, `npm run lint`, `npm run build`,
  backend tests, `git diff --check`, and local dev server HTTP rendering.

## Completion Summary

Added the initial Time Archive frontend scaffold under `apps/web`.

The app uses Next.js 16.2.9, React 19.2.4, TypeScript, Tailwind CSS, ESLint,
and npm. The first page is a minimal fullscreen player shell that establishes
the public product surface without implementing timeline API integration yet.

Frontend CI now installs dependencies with `npm ci`, runs lint, and builds the
app.

## Files Changed

- `.github/workflows/ci.yml`
- `README.md`
- `apps/web/.gitignore`
- `apps/web/README.md`
- `apps/web/eslint.config.mjs`
- `apps/web/next.config.ts`
- `apps/web/package-lock.json`
- `apps/web/package.json`
- `apps/web/postcss.config.mjs`
- `apps/web/src/app/globals.css`
- `apps/web/src/app/layout.tsx`
- `apps/web/src/app/page.tsx`
- `apps/web/tsconfig.json`
- `docs/architecture/time-archive-architecture.md`
- `docs/implementation-plan/2026-06-18/scaffold-next-web-app.md`

## Tests Run and Results

- `npx.cmd create-next-app@latest --help` - passed
- `npm.cmd install` under `apps/web` - passed
- `npm.cmd audit --audit-level=moderate` under `apps/web` - initially reported
  a moderate transitive PostCSS finding through Next.js
- `npm.cmd install` after adding `overrides.postcss=8.5.10` - passed with
  0 vulnerabilities
- `npm.cmd ci` under `apps/web` - passed with 0 vulnerabilities
- `npm.cmd run lint` under `apps/web` - passed
- `npm.cmd run build` under `apps/web` - passed
- `.\gradlew.bat test --max-workers=2` under `apps/api` - passed
- `git diff --check` - passed

## Manual Verification Results

- Started the local frontend dev server on `http://127.0.0.1:3000`.
- Confirmed HTTP 200 response.
- Confirmed rendered HTML contains `Time Archive`.
- Stopped the local dev server after verification.

## Known Limitations

- The player shell does not fetch `GET /api/timeline` yet.
- No admin, owner, auth, or purchase UI exists yet.
- The frontend is not Dockerized yet.
- No root-level npm workspace is configured yet.

## Follow-up Recommendations

- Implement the public timeline player against `GET /api/timeline`.
- Add API base URL environment configuration before API integration.
- Add frontend E2E or component tests once player behavior is introduced.
