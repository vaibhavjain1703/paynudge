-- PayNudge initial schema (docs/DESIGN.md section 7).
-- Money is integer paise. Timestamps are TIMESTAMPTZ and always written from the application Clock.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- One business per owner in the MVP.
CREATE TABLE businesses (
    id             BIGSERIAL PRIMARY KEY,
    owner_id       BIGINT       NOT NULL UNIQUE REFERENCES users (id),
    name           VARCHAR(200) NOT NULL,
    timezone       VARCHAR(50)  NOT NULL DEFAULT 'Asia/Kolkata',
    quiet_start    TIME         NOT NULL DEFAULT '21:00',
    quiet_end      TIME         NOT NULL DEFAULT '09:00',
    sender_name    VARCHAR(100),
    daily_send_cap INT          NOT NULL DEFAULT 500 CHECK (daily_send_cap > 0),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- UNIQUE (business_id, email) also serves as the customers (business_id) index.
CREATE TABLE customers (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT       NOT NULL REFERENCES businesses (id),
    name        VARCHAR(200) NOT NULL,
    email       VARCHAR(255),
    phone       VARCHAR(20),
    opted_out   BOOLEAN      NOT NULL DEFAULT false,
    unsub_token VARCHAR(64)  NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (business_id, email),
    CHECK (email IS NOT NULL OR phone IS NOT NULL)
);

CREATE TABLE policies (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT       NOT NULL REFERENCES businesses (id),
    name        VARCHAR(100) NOT NULL,
    definition  JSONB        NOT NULL,
    is_template BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_policies_business ON policies (business_id);

-- policy_snapshot is the policy as it was when the due was created; later policy edits never touch it.
-- Deleting a customer (privacy request) removes their dues and everything hanging off them.
CREATE TABLE dues (
    id              BIGSERIAL PRIMARY KEY,
    business_id     BIGINT      NOT NULL REFERENCES businesses (id),
    customer_id     BIGINT      NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    policy_id       BIGINT      REFERENCES policies (id) ON DELETE SET NULL,
    amount_paise    BIGINT      NOT NULL CHECK (amount_paise > 0),
    due_date        DATE        NOT NULL,
    status          VARCHAR(20) NOT NULL
        CHECK (status IN ('SCHEDULED', 'ACTIVE', 'PAID', 'CANCELLED', 'ESCALATED')),
    pay_token       VARCHAR(64) NOT NULL UNIQUE,
    policy_snapshot JSONB       NOT NULL,
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dues_business_status_date ON dues (business_id, status, due_date);
CREATE INDEX idx_dues_customer ON dues (customer_id);

-- The queue. A step is a row with a future run_at; workers claim with FOR UPDATE SKIP LOCKED plus a lease.
CREATE TABLE due_steps (
    id          BIGSERIAL PRIMARY KEY,
    due_id      BIGINT      NOT NULL REFERENCES dues (id) ON DELETE CASCADE,
    step_key    VARCHAR(64) NOT NULL,
    action      VARCHAR(30) NOT NULL,
    status      VARCHAR(20) NOT NULL
        CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED', 'CANCELLED', 'SKIPPED')),
    run_at      TIMESTAMPTZ NOT NULL,
    attempt     INT         NOT NULL DEFAULT 0 CHECK (attempt >= 0),
    lease_until TIMESTAMPTZ,
    error       TEXT,
    UNIQUE (due_id, step_key),
    CHECK (status <> 'RUNNING' OR lease_until IS NOT NULL)
);

-- Claim query: oldest runnable PENDING step. Reaper query: RUNNING steps whose lease expired.
CREATE INDEX idx_steps_claim ON due_steps (run_at) WHERE status = 'PENDING';
CREATE INDEX idx_steps_lease ON due_steps (lease_until) WHERE status = 'RUNNING';

-- One row per actual send. The unique key is what makes at-least-once delivery never send twice.
CREATE TABLE messages (
    id          BIGSERIAL PRIMARY KEY,
    due_id      BIGINT       NOT NULL REFERENCES dues (id) ON DELETE CASCADE,
    step_key    VARCHAR(64)  NOT NULL,
    channel     VARCHAR(20)  NOT NULL,
    subject     VARCHAR(200),
    body        TEXT         NOT NULL,
    provider_id VARCHAR(100),
    sent_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (due_id, step_key, channel)
);

-- provider_payment_id is unique so a webhook delivered five times records one payment.
CREATE TABLE payments (
    id                  BIGSERIAL PRIMARY KEY,
    due_id              BIGINT       NOT NULL REFERENCES dues (id) ON DELETE CASCADE,
    provider_payment_id VARCHAR(100) NOT NULL UNIQUE,
    amount_paise        BIGINT       NOT NULL CHECK (amount_paise > 0),
    paid_at             TIMESTAMPTZ  NOT NULL,
    method              VARCHAR(30)
);

CREATE INDEX idx_payments_due ON payments (due_id);
