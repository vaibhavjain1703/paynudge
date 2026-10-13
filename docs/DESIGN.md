# PayNudge: Full Project Guide and System Design

> Original project guide (system design + build plan). Code blocks were flattened when exported from Word; the authoritative versions live in the code: schema in `backend/src/main/resources/db/migration`, engine SQL in `backend/src/main/java/com/paynudge/engine`.

Oct 12, 2026 · @vaibhav jain
PayNudge lets a small business with recurring dues (tuition centers, gyms, PGs, small landlords) upload a dues sheet, pick a reminder policy once, and have reminders go out on schedule, escalate on their own, stop the moment someone pays, and never send twice.
## 1. Problem and positioning
- Problem: small businesses chase late payments by hand over WhatsApp and calls. It is slow, awkward, and cash flow suffers.
- Users: the owner or admin (creates dues, sets policy, watches the dashboard) and the customer (receives reminders, pays through a link).
- Promise: import a CSV, choose a policy, and collection runs itself with safe escalation and a full audit trail.
- Why not Zoho Invoice, Vyapar or Razorpay payment links? They include basic reminders, so check their current features yourself. Your angle is a narrow niche (recurring dues), escalation policies, two-minute CSV import, and provable never-twice delivery.
- Pilot idea: one tuition center, gym or PG owner you know. Real usage, even from one business, is worth more than extra features.
## 2. Scope
| In the MVP | Out (future work) |
| Auth, one business per owner | Multi-tenancy with row-level security |
| CSV import with validation and preview | Accounting software integrations |
| Collection policies with 3 starter templates | WhatsApp and SMS (provider approvals needed) |
| Postgres-backed scheduler, workers, retries, crash recovery | Kafka or SQS |
| Email reminders through a sandbox provider | Partial payments and instalments |
| Razorpay test-mode pay link and verified webhook | Real-money flow (needs provider verification) |
| Live dashboard and due timeline (SSE) | Mobile app |
| AI: template drafting, CSV column mapping, small chatbot | Per-send AI text generation |

## 3. Core user flow
- Owner signs up and sets business name, timezone, quiet hours and sender name.
- Owner imports a CSV of dues (name, email or phone, amount, due date), maps columns, reviews errors, and confirms.
- Owner picks a collection policy, for example Friendly then firm.
- The system creates one due per row and schedules every reminder step in one transaction.
- Workers send each reminder at its time. Every message carries a pay link and an unsubscribe link.
- The customer pays. The webhook marks the due PAID and cancels all remaining steps.
- The owner watches collected, overdue and upcoming reminders live.
## 4. Architecture
Browser (Next.js) --REST + SSE--> Caddy (HTTPS) --> Spring Boot (modular monolith)                                                    |- auth, business, customers, dues, policies                                                    |- engine: scheduler, StepWorker, LeaseReaper                                                    |- channel: EmailChannel (sandbox), SmsChannel (stub)                                                    |- payment: pay link, Razorpay webhook verifier                                                    |- assistant: drafting, column mapping, chatbot tools                                                    |- realtime: SSE publisher                                                    |                         PostgreSQL (state + queue + audit) <--+Razorpay (test mode) --webhook--> /webhooks/razorpayLLM API <-- assistant module only (key stays on the server)
Key decisions:
- One deployable, clean modules. Cheap to host and easy to debug. The engine can become its own service later.
- Postgres is the queue. A step is a row with a future run_at. A wait costs nothing because no thread is held. Claiming uses SKIP LOCKED.
- At-least-once delivery made safe by idempotency. Unique constraints stop duplicate sends even after crashes.
- SSE instead of WebSockets. Updates are one-way, simpler, and proxy-friendly.
## 5. Tech stack
| Layer | Choice |
| Backend | Java 21 (virtual threads), Spring Boot 3, Spring Security (JWT), Spring Data JPA, Flyway, Bean Validation |
| Database | PostgreSQL 17 (Supabase in production) |
| Frontend | Next.js (App Router), TypeScript, Tailwind, shadcn/ui, motion, TanStack Query, React Hook Form + Zod, Recharts |
| Payments | Razorpay test mode (payment links and webhooks) |
| Email | Sandbox provider (Mailtrap or similar) behind a Channel interface |
| AI | One LLM API through a plain HTTP client, with tool calling for the chatbot |
| Testing | JUnit 5, Mockito, Testcontainers, Awaitility, k6, JaCoCo, Playwright (optional) |
| DevOps | Docker, Docker Compose, GitHub Actions, GHCR, Caddy |
| Observability | Actuator, Micrometer, structured JSON logs |

