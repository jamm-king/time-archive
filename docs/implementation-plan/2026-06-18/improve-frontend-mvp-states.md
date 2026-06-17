# Improve Frontend MVP States

## Objective

Improve the frontend MVP player states so the public timeline experience is
clear, resilient, and minimal across loading, empty, error, and media failure
cases.

## Scope

- Improve loading, empty, and error states in the public timeline player.
- Add a retry path for timeline fetch failures.
- Add basic media loading and media failure handling.
- Validate public timeline response shape before rendering.
- Preserve the fullscreen, minimal product direction.
- Keep the implementation dependency-free.

## Out of Scope

- Authentication.
- Purchase UI.
- Owner upload UI.
- Admin moderation UI.
- Timeline scrubbing.
- Browser E2E test framework setup.
- Visual redesign or a new component system.

## Relevant Files or Modules

- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `apps/web/src/lib/timeline.ts`
- `apps/web/src/app/globals.css`
- `docs/implementation-plan/2026-06-18/improve-frontend-mvp-states.md`

## Key Design Decisions

- Keep state handling inside the player because the current frontend has only
  one user-facing surface.
- Use native React state and browser media events instead of adding a data
  fetching or playback dependency.
- Treat invalid timeline responses as fetch failures so rendering code receives
  trusted data.
- Keep visible text short and functional. The player should remain the first
  screen, not become a product explanation page.

## Step-by-Step Execution Plan

- [x] Update `main` and create a dedicated feature branch.
- [x] Create this implementation plan.
- [x] Validate timeline API response shape in the frontend fetch helper.
- [x] Add retry support for failed timeline fetches.
- [x] Improve loading, empty, and error state presentation.
- [x] Add media loading and media error fallback.
- [x] Verify lint and production build.
- [x] Verify the player through local HTTP checks.
- [x] Update this plan with completion details.
- [ ] Commit and push the branch.

## Risks and Rollback Strategy

- Risk: Additional state handling could make the minimal player feel busy.
  - Mitigation: Keep the UI quiet and only expose state details where they help
    recovery.
- Risk: Response validation could reject backend-compatible data if the
  frontend schema is too strict.
  - Mitigation: Validate only fields currently required for playback.
- Risk: Media fallback could hide a real storage configuration problem.
  - Mitigation: Show a concise media unavailable state and keep console details
    limited to development diagnostics.
- Rollback: Revert the frontend state changes. No backend, schema, or persisted
  data changes are expected.

## Verification Plan

- `npm.cmd run lint` under `apps/web`
- `npm.cmd run build` under `apps/web`
- Local browser verification of loading, empty, and error-safe layout
- `git diff --check`

## Open Questions

- None.

## Progress Log

- 2026-06-18: Confirmed `main` was up to date.
- 2026-06-18: Created `codex/improve-frontend-mvp-states`.
- 2026-06-18: Created implementation plan.
- 2026-06-18: Added frontend response validation for public timeline reads.
- 2026-06-18: Added timeline fetch retry support in the player error state.
- 2026-06-18: Improved loading, empty, and error copy while keeping the
  fullscreen player layout minimal.
- 2026-06-18: Added media loading and media unavailable overlays for active
  image and video segments.
- 2026-06-18: Verified frontend lint and production build.
- 2026-06-18: Verified the local page and timeline proxy with HTTP checks.

## Completion Summary

Improved the frontend MVP public timeline states without adding dependencies.

The player now:

- Validates timeline API responses before rendering.
- Lets users retry failed timeline reads.
- Shows concise loading, empty, and error state details.
- Handles active media loading and media failure independently from timeline
  data loading.

## Files Changed

- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `apps/web/src/lib/timeline.ts`
- `docs/implementation-plan/2026-06-18/improve-frontend-mvp-states.md`

## Tests Run and Results

- `npm.cmd run lint` under `apps/web` - passed
- `npm.cmd run build` under `apps/web` - passed
- `git diff --check` - passed
- `docker compose up -d --build api` - passed
- `GET http://127.0.0.1:3000` - passed with HTTP 200
- `GET http://127.0.0.1:3000/api/timeline?from=0&to=1` - passed with
  `{"from":0,"to":1,"segments":[]}`

## Manual Verification Results

Started the local API stack and frontend dev server. Confirmed that the page
serves successfully and that the Next.js timeline proxy returns the expected
empty public timeline response.

The in-app browser control tool was not available in this session, so visual
screenshot verification was not performed.

## Known Limitations

- Browser screenshot verification was not available in this session.
- `docker compose down` could not be completed after verification because the
  local Docker engine pipe was unavailable. Docker Desktop may need to be
  restarted before the next Docker command.
- The player still has no timeline scrubbing, purchase UI, or auth-aware owner
  actions.

## Follow-up Recommendations

- Add a lightweight browser smoke test when frontend behavior stabilizes.
- Add test coverage for timeline response parsing if the frontend introduces a
  test runner.
