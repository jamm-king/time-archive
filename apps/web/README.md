# Time Archive Web

This is the Time Archive frontend application.

It is a Next.js, React, TypeScript, and Tailwind CSS app under `apps/web`.
The initial implementation is a minimal fullscreen player shell. Public
timeline API integration is implemented through a same-origin Next.js route
handler.

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

The proxy forwards requests to the backend API. Override the backend API base
URL when needed:

```bash
TIME_ARCHIVE_API_BASE_URL=http://localhost:8080 npm run dev
```

## Verification

```bash
npm run lint
npm run build
```

## Notes

The fullscreen player is intentionally CSR-first. Future static or
server-rendered pages can still be added with Next.js routes.

The project uses an npm `overrides` entry to pin `postcss` to a patched version
because the scaffolded Next.js dependency tree otherwise reports a moderate
audit finding.
