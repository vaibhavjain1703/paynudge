-- The Razorpay payment link a due's pay page sends the customer to. Made on the first click of "Pay" and reused
-- until it nears its expiry, so a customer who clicks twice pays through the same link.
ALTER TABLE dues ADD COLUMN pay_link_id VARCHAR(64);
ALTER TABLE dues ADD COLUMN pay_link_url VARCHAR(255);
ALTER TABLE dues ADD COLUMN pay_link_expires_at TIMESTAMPTZ;
