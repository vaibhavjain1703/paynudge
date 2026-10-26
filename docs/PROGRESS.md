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

## Day 5: test day: duplicates, race, crash, webhook, isolation

Done when: all correctness tests pass in CI.

- [x] Payment webhook (`POST /webhooks/razorpay`), built now so its tests could be: HMAC-SHA256 signature over the raw
  body checked first (constant time), then `payment_link.paid` finds its due by the pay token in the link's notes,
  locks the due, records the payment (unique on the provider's payment id) and settles the due. Pay links, the pay
  page and live updates stay on Day 7
- [x] Duplicate sends: 8 workers drain 2,500 steps; every step DONE on its first try, every reminder sent once, in
  policy order (`DuplicateSendsTest`)
- [x] Pay versus remind: 2,000 trials racing a worker against the webhook; an outside connection checks the due at the
  instant of every send; zero reminders after PAID, every trial ends paid with nothing waiting, both orders occur
  (`PayVersusRemindRaceTest`)
- [x] Crash recovery: Day 4's `CrashRecoveryTest` (killed mid-send, slow sends past the lease, poison steps)
- [x] Webhook idempotency and security: redeliveries, eight simultaneous copies, forged, unsigned and altered events,
  unknown links, other events, underpaid and cancelled dues (`PaymentWebhookTest`, `WebhookSignatureTest`)
- [x] Isolation: `OwnerIsolationTest` lists every endpoint the app serves and fails if one has neither a check (one
  owner against another's data) nor a stated reason it holds no business's data
- [x] Retries: two failures then success take three tries with growing waits; permanent failures end at once
- [x] Quiet hours and offsets: 10,000 random policies, dates, windows and zones (DST, half- and quarter-hour offsets)
  (`DueSchedulePropertyTest`)
- [x] Money: 100,000 random amounts read back exact to the paisa (`MoneyExactnessTest`)

### Notes

- In the race test, a worker that still has to claim the step usually loses (the webhook takes the due's lock in its
  first query), so half the trials claim the step first and give the payment a random head start of up to 3 ms.
  Locally the split was 525 reminder-first and 1,475 payment-first. Order is not what is tested; that a paid customer
  is never reminded is.
- New setting `RAZORPAY_WEBHOOK_SECRET`; until it is set, every webhook is refused (Day 7 sets it up).

## Day 6: dashboard, dues list, due detail with animated timeline

Done when: the timeline shows scheduled and sent steps.

- [x] `GET /api/dashboard`: collected this month, outstanding, overdue (sums and counts), needs-you, twelve weeks of
  collections and the next five reminders, all on the business's own calendar (a payment at 00:30 on 1 January in
  Kolkata counts in January)
- [x] Dashboard page: tiles that count up and open the matching dues filter; a collections chart (one series in the
  validated `--chart-1` hue, the tallest bar labelled, every bar's value on hover or focus, a table view); "Coming up"
- [x] Dues: `filter` (all, open, needs-you, overdue, paid, cancelled) and `q` search on `GET /api/dues`, with the count
  behind each chip; `POST /api/dues/bulk` (up to 100, locked in id order); chips, search, row selection and a bulk bar
- [x] Due detail (`/dues/[id]`): the timeline. Each step with its day and tone (from the due's own policy copy), the
  email's subject and send time, skipped, scheduled, cancelled and failed states with reasons, the payment where it
  landed and a "Now" marker; mark paid and stop reminders from the page; refreshes every 15 s while open
- [x] e2e: an imported overdue due's timeline shows the sent reminder and the scheduled ones, then cancels the rest
  when marked paid, and the dashboard counts it collected; filters, search and bulk mark-paid

### Notes

- The detail page polls until Day 7's live updates (SSE) replace it.
- Chart colour checked with the dataviz palette validator: `--chart-1` passes on the light card; in dark mode its
  step moved from L 0.78 to 0.66 to sit in the band.

## Day 7: pay link, pay page, verified webhook, SSE live updates

Done when: a test payment flips the due to PAID live.

- [x] Pay page (`/pay/[token]`, public): business, the customer's first name, amount and due date, and "Pay"; paid and
  cancelled states; a broken link says so
- [x] Payment links: `POST /api/public/pay/{token}/link` makes a Razorpay test-mode link on the first click (full
  amount, Razorpay's own SMS/email/WhatsApp off, the pay token in its notes, back to the pay page, 7 days) and reuses
  it until a day is left (V6 stores it on the due)
- [x] The return trip: Razorpay's signed result (HMAC with the key secret) is checked, the payment is read from
  Razorpay and recorded through the webhook's path, so either may arrive second. Payments therefore work locally,
  where Razorpay cannot reach the webhook
- [x] Live updates: `GET /api/events` (server-sent events per business, read with fetch so the token stays in a
  header). Committed changes publish "due" (the due as its page shows it, merged into the query cache) or "dues"
  (refetch); every connect starts with "ready" so a reconnect is never stale. The due page's polling is gone
- [x] e2e: the owner's open due page turns PAID by itself when a signed webhook lands, and the customer's pay page
  then says thank you. Checked by hand against Razorpay's real test API: link created and reused as above

### Notes

- `RAZORPAY_WEBHOOK_SECRET` is generated in `secrets.local.env`; on Day 8, with a public API URL, add the webhook in
  Razorpay (Test Mode, Webhooks, event `payment_link.paid`) with that secret.
- One API process holds the live streams; more than one would need Postgres LISTEN/NOTIFY between them.

## Between Days 7 and 8: add a due by hand on the import page

- [x] Import page: "Upload a CSV" or "Add manually" tabs, one reminder policy choice for both. The manual form takes a
  customer (name, email, phone) and a due (amount in rupees as typed, due date)
- [x] `POST /api/import/manual`: the CSV row rules in one call. Same checks and messages, the same customer when the
  email (or, without one, the phone) is already in the list, who keeps their own details, and the same fingerprint, so
  the same due typed twice, or typed after it was imported (or imported after it was typed), is refused with
  `DUE_EXISTS` and a link to the one already there
- [x] The new due reaches open dues pages live ("dues" event); what is typed survives a visit to another page, in
  memory only, like a file being checked
- [x] e2e: a due added by hand shows in Dues and Customers, a second try links to the first; an empty form names every
  missing field; a half-typed due is still there after visiting Dues
