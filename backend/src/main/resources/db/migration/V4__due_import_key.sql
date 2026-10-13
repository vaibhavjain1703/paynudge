-- A fingerprint of the CSV row a due came from (name, contact, amount, due date). Importing the same file again
-- finds every row already imported, so nobody gets reminded twice. Dues created by hand have none.
ALTER TABLE dues ADD COLUMN import_key VARCHAR(64);
CREATE UNIQUE INDEX uq_dues_import_key ON dues (business_id, import_key) WHERE import_key IS NOT NULL;
