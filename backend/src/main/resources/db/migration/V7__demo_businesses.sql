-- Demo businesses come from "Try the demo" on the landing page: an owner with no way to sign in again, a business
-- full of sample data, deleted a day later. Their owner notices are kept, never emailed.
ALTER TABLE businesses ADD COLUMN demo BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_businesses_demo_created ON businesses (created_at) WHERE demo;