## 6. Repository structure
paynudge/  CLAUDE.md  docker-compose.yml  .github/workflows/ci.yml, deploy.yml  docs/benchmarks/            raw k6, JaCoCo and test output  backend/src/main/java/com/paynudge/    auth/  business/  customer/  dues/  policy/    engine/   (scheduler, StepWorker, LeaseReaper, RetryPolicy)    channel/  (Channel interface, EmailChannel, SmsChannel stub)    payment/  (PaymentLinkService, RazorpayWebhookController)    assistant/  realtime/  security/  common/  backend/src/main/resources/db/migration/V1__init.sql  frontend/app/ (marketing) (auth) (app)/dashboard,dues,customers,policies,import,settings                pay/[token]  unsubscribe/[token]  frontend/components/  frontend/lib/
## 7. Data model
Money is stored as integer paise, never floats.
CREATE TABLE users (id BIGSERIAL PRIMARY KEY, email VARCHAR(255) UNIQUE NOT NULL,  password_hash VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ DEFAULT now());CREATE TABLE businesses (id BIGSERIAL PRIMARY KEY, owner_id BIGINT NOT NULL REFERENCES users(id),  name VARCHAR(200) NOT NULL, timezone VARCHAR(50) DEFAULT 'Asia/Kolkata',  quiet_start TIME DEFAULT '21:00', quiet_end TIME DEFAULT '09:00', sender_name VARCHAR(100));CREATE TABLE customers (id BIGSERIAL PRIMARY KEY, business_id BIGINT NOT NULL REFERENCES businesses(id),  name VARCHAR(200) NOT NULL, email VARCHAR(255), phone VARCHAR(20),  opted_out BOOLEAN DEFAULT false, unsub_token VARCHAR(64) UNIQUE NOT NULL,  UNIQUE (business_id, email));CREATE TABLE policies (id BIGSERIAL PRIMARY KEY, business_id BIGINT NOT NULL REFERENCES businesses(id),  name VARCHAR(100) NOT NULL, definition JSONB NOT NULL, is_template BOOLEAN DEFAULT false);CREATE TABLE dues (id BIGSERIAL PRIMARY KEY, business_id BIGINT NOT NULL REFERENCES businesses(id),  customer_id BIGINT NOT NULL REFERENCES customers(id), policy_id BIGINT REFERENCES policies(id),  amount_paise BIGINT NOT NULL CHECK (amount_paise > 0), due_date DATE NOT NULL,  status VARCHAR(20) NOT NULL,  -- SCHEDULED | ACTIVE | PAID | CANCELLED | ESCALATED  pay_token VARCHAR(64) UNIQUE NOT NULL, policy_snapshot JSONB NOT NULL,  created_at TIMESTAMPTZ DEFAULT now());CREATE TABLE due_steps (id BIGSERIAL PRIMARY KEY,  due_id BIGINT NOT NULL REFERENCES dues(id) ON DELETE CASCADE,  step_key VARCHAR(64) NOT NULL, action VARCHAR(30) NOT NULL,  status VARCHAR(20) NOT NULL,  -- PENDING | RUNNING | DONE | FAILED | CANCELLED | SKIPPED  run_at TIMESTAMPTZ NOT NULL, attempt INT DEFAULT 0, lease_until TIMESTAMPTZ, error TEXT,  UNIQUE (due_id, step_key));CREATE INDEX idx_steps_claim ON due_steps (run_at) WHERE status = 'PENDING';CREATE TABLE messages (id BIGSERIAL PRIMARY KEY, due_id BIGINT NOT NULL REFERENCES dues(id),  step_key VARCHAR(64) NOT NULL, channel VARCHAR(20) NOT NULL, body TEXT NOT NULL,  provider_id VARCHAR(100), sent_at TIMESTAMPTZ DEFAULT now(),  UNIQUE (due_id, step_key, channel));CREATE TABLE payments (id BIGSERIAL PRIMARY KEY, due_id BIGINT NOT NULL REFERENCES dues(id),  provider_payment_id VARCHAR(100) UNIQUE NOT NULL, amount_paise BIGINT NOT NULL,  paid_at TIMESTAMPTZ NOT NULL, method VARCHAR(30));
Add indexes on dues (business_id, status, due_date) and customers (business_id) for the dashboard queries.
## 8. Collection policy
Offsets are in days relative to the due date. A policy is copied into the due as policy_snapshot so later edits never change in-flight dues.
{ "schemaVersion": 1,  "steps": [    { "key": "remind_1", "offsetDays": -2, "action": "SEND_EMAIL", "tone": "friendly" },    { "key": "remind_2", "offsetDays": 0,  "action": "SEND_EMAIL", "tone": "neutral" },    { "key": "remind_3", "offsetDays": 3,  "action": "SEND_EMAIL", "tone": "firm" },    { "key": "notify_owner", "offsetDays": 7, "action": "NOTIFY_OWNER" },    { "key": "final", "offsetDays": 10, "action": "SEND_EMAIL", "tone": "final_notice" } ],  "stopOn": ["PAID", "OPTED_OUT"] }
Validation: at most 8 steps, ascending offsets, a minimum gap (for example 24 hours) between customer messages, known actions only, unique step keys, and every template must contain the placeholders name, amount and pay_link. The gap rule is also an anti-spam safeguard.
## 9. The engine
### 9.1 Scheduling
Creating a due inserts all its due_steps in one transaction, with run_at computed from the due date and offset, then shifted out of quiet hours. A wait is just a future run_at.
### 9.2 Claiming work
UPDATE due_steps SET status = 'RUNNING', attempt = attempt + 1,       lease_until = now() + interval '60 seconds'WHERE id = (SELECT id FROM due_steps            WHERE status = 'PENDING' AND run_at <= now()            ORDER BY run_at FOR UPDATE SKIP LOCKED LIMIT 1)RETURNING *;
Many workers can run this at once. SKIP LOCKED makes each take a different row, so none blocks another and none claims the same step. Variant: claim a batch (LIMIT 10) for higher throughput, and measure the difference.
### 9.3 Executing a step (the pay-versus-remind race)
Inside one transaction the worker:
- Locks the due row with SELECT ... FOR UPDATE and re-reads its status.
- If the due is PAID, CANCELLED, or the customer opted out, marks the step CANCELLED and stops.
- Otherwise renders the template (code inserts name, amount, pay link) and calls the channel.
- Inserts into messages. The unique key (due_id, step_key, channel) rejects a duplicate send.
- Marks the step DONE and publishes an SSE event after commit.
The payment webhook takes the same row lock to set PAID and cancel pending steps. Whichever commits first wins, so a customer who just paid never gets an overdue message. Keep the external send inside the lock only if it is fast (short timeout); otherwise insert the message row first as the claim, then send.
### 9.4 Retries
Transient errors (timeouts, provider 5xx) return the step to PENDING with run_at = now + base x 2^attempt + jitter. Permanent errors (invalid address) fail immediately. After max attempts the step is FAILED and the owner is notified.
### 9.5 Crash recovery
A reaper runs every 15 seconds:
UPDATE due_steps SET status = 'PENDING', run_at = now()WHERE status = 'RUNNING' AND lease_until < now();
If a worker dies mid-step, another worker retries it. If the send had already happened, the messages unique constraint blocks a second send.
### 9.6 Quiet hours and stop rules
If run_at falls inside the business quiet window (default 21:00 to 09:00 in its timezone), move it to the next window start. PAID, CANCELLED, owner cancel, or an opt-out sets every PENDING step to CANCELLED.
### 9.7 Worker pool
N worker loops on virtual threads (default 8). Each claims, executes, and sleeps 250 ms with jitter when the queue is empty. Use an injectable Clock bean everywhere so tests can move time.
## 10. Payments
- Create a Razorpay test-mode payment link per due, or serve a simple pay page at /pay/[token] that starts checkout.
- The webhook endpoint verifies the HMAC signature with the webhook secret. A bad signature returns 400 or 401 with no state change.
- In one transaction: insert into payments (unique on provider_payment_id, so duplicate deliveries do nothing), lock the due, set PAID, cancel pending steps, publish an SSE event.
- Ignore events for unknown links safely, and always return 200 for valid duplicates.
- Say clearly in the README that this is test mode. Real money needs provider business verification.
## 11. Messaging, templates and compliance
- Channel interface: send(to, subject, body) returns a provider id. Implement EmailChannel (sandbox) now, stub SMS and WhatsApp for later.
- Templates: owner-approved once per policy step. Placeholders name, amount, due_date, pay_link, business_name and unsubscribe_link are filled by code.
- Safeguards: an unsubscribe link in every message (enforced server-side), quiet hours, a minimum gap, no threats or false legal claims (the final notice stays factual), and a per-business daily send cap.
- Privacy: store only what is needed, let owners delete a customer, and note DPDP awareness in the README.
## 12. AI layer (thin and safe)
| Feature | How it works | Guardrail |
| Template drafting | One LLM call turns tone, business name and placeholders into a short message. The owner previews and approves once. | Amount, date and pay link are inserted by code. Validate that required placeholders exist before saving. |
| CSV column mapping | The LLM suggests which header is name, amount or date. | The user confirms the mapping before import. |
| Chatbot | Two or three tools (list dues, summarize by customer, overdue this week) that call your own APIs. | Every tool call is schema-validated and scoped to the logged-in business. Read-only. Wrong calls never change data. |

