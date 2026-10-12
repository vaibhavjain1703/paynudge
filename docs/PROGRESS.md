# Progress log

Build plan: `docs/DESIGN.md` section 19. Newest entries at the bottom of each day.

## Day 1: skeleton, auth, Flyway V1, CI, design system, hello-world deploy

Done when: sign up and log in work, CI is green.

- [x] Spring Boot 4.1 scaffold builds; tests run against Postgres 16 via Testcontainers
- [x] Flyway V1 schema, env-based config, `Clock` bean, JSON error shape (schema invariants pinned by `SchemaConstraintsTest`)
- [x] Auth: signup, login, `GET /api/me` (BCrypt, 2h HS256 JWT, owner-scoped lookups, CORS). Login rate limit deferred to Day 8 with the other Bucket4j limits
- [x] CI workflow (backend `mvn verify`); first run pending a GitHub remote
- [ ] Frontend scaffold: Next.js 16, Tailwind 4, shadcn/ui, design tokens, login/signup
- [ ] Hello-world deploy (API on Render, Postgres on Supabase, frontend on Vercel)
