-- Who holds a RUNNING step: set when a worker claims it and cleared when the step moves on. Every later write checks
-- it, so a worker whose lease was handed to another can never finish, fail or postpone the step.
ALTER TABLE due_steps ADD COLUMN lease_token UUID;

-- Why a step is waiting (quiet hours, the daily limit, the gap between emails), for the due's timeline.
ALTER TABLE due_steps ADD COLUMN note VARCHAR(200);

-- When the customer used the unsubscribe link.
ALTER TABLE customers ADD COLUMN opted_out_at TIMESTAMPTZ;

-- Customer emails per business per local day. A worker reserves a slot here before each send (and gives it back if
-- nothing went out), so parallel workers can never pass the business's daily cap.
CREATE TABLE daily_sends (
    business_id BIGINT NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    day         DATE   NOT NULL,
    sent        INT    NOT NULL CHECK (sent >= 0),
    PRIMARY KEY (business_id, day)
);