Keep the key on the server, set per-user rate limits, and treat model output as untrusted data.
## 13. API surface
| Area | Endpoints |
| Auth | POST /api/auth/signup, POST /api/auth/login, GET /api/me |
| Business | GET and PUT /api/business |
| Customers | GET and POST /api/customers, PUT and DELETE /api/customers/{id} |
| Import | POST /api/import/preview, POST /api/import/commit |
| Dues | GET and POST /api/dues, GET /api/dues/{id}, POST /api/dues/{id}/mark-paid, POST /api/dues/{id}/cancel |
| Policies | GET and POST /api/policies, PUT and DELETE /api/policies/{id} |
| Dashboard | GET /api/dashboard |
| Realtime | GET /api/dues/{id}/stream (SSE) |
| Assistant | POST /api/assistant/draft-template, /map-columns, /ask |
| Public | GET /pay/{token}, POST /webhooks/razorpay, GET /unsubscribe/{token} |

Errors use one JSON shape: code, message, details.
## 14. Frontend
| Page | Contents |
| Landing | Animated timeline demo with the pitch |
| Dashboard | Collected, outstanding, overdue (animated counters), collections chart, upcoming reminders |
| Dues | Filters, status chips, bulk actions |
| Due detail | Live animated timeline of scheduled and sent steps, payment status, mark-paid button |
| Import | Drag-and-drop CSV, column mapping, preview with row errors, confirm |
| Policies | Vertical step builder with templates |
| Pay page | Public page with business name, amount and a Pay button |
| Settings | Business profile, quiet hours, sender name |

