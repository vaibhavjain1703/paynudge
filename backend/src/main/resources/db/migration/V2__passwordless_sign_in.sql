-- Owners who sign in with an email code or Google have no password.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- One-time sign-in codes. Only a keyed HMAC of each code is stored, never the code itself.
-- Only the newest code per email is ever honoured; older rows remain as the audit trail and for rate limits.
CREATE TABLE login_codes (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    code_hash   VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_login_codes_email_created ON login_codes (email, created_at DESC);
