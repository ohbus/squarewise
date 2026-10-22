-- Drop foreign key constraint on expenses referencing expense_groups
-- to eliminate shared row-lock contention on expense_groups during high-throughput expense inserts.
-- Application-level referential integrity is enforced via EntityReferenceValidator.
-- The existing index expenses_group_idx (group_id, created_at) is preserved for fast lookups.

ALTER TABLE expenses DROP CONSTRAINT IF EXISTS expenses_group_id_fkey;

-- Add covering index for balance calculations to allow index-only scans
-- without accessing the table heap during balance aggregation queries.
CREATE INDEX IF NOT EXISTS idx_balance_postings_covering
    ON balance_postings (group_id, participant_id, currency, amount_minor);
