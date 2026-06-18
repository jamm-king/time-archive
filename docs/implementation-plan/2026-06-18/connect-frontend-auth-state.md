# Connect Frontend Auth State

## Objective

Connect the Next.js frontend to the backend session authentication and CSRF
contract so the app can load, display, register, log in, and log out the current
user state.

## Scope

- Add same-origin Next.js route handlers for auth, current-user, and CSRF API
  calls.
- Preserve backend `Set-Cookie` headers through the frontend proxy.
- Add a small frontend auth client that fetches CSRF tokens and sends
  `X-XSRF-TOKEN` for mutations.
- Add minimal login/register/logout UI to the fullscreen player without changing
  the product direction.
- Document local frontend auth behavior.

## Out of Scope

- Owned range listing.
- Media upload UI.
- Admin UI.
- Payment UI.
- OAuth, password reset, and email verification.

## Relevant Files and Modules

- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `apps/web/src/lib`
- `apps/web/src/app/api`
- `apps/web/README.md`
- `README.md`

## Key Design Decisions

- Keep browser requests same-origin by proxying backend auth endpoints through
  Next route handlers. This lets the web app keep session and CSRF cookies under
  the frontend origin in both local development and Docker Compose.
- Use a minimal in-player auth panel rather than introducing a separate route or
  broader navigation model.
- Keep auth state client-side because the current player is CSR-first and does
  not need server-rendered personalization.
- Do not add dependencies.

## Execution Plan

- [x] Create a dedicated feature branch from latest `main`.
- [x] Document this implementation plan.
- [x] Add reusable backend proxy helpers for Next route handlers.
- [x] Add `/api/csrf`, `/api/auth/register`, `/api/auth/login`,
  `/api/auth/logout`, and `/api/me` frontend route handlers.
- [x] Add frontend auth client helpers and state handling.
- [x] Add minimal auth UI states to the fullscreen player.
- [x] Update frontend documentation.
- [x] Run lint/build verification.
- [x] Run Docker Compose web smoke if practical.
- [x] Record completion details in this plan.

## Risks and Rollback Strategy

- Risk: `Set-Cookie` forwarding can be incomplete and break sessions.
  - Mitigation: Centralize proxy response construction and preserve all
    upstream `Set-Cookie` values.
- Risk: Auth UI can clutter the minimal player.
  - Mitigation: Keep it as a compact top-right panel with restrained styling.
- Risk: CSRF token refresh can become stale after login/register/logout.
  - Mitigation: Fetch CSRF before mutations and reload current user after auth
    mutations.
- Rollback: Revert frontend proxy route handlers and auth UI changes; public
  timeline proxy and player remain independent.

## Verification Plan

- Run `npm run lint` in `apps/web`.
- Run `npm run build` in `apps/web`.
- If practical, run Docker Compose web smoke after building.

## Open Questions

- None.

## Progress Log

- 2026-06-18: Confirmed `main` includes backend session identity, admin role,
  and CSRF protection.
- 2026-06-18: Created `feature/frontend-auth-state`.
- 2026-06-18: Added same-origin auth and CSRF proxy route handlers.
- 2026-06-18: Added client-side auth helpers and compact player auth UI.
- 2026-06-18: Verified lint, production build, Docker Compose web smoke, and
  same-origin auth proxy behavior.

## Completion Summary

The frontend now understands backend session authentication state. It loads the
current user through `/api/me`, supports login, registration, and logout, and
uses `/api/csrf` plus `X-XSRF-TOKEN` for mutating auth requests.

Auth and CSRF calls are proxied through same-origin Next.js route handlers so
browser cookies stay scoped to the web origin. The fullscreen player keeps its
minimal direction and exposes auth as a compact top-right control.

## Files Changed

- `apps/web/src/lib/backend-proxy.ts`
- `apps/web/src/lib/auth.ts`
- `apps/web/src/app/api/csrf/route.ts`
- `apps/web/src/app/api/me/route.ts`
- `apps/web/src/app/api/auth/register/route.ts`
- `apps/web/src/app/api/auth/login/route.ts`
- `apps/web/src/app/api/auth/logout/route.ts`
- `apps/web/src/app/api/timeline/route.ts`
- `apps/web/src/components/PublicTimelinePlayer.tsx`
- `apps/web/README.md`
- `README.md`
- `docs/implementation-plan/2026-06-18/connect-frontend-auth-state.md`

## Tests Run and Results

- `npm.cmd run lint` in `apps/web`: passed.
- `npm.cmd run build` in `apps/web`: passed.
- `docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d --build api web`: passed.
- Web home smoke against `http://localhost:3000`: passed.
- Same-origin auth proxy smoke against `http://localhost:3000/api/csrf`,
  `/api/auth/register`, and `/api/me`: passed.
- `git diff --check`: passed.

## Manual Verification Results

Docker Compose web and API services were started. A smoke verification fetched a
CSRF token through the web origin, confirmed unauthenticated `/api/me` returned
401, registered a new user through the web auth proxy with `X-XSRF-TOKEN`, and
then confirmed `/api/me` returned the registered user through the same cookie
session.

The home page returned HTTP 200 and included the Time Archive player shell.

## Known Limitations

- In-app browser screenshot verification was not available because the Node REPL
  browser check failed to initialize its kernel assets in this session.
- The frontend still does not expose owned ranges, media upload, admin UI, or
  payment UI.
- Auth state is client-side only and intentionally scoped to the current
  fullscreen player shell.

## Follow-up Recommendations

- Add owned range listing so authenticated users can see purchased seconds.
- Add media upload UI on top of the current authenticated session and CSRF
  client contract.
- Add a frontend smoke script if the auth proxy flow becomes a required CI gate.
