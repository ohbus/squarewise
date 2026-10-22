-- Drop high-throughput foreign key constraints on writer-intensive tables
-- to eliminate row-level lock contention on parent tables (expense_groups, expenses, settlements)
-- under 10M DAU concurrent write loads.
-- Referential integrity is strictly validated in the application layer via EntityReferenceValidator.
-- Foreign key column indexes are preserved and ensured for O(1) query performance.

-- 1. Balance postings (highest write throughput ledger table)
ALTER TABLE balance_postings DROP CONSTRAINT IF EXISTS balance_postings_group_id_fkey;
ALTER TABLE balance_postings DROP CONSTRAINT IF EXISTS balance_postings_expense_id_fkey;
ALTER TABLE balance_postings DROP CONSTRAINT IF EXISTS balance_postings_settlement_id_fkey;
CREATE INDEX IF NOT EXISTS balance_postings_expense_idx ON balance_postings (expense_id);

-- 2. Expense payers and allocations (multi-row inserts per expense transaction)
ALTER TABLE expense_payers DROP CONSTRAINT IF EXISTS expense_payers_expense_id_fkey;
ALTER TABLE expense_allocations DROP CONSTRAINT IF EXISTS expense_allocations_expense_id_fkey;

-- 3. Expense idempotency claims
ALTER TABLE expense_idempotency DROP CONSTRAINT IF EXISTS expense_idempotency_group_id_fkey;
ALTER TABLE expense_idempotency DROP CONSTRAINT IF EXISTS expense_idempotency_expense_id_fkey;

-- 4. Recurring expense occurrences
ALTER TABLE recurring_expense_occurrences DROP CONSTRAINT IF EXISTS recurring_expense_occurrences_schedule_id_fkey;
ALTER TABLE recurring_expense_occurrences DROP CONSTRAINT IF EXISTS recurring_expense_occurrences_expense_id_fkey;
CREATE INDEX IF NOT EXISTS idx_recurring_occurrences_expense ON recurring_expense_occurrences (expense_id);