State: TanStack Query for server data and a small useEventSource hook that merges SSE events into the query cache. On connect, the server sends a snapshot so a refresh is never stale.
Design: dark-first tokens with a light mode, Geist and JetBrains Mono, shadcn/ui, motion animations of 150 to 400 ms, reduced-motion support, and a skeleton, empty and error state on every async view. The animated due timeline is the signature visual for the demo video.
## 15. Security
| Threat | Mitigation |
| Account abuse | BCrypt, short-lived JWTs, login rate limit |
| Cross-account access | Every query scoped by business_id, with isolation tests on every endpoint |
| Fake webhooks | HMAC signature check, replay-safe idempotent writes |
| Public endpoint abuse | Bucket4j rate limits on pay, unsubscribe and webhook routes, body size cap |
| Spam through the product | Sandbox email provider, daily caps, minimum gaps, opt-out |
| Secret leakage | Environment variables only, masked in logs, no secrets in the repo |
| Prompt injection | Model output is validated data and never executed. The chatbot is read-only. |
| Browser attacks | Strict CORS to the frontend origin, security headers, parameterized queries |

## 16. Observability
- Metrics: reminders_sent_total, steps_failed_total, step_retries_total, queue_depth, lease_expirations_total, webhook_duplicates_total, step duration timer.
- Logs: structured JSON with dueId, stepKey and attempt on every line.
- Health: /actuator/health for the deploy smoke test and an uptime monitor.
## 17. Testing plan
Use a real Postgres through Testcontainers (never H2), an injectable Clock, a FakeChannel that records sends and can fail, and a helper that signs valid and invalid webhook payloads.
| Test | What it proves |
| Duplicate sends | N workers drain thousands of steps with every step DONE and zero duplicate (due, step) pairs |
| Pay-versus-remind race | Worker and webhook released together over thousands of trials, with zero reminders after PAID |
| Crash recovery | Expired leases are requeued and finish, with no duplicate sends |
| Webhook idempotency | Repeated deliveries create one payment row and cancel remaining steps |
| Webhook security | Bad signatures, replays and unknown links cause no state change |
| Isolation | User A can never read or change user B's data |
| Retries | Fail twice then succeed gives three attempts with growing backoff, and permanent failure ends cleanly |
| Quiet hours and offsets | No send inside the quiet window, and run_at values ascend |
| Money math | Totals are exact to the paisa |

