# Implement Public Timeline Player

## Objective

Implement the first usable public timeline player in `apps/web`.

The player should fetch approved public timeline segments from the backend and
render the active segment for the current second of the canonical 24-hour
timeline.

## Scope

- Add a client-side public timeline player.
- Fetch `GET /api/timeline?from=&to=` from the configured API base URL.
- Display approved image or video media for the active second.
- Show minimal loading, empty, and error states.
- Show current time and 24-hour progress.
- Keep the UI fullscreen, minimal, and player-first.
- Add local environment documentation for the frontend API base URL.

## Out of Scope

- Admin moderation UI.
- Owner dashboard.
- Authentication.
- Purchase UI.
- Timeline scrubbing.
- Multi-window preloading.
- CDN manifest reads.
- Generated OpenAPI client.
- Frontend E2E automation.

## Relevant Files or Modules

- `apps/web/src/app/page.tsx`
- `apps/web/src/app/globals.css`
- `apps/web/src/components`
- `apps/web/src/lib`
- `apps/web/README.md`
- `README.md`
- `docs/architecture/time-archive-architecture.md`
- `docs/implementation-plan/2026-06-18/implement-public-timeline-player.md`

## Key Design Decisions

- Keep the player CSR-first because playback state, ticking time, media
  switching, and future controls are browser-state-heavy.
- Use native `fetch` and React state for the first implementation. TanStack
  Query is deferred until data fetching becomes broader than the player.
- Fetch a 300-second window around the current second, matching the architecture
  sketch.
- Use a same-origin Next.js route handler at `/api/timeline` to proxy public
  timeline reads to the backend.
- Use `TIME_ARCHIVE_API_BASE_URL` for the server-side backend API base URL.
- Default local API base URL to `http://localhost:8080`.
- Render unowned or empty seconds as a quiet placeholder, not as explanatory
  product copy.
- Videos autoplay muted, loop, and use `playsInline`.

## Step-by-Step Execution Plan

- [x] Update `main` and create a dedicated feature branch.
- [x] Create this implementation plan.
- [x] Add timeline API types and fetch helper.
- [x] Add time formatting and active segment helpers.
- [x] Implement fullscreen public timeline player component.
- [x] Replace the scaffold page with the player.
- [x] Update frontend and repository docs.
- [x] Run frontend lint and build.
- [x] Run backend smoke tests if needed.
- [x] Verify local rendering.
- [x] Commit and push the branch.

## Risks and Rollback Strategy

- Risk: Browser requests to the backend may fail if the API is not running or
  CORS blocks local development.
  - Mitigation: Keep error state clear and document local API base URL.
  - Follow-up: Add CORS configuration or a Next.js proxy if needed.
- Risk: Remote approved media URLs may not be optimized by Next Image.
  - Mitigation: Use native `img` for this initial player.
- Risk: The first implementation fetches only one window.
  - Mitigation: Refetch when the active second leaves the loaded window.
- Rollback: Revert the player commit. No backend or data changes are expected.

## Verification Plan

- `npm.cmd run lint` under `apps/web`
- `npm.cmd run build` under `apps/web`
- `.\gradlew.bat test --max-workers=2` under `apps/api` if backend behavior is
  touched indirectly
- Local dev server HTTP check
- `git diff --check`

## Open Questions

- Should the next iteration use a Next.js route handler as an API proxy to avoid
  browser CORS configuration?

## Progress Log

- 2026-06-18: Updated `main` from `origin/main`.
- 2026-06-18: Created `codex/implement-public-timeline-player`.
- 2026-06-18: Created implementation plan.
- 2026-06-18: Added timeline API types, helper functions, and fetch helper.
- 2026-06-18: Added a same-origin Next.js `/api/timeline` route handler that
  proxies to the backend API.
- 2026-06-18: Implemented the first fullscreen public timeline player.
- 2026-06-18: Updated frontend, root, and architecture documentation.
- 2026-06-18: Fixed React lint findings by deriving loading state from the
  loaded timeline window.
- 2026-06-18: Verified frontend lint/build, backend tests, Docker Compose API
  startup, page rendering, and the `/api/timeline` proxy.

## Completion Summary

Implemented the first usable public timeline player in `apps/web`.

The player now:

- Tracks the current second of the day.
- Fetches a 300-second public timeline window.
- Proxies timeline reads through the same-origin Next.js `/api/timeline` route.
- Renders approved image or video media for the active segment.
- Shows loading, empty, and error states.
- Displays current archive time, loaded window, and 24-hour progress.

## Files Changed

- `README.md`
- `apps/web/README.md`
- `apps/web/src/app/api/timeline/route.ts`
- `apps/web/src/app/page.tsx`
- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `apps/web/src/lib/timeline.ts`
- `docs/architecture/time-archive-architecture.md`
- `docs/implementation-plan/2026-06-18/implement-public-timeline-player.md`

## Tests Run and Results

- `npm.cmd run lint` under `apps/web` - passed
- `npm.cmd run build` under `apps/web` - passed
- `.\gradlew.bat test --max-workers=2` under `apps/api` - passed
- `git diff --check` - passed
- `docker compose up -d --build` - passed
- `docker compose down` - passed

## Manual Verification Results

- Started the frontend dev server on `http://127.0.0.1:3000`.
- Confirmed the page returns HTTP 200.
- Confirmed `GET http://127.0.0.1:3000/api/timeline?from=0&to=1` returns:

```json
{"from":0,"to":1,"segments":[]}
```

This confirms the Next.js route handler can proxy to the local backend API.

## Known Limitations

- The player fetches only the active 300-second window.
- There is no scrubbing, manual playback control, or preloading yet.
- Empty seconds show a placeholder only.
- Frontend automated browser tests are not added yet.
- Approved MinIO object URLs may still require storage-level public access or a
  signed delivery strategy for real playback.

## Follow-up Recommendations

- Add player window preloading before the active second crosses a window
  boundary.
- Add hide/removal E2E coverage once player behavior is stable.
- Add a minimal admin moderation UI or owner upload UI after the public playback
  surface is validated.
