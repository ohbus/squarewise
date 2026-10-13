-- Drop remaining foreign key constraints on group_memberships, group_invitations, and recurring_expense_schedules
-- referencing expense_groups and group_memberships.
-- Eliminates shared row lock contention on expense_groups during concurrent group member additions,
-- invitations, and recurring schedule setups under 10M DAU.
-- Referential integrity is strictly validated in the application layer via EntityReferenceValidator.

-- 1. Drop foreign key on group_memberships referencing expense_groups
ALTER TABLE group_memberships DROP CONSTRAINT IF EXISTS group_memberships_group_id_fkey;

-- 2. Drop foreign keys on group_invitations referencing expense_groups and group_memberships
ALTER TABLE group_invitations DROP CONSTRAINT IF EXISTS group_invitations_group_id_fkey;
ALTER TABLE group_invitations DROP CONSTRAINT IF EXISTS group_invitations_placeholder_id_fkey;

-- 3. Drop foreign key on recurring_expense_schedules referencing expense_groups
ALTER TABLE recurring_expense_schedules DROP CONSTRAINT IF EXISTS recurring_expense_schedules_group_id_fkey;

-- 4. Add composite indexes on group_memberships for fast status filtering and member lookup
CREATE INDEX IF NOT EXISTS idx_group_memberships_group_status
    ON group_memberships (group_id, status);

CREATE INDEX IF NOT EXISTS idx_group_memberships_lookup
    ON group_memberships (group_id, membership_id, status);