Performance (k6 and timing runs): queue drain with 1, 2, 4 and 8 workers, webhook endpoint at stepped request rates, dashboard reads with thousands of dues, and CSV import at several sizes. Run each three times, discard a warm-up, report the median, and record machine spec, worker count, data size, date and commit hash.
Resume numbers must be measured by you. Only write a figure you can back with a log, and keep raw outputs in docs/benchmarks/.
## 18. Deployment
GitHub push -> Actions: build, test (Testcontainers), image to GHCR -> SSH deployVPS (Docker Compose): caddy (auto HTTPS) -> api (Spring Boot + workers) -> postgres (volume)Frontend on Vercel or Netlify with NEXT_PUBLIC_API_URL set to the API domain
- Environment variables: DB credentials, JWT secret, Razorpay key and webhook secret, email provider key, LLM key, allowed CORS origin.
- Release safety: Flyway migrates on startup, a health check runs after deploy, and a failed check rolls back to the previous image tag.
- Backups: nightly pg_dump to object storage, and UptimeRobot on the health endpoint.
- Hosting: a small always-on VPS or Lightsail. Avoid hosts that sleep, because scheduled reminders need a running process. Check current pricing and free-tier limits before choosing.
- Razorpay webhook: point it at your public API URL (or use a tunnel during development).
## 19. Build plan: 10 days at 8 hours a day
| Day | Goal | Done when |
| 1 | Skeleton, auth, Flyway V1, CI, design system, early hello-world deploy | Sign up and log in work, CI is green |
| 2 | Business and customer CRUD, CSV import with validation and preview | A messy CSV imports with clear row errors |
| 3 | Dues, policies, step scheduling, claiming worker, email sandbox channel | A due schedules and its first reminder sends |
| 4 | Retries, lease reaper, quiet hours, stop rules, idempotent sends | Killing a worker mid-step still ends with one send |
| 5 | Test day: duplicates, race, crash, webhook, isolation tests | All correctness tests pass in CI |
| 6 | Dashboard, dues list, due detail with animated timeline | Timeline shows scheduled and sent steps |
| 7 | Pay link, pay page, verified webhook, SSE live updates | A test payment flips the due to PAID live |
| 8 | Deploy, landing page, rate limits, demo seed data | A public URL works for a stranger |
| 9 | AI drafting, column mapping, chatbot, k6 benchmarks | Benchmarks logged in docs/benchmarks |
| 10 | README, diagram, demo video, polish, optional pilot | Everything linked on the resume |

If you fall behind, cut in this order: chatbot, then polish, then CSV mapping AI. Never cut tests or deployment. Freeze scope after Day 5.
## 20. Working with Claude Code
Create a CLAUDE.md first. Suggested rules:
- Plan first, then implement. Flyway migrations only (no ddl-auto update). Constructor injection and DTOs at API boundaries.
- Every feature gets tests, and mvn test must pass before a task is called done.
- No secrets in code. No Kafka, no multi-tenancy, no extra channels in the MVP.
- Engine invariants: delivery is at-least-once, UNIQUE (due_id, step_key) on due_steps, UNIQUE (due_id, step_key, channel) on messages, and workers claim with SKIP LOCKED plus a lease.
- Frontend: shadcn/ui, motion library, reduced-motion support, and loading, empty and error states everywhere.
Work in small steps, review every diff, and commit after each working step. Hand-write the claim, lease and pay-versus-remind logic yourself so you can explain it. Let Claude Code generate DTOs, CRUD and UI scaffolding.
## 21. Resume mapping and interview questions
| Resume claim | Where it comes from |
| Validated scheduled steps with duplicate prevention and crash recovery | Sections 9 and 17 |
| Queue throughput across 1 and 4 workers | Worker pool and the drain benchmark |
| Webhook load and p95 latency | Section 10 with k6 |
| Chatbot tool-selection accuracy and AI quality accuracy | Section 12 with a labeled test set |
| JaCoCo coverage, CI and deploy time | Sections 17 and 18 |

Questions to prepare:
- Why Postgres as a queue instead of Kafka, and when would you switch?
- How does SKIP LOCKED work, and what does the lease protect against?
- A customer pays at the same moment a reminder goes out. What happens?
- How do you guarantee a reminder is never sent twice after a crash?
- How do you verify a webhook, and what if it is delivered five times?
- Why integer paise and a policy snapshot on each due?
- How do you stop the AI from writing a wrong amount in a payment message?
- What would you change to serve 100 times the load?
## 22. Risks and future work
- Crowded space: differentiate by niche, reliability and a real pilot.
- Provider limits: WhatsApp and SMS need approvals, so they stay behind the Channel interface.
- Legal and tone: keep messages factual and respect opt-outs and local rules.
- Scope creep: the MVP is the engine, the policy, the import, and the dashboard.
- Future work: partial payments, WhatsApp and SMS, multi-tenancy with row-level security, Kafka or an outbox pattern at higher volume, per-customer reminder preferences, and a reporting export.
