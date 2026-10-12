# PayNudge

Small businesses with recurring dues (tuition centers, gyms, PGs) import a CSV of dues, pick a reminder policy once,
and reminders go out on schedule, escalate, stop the moment someone pays, and never send twice.

Design source of truth: `docs/DESIGN.md` (from the original project guide). Progress log: `docs/PROGRESS.md`.

## Layout

- `backend/` Spring Boot 4.1 (Java 21, Maven) - modular monolith, package per module under `com.paynudge`:
  `auth, business, customer, dues, policy, engine, channel, payment, assistant, realtime, security, common`
- `frontend/` Next.js 16 App Router, TypeScript, Tailwind 4, shadcn/ui
- `docker-compose.yml` local Postgres 17 for development (same major version as production on Supabase)
- `.github/workflows/` CI (build + tests) and deploy
- `docs/benchmarks/` raw k6 / JaCoCo / test output backing every number quoted in the README

## Rules

- Plan first, then implement. Small steps; commit after each working step.
- Database changes only through Flyway migrations in `backend/src/main/resources/db/migration` (never `ddl-auto=update`; JPA runs with `validate`).
- Constructor injection only. DTOs (records) at API boundaries; never expose entities.
- Every feature gets tests. `mvn verify` (backend) and `npm run lint && npm run typecheck && npm run build` (frontend) must pass before a task is done.
- Tests use a real Postgres via Testcontainers (never H2), an injectable `Clock`, and a recording `FakeChannel`.
- Money is integer paise (`long amountPaise`), never floating point.
- No secrets in code or in the repo. Configuration comes from environment variables. Local secrets live in the git-ignored `secrets.local.env`.
- MVP scope only: no Kafka, no multi-tenancy/RLS, no WhatsApp/SMS sending (stub behind the `Channel` interface), no partial payments.
- One JSON error shape everywhere: `{ "code", "message", "details" }`.
- Every query is scoped by the caller's `business_id`; add an isolation test for every new endpoint.

## Engine invariants (do not weaken)

- Delivery is at-least-once, made safe by idempotency.
- `UNIQUE (due_id, step_key)` on `due_steps`; `UNIQUE (due_id, step_key, channel)` on `messages`.
- Workers claim with `FOR UPDATE SKIP LOCKED` plus a lease (`lease_until`); a reaper requeues expired leases.
- Executing a step locks the due row (`SELECT ... FOR UPDATE`) and re-checks status; the payment webhook takes the same lock. PAID/CANCELLED/opted-out always wins.
- A policy is copied into the due as `policy_snapshot`; later policy edits never change in-flight dues.
- Customer messages respect quiet hours, a minimum gap, the daily cap, and always carry an unsubscribe link.
- AI output is untrusted data: amounts, dates and pay links are inserted by code, never by the model. The chatbot is read-only.

## Frontend

- shadcn/ui components, `motion` for animation (150-400 ms), respect `prefers-reduced-motion`.
- Every async view has loading (skeleton), empty and error states.
- Server data through TanStack Query; SSE events merge into the query cache.

## Local development (Windows, everything on D:)

- Backend: `cd backend; mvn spring-boot:run` (uses the installed `mvn`; the Maven wrapper is for CI). Tests need Docker Desktop running.
- Local DB: `docker compose up -d db` from the repo root.
- Frontend: `cd frontend; npm run dev`.
- Tool caches are already redirected to D:\Dev (Maven repo, npm cache, Docker data); do not install anything to C:.
