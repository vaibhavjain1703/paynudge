-- A business's policies are picked by name in the UI, and the starter policies are created by name on demand.
ALTER TABLE policies ADD CONSTRAINT uq_policies_business_name UNIQUE (business_id, name);
