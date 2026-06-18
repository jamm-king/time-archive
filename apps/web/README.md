# Time Archive Web

This is the Time Archive frontend application.

It is a Next.js, React, TypeScript, and Tailwind CSS app under `apps/web`.
The initial implementation is a minimal fullscreen player shell. Public
timeline and authentication API integration are implemented through same-origin
Next.js route handlers.

## Getting Started

Install dependencies:

```bash
npm install
```

Run the development server:

```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser.

The player proxies public timeline reads through:

```text
/api/timeline
```

The app also proxies session auth and CSRF calls through:

```text
/api/csrf
/api/auth/register
/api/auth/login
/api/auth/logout
/api/me
```

The proxy forwards requests and backend `Set-Cookie` headers so browser cookies
stay scoped to the frontend origin. Mutating auth requests fetch `GET /api/csrf`
and send `X-XSRF-TOKEN`.

Override the backend API base URL when needed:

```bash
TIME_ARCHIVE_API_BASE_URL=http://localhost:8080 npm run dev
```

## Verification

```bash
npm run lint
npm run build
```

## Docker

Build the image from the repository root:

```bash
docker build -t time-archive-web:local apps/web
```

Run the full local stack from the repository root:

```bash
docker compose up -d --build
```

The Dockerized web app listens on:

```text
http://localhost:3000
```

The Compose service sets `TIME_ARCHIVE_API_BASE_URL=http://api:8080` so the
Next.js route handler can call the backend API through the Compose network.

## Notes

The fullscreen player is intentionally CSR-first. Future static or
server-rendered pages can still be added with Next.js routes.

The project uses an npm `overrides` entry to pin `postcss` to a patched version
because the scaffolded Next.js dependency tree otherwise reports a moderate
audit finding.
