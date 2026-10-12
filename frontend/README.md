# PayNudge web

Next.js 16 (App Router), TypeScript, Tailwind 4, shadcn/ui (Radix), TanStack Query, motion.

## Run

```bash
cp .env.example .env.local   # NEXT_PUBLIC_API_URL, defaults to http://localhost:8080
npm install
npm run dev                  # http://localhost:3000, needs the API running (see ../CLAUDE.md)
```

## Checks

```bash
npm run lint && npm run typecheck && npm run build
npm run e2e                  # Playwright against the real API; needs `docker compose up -d db`
```

`npm run e2e` starts its own API on :8180 and a production build on :3100 (built into `.next-e2e`), so it never
interferes with a running dev server.

## Notes

- The API lives on another origin, so the session is a short-lived bearer token in `localStorage`, cleared on any 401.
- Server data goes through TanStack Query. Every async view has loading (skeleton), empty and error states.
- Dark-first theme. An inline script in `app/layout.tsx` applies a saved light preference before paint.
