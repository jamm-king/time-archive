# Time Archive Web

This is the Time Archive frontend application.

It is a Next.js, React, TypeScript, and Tailwind CSS app under `apps/web`.
The initial implementation is a minimal fullscreen player shell. Public
timeline API integration will be added in a follow-up task.

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

## Verification

```bash
npm run lint
npm run build
```

## Notes

The frontend is intentionally CSR-friendly for the fullscreen player experience.
Future static or server-rendered pages can still be added with Next.js routes.

The project uses an npm `overrides` entry to pin `postcss` to a patched version
because the scaffolded Next.js dependency tree otherwise reports a moderate
audit finding.
