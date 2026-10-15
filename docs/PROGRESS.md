# Progress log

Build plan: `docs/DESIGN.md` section 19. Newest entries at the bottom of each day.

## Day 1: skeleton, auth, Flyway V1, CI, design system, hello-world deploy

Done when: sign up and log in work, CI is green.

- [x] Spring Boot 4.1 scaffold builds; tests run against Postgres 16 via Testcontainers
- [x] Flyway V1 schema, env-based config, `Clock` bean, JSON error shape (schema invariants pinned by `SchemaConstraintsTest`)
- [x] Auth: signup, login, `GET /api/me` (BCrypt, 2h HS256 JWT, owner-scoped lookups, CORS). Login rate limit deferred to Day 8 with the other Bucket4j limits
- [x] CI workflow (backend `mvn verify`, frontend lint/typecheck/build): green on the first run (PR #1, public repo github.com/vaibhavjain1703/paynudge)
- [x] Frontend scaffold: Next.js 16.3.8, Tailwind 4, shadcn/ui (Radix, Nova), dark-first tokens, login/signup/dashboard shell; 12 Playwright e2e checks (desktop + mobile)
- [ ] Hello-world deploy (API on Render, Postgres on Supabase, frontend on Vercel)

### Notes

- Next pinned to 16.3.8: 16.0.0 to 16.3.7 carry published advisories (image-optimizer SSRF, ISR cache poisoning).
  `npm audit --omit=dev` is clean. Five remaining dev-only findings sit in ESLint's `braces` chain with no upstream fix.
- `npm run e2e` runs on its own ports (API 8180, web 3100, build dir `.next-e2e`) because Playwright's output folder
  triggers dev-server Fast Refresh reloads that drop in-flight form submissions.
- Deploy credentials (Supabase, Render, Vercel) are in `secrets.local.env` and verified; deploy itself still pending.
- Motion pass: landing timeline demo (the signature visual, ahead of Day 8), staggered entrances, count-up numbers,
  animated form errors with a shake on rejected submits, theme crossfade, bell "nudge" on the logo. Everything
  honours `prefers-reduced-motion` (the demo shows its finished state instead of looping).
- `mvn spring-boot:run` uses the full JIT (`optimizedLaunch=false`); with C1 only, BCrypt was slow enough to time out
  e2e sign-ups. The e2e suite also warms both servers in `e2e/global-setup.ts` before running.
- Postgres moved from 16 to 17 everywhere (docker-compose, Testcontainers) to match Supabase, which runs 17.11.
- Passwordless sign-in: 6-digit email codes (10 min, single use, 5 guesses, 60 s resend, 5/hour, HMAC-stored) and
  "Continue with Google" (ID token verified against Google's keys: issuer, audience, expiry, verified email). One
  email is one account across password, code and Google. Local mail goes to Mailpit (docker-compose `mail`).
  Render's free plan blocks outbound SMTP (since Sept 2025), so real mail goes through Brevo's HTTPS API
  (`MAIL_PROVIDER=brevo`, free 300/day). A Gmail sender cannot be DKIM-authenticated by Brevo, so it rewrites the
  sender domain and early mails may land in spam; a custom domain fixes that later. e2e always forces Mailpit.
- Google sign-in runs in Testing mode (only listed test users) until deploy: publishing needs a home page and a privacy
  policy on the live domain. Add the Vercel URL to the client's JavaScript origins then.

## Day 2: business and customer CRUD, CSV import with validation and preview

Done when: a messy CSV imports with clear row errors.

- [x] Business settings: `GET`/`PUT /api/business` (name, sender name, timezone, quiet hours that may cross midnight,
  daily cap 1 to 500) and a Settings page; tab bar in the app shell
- [x] Customers: `GET` (search, paging), `POST`, `PUT`/`DELETE /api/customers/{id}`; one rule set (`CustomerFields`)
  for names, emails and phones (`+91` for bare 10-digit mobiles), one email per customer per business, delete
  cascades to dues; Customers page with add/edit dialog and delete confirmation
- [x] CSV import: `POST /api/import/preview` (match columns, check every row, save nothing) and `/commit` (same checks
  on the server, import valid rows, re-import creates no duplicates); Import page with drag and drop, column picker,
  row errors in the cell that is wrong, "only problems" filter and a downloadable sample file
- [x] Isolation tests on every new endpoint; Playwright covers settings, customers and import

### Notes

- Import creates customers only. Amount and due date are checked (exact paise, day-first dates) but dues need a
  reminder policy and `policy_snapshot`, so the same commit creates dues on Day 3.
- Jakarta Mail pins virtual-thread carriers on JDK 21 (`jdk.tracePinnedThreads` showed `SMTPTransport`), which
  stalled the API under parallel sign-in codes. SMTP sends now run on a small platform-thread pool.
- Messy-file handling lives in `CsvReader` (byte order mark, Windows-1252, `;`/tab separators, `sep=` line, Excel
  workbook detection, 5,000-row and 2 MB limits); `backend/src/test/resources/csv/messy-dues.csv` is the reference.

## Day 3: dues, policies, step scheduling, claiming worker, email sandbox channel

Done when: a due schedules and its first reminder sends.

- [x] Policies: three read-only starters (Friendly then firm, Gentle, Firm) plus owner policies through
  `GET`/`POST`/`PUT`/`DELETE /api/policies`; rules: 1 to 8 steps in date order, a day between customer emails, every
  email names {name}, {amount} and {pay_link}; read-only Policies page
- [x] Dues: `POST`/`GET /api/dues`, `GET /api/dues/{id}`; a due copies its policy (`policy_snapshot`) and gets every
  step in one transaction, at 10:00 business time, moved out of quiet hours; Dues page and dashboard summary
- [x] Import commit takes a policy and creates the dues; a row fingerprint (`dues.import_key`, V4) makes re-imports
  and repeated rows skip, so nobody is reminded twice
- [x] Import page keeps the file being checked (preview and chosen policy) while the owner visits other pages; it is
  held in memory only, so importing, logging out or reloading lets it go
- [x] Engine: worker loops on virtual threads claim with `FOR UPDATE SKIP LOCKED` and a 60 s lease, lock the due,
  skip paid/cancelled/opted-out dues, record the message (unique per due, step, channel) and send through the
  `Channel` (EmailChannel, its own sandbox SMTP); NOTIFY_OWNER emails the owner and escalates the due
- [x] e2e: an imported overdue due's first reminder reaches the customer in Mailpit with pay and unsubscribe links

### Notes

- A due created late skips steps whose time has passed, except the latest customer email, which goes out now
  (after quiet hours) unless the next email is under a day away. Starter wording ("due on {due_date}") stays true
  when sent late.
- Failed sends roll back (message row included) and retry after 30 s, 1, 2, 4 min, then FAILED. Day 4 adds the
  lease reaper, permanent-vs-transient errors, runtime quiet hours, minimum gap and daily cap.
- Customer reminders never reach real customers: Mailpit locally; production needs a sandbox decision at deploy
  (Mailtrap's free sandbox is ~50 emails/month; alternative: deliver every reminder to the owner's own inbox).
- e2e runs two browsers: four were no faster on this 8 GB machine and once pushed Docker Desktop over.

## Day 4: retries, lease reaper, quiet hours, stop rules, idempotent sends

Done when: killing a worker mid-step still ends with one send.

- [x] Crash safety: a claim leases the step under a fresh token (`due_steps.lease_token`, V5) and the executor holds
  the step's row lock for the whole run, so a slow send is never taken over and a late worker cannot touch a step it
  lost; a reaper (every 15 s) hands back steps whose worker died, and fails a step that kills every worker after five
  tries
- [x] Retries: provider outages retry after 30 s, 1, 2, 4 min plus up to 10% jitter; an address the provider refuses
  fails at once; a failed step flags the due "Needs you" (ESCALATED)
- [x] Checked just before each customer email: quiet hours as the business has them now, a day since the customer's
  last email across all their dues, and the daily cap (`daily_sends`, reserved atomically so parallel workers never
  pass it). A step that has to wait is postponed with a note; waiting is not a try
- [x] Stop rules: `POST /api/dues/{id}/mark-paid` and `/cancel`, public `GET`/`POST /api/public/unsubscribe/{token}`
  (also the one-click target of the List-Unsubscribe header). Each takes the lock a sending worker holds (due or
  customer row), so a stop that lands mid-send waits for it and cancels the rest
- [x] Emails carry a Message-ID fixed per step, `List-Unsubscribe` and `List-Unsubscribe-Post` (RFC 8058)
- [x] Dues page: mark paid and stop reminders (with confirmation), "Unsubscribed" status; public unsubscribe page
- [x] Tests: CrashRecoveryTest terminates a worker's database session mid-send and the customer still gets exactly
  one email; slow sends past the lease, stale leases, poison steps; SendSafeguardsTest (quiet hours, gap, cap with
  four workers); StopRulesTest (pay or unsubscribe mid-send); e2e unsubscribe from the email link and dues actions

### Notes

- One duplicate cannot be ruled out with SMTP: a worker that dies after the mail server accepted the email but before
  its transaction commits. The retry sends the same Message-ID, which Gmail and most mail services collapse.
- Fixed a Day 1 bug: `hibernate.jdbc.time_zone=UTC` shifted `TIME` columns by the JVM's offset, so on an IST machine
  quiet hours were stored 5 h 30 early (the app read them back shifted, so it looked right). Removed; tests now run
  with the JVM on India time (surefire `-Duser.timezone=Asia/Kolkata`) so CI, which is on UTC, catches this kind of
  drift.
- New setting `API_URL` (the API's public address, for one-click unsubscribe links). Defaults to
  `http://localhost:8080`; set it at deploy.
- Owner email digests for failed steps are left for later; failures show in the app as "Needs you".
