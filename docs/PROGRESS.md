# Progress log

Build plan: `docs/DESIGN.md` section 19. Newest entries at the bottom of each day.

## Day 1: skeleton, auth, Flyway V1, CI, design system, hello-world deploy

Done when: sign up and log in work, CI is green.

- [x] Spring Boot 4.1 scaffold builds; tests run against Postgres 16 via Testcontainers
- [x] Flyway V1 schema, env-based config, `Clock` bean, JSON error shape (schema invariants pinned by `SchemaConstraintsTest`)
- [x] Auth: signup, login, `GET /api/me` (BCrypt, 2h HS256 JWT, owner-scoped lookups, CORS). Login rate limit deferred to Day 8 with the other Bucket4j limits
- [x] CI workflow (backend `mvn verify`); first run pending a GitHub remote
- [x] Frontend scaffold: Next.js 16.3.8, Tailwind 4, shadcn/ui (Radix, Nova), dark-first tokens, login/signup/dashboard shell; 12 Playwright e2e checks (desktop + mobile)
- [ ] Hello-world deploy (API on Render, Postgres on Supabase, frontend on Vercel)

### Notes

- Next pinned to 16.3.8: 16.0.0 to 16.3.7 carry published advisories (image-optimizer SSRF, ISR cache poisoning).
  `npm audit --omit=dev` is clean. Five remaining dev-only findings sit in ESLint's `braces` chain with no upstream fix.
- `npm run e2e` runs on its own ports (API 8180, web 3100, build dir `.next-e2e`) because Playwright's output folder
  triggers dev-server Fast Refresh reloads that drop in-flight form submissions.
- Deploy is blocked on credentials: every value in `secrets.local.env` except `JWT_SECRET` is still empty.
