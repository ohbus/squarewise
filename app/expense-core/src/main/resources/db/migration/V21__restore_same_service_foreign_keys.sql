-- Re-establish durable same-service referential integrity after the historical
-- high-throughput constraint experiment. PostgreSQL validates each constraint
-- during creation, so an existing orphan fails the migration before rollout.
ALTER TABLE group_memberships
    ADD CONSTRAINT group_memberships_group_id_fkey
    FOREIGN KEY (group_id) REFERENCES expense_groups (group_id) ON DELETE CASCADE;
ALTER TABLE group_invitations
    ADD CONSTRAINT group_invitations_group_id_fkey
    FOREIGN KEY (group_id) REFERENCES expense_groups (group_id) ON DELETE CASCADE;
ALTER TABLE group_invitations
    ADD CONSTRAINT group_invitations_placeholder_id_fkey
    FOREIGN KEY (placeholder_id) REFERENCES group_memberships (membership_id) ON DELETE SET NULL;
ALTER TABLE settlements
    ADD CONSTRAINT settlements_group_id_fkey
    FOREIGN KEY (group_id) REFERENCES expense_groups (group_id);
ALTER TABLE group_audit
    ADD CONSTRAINT group_audit_group_id_fkey
    FOREIGN KEY (group_id) REFERENCES expense_groups (group_id);
ALTER TABLE expenses
    ADD CONSTRAINT expenses_group_id_fkey
    FOREIGN KEY (group_id) REFERENCES expense_groups (group_id) ON DELETE CASCADE;
ALTER TABLE expense_payers
    ADD CONSTRAINT expense_payers_expense_id_fkey
    FOREIGN KEY (expense_id) REFERENCES expenses (expense_id) ON DELETE CASCADE;
ALTER TABLE expense_allocations
    ADD CONSTRAINT expense_allocations_expense_id_fkey
    FOREIGN KEY (expense_id) REFERENCES expenses (expense_id) ON DELETE CASCADE;
ALTER TABLE balance_postings
    ADD CONSTRAINT balance_postings_group_id_fkey
    FOREIGN KEY (group_id) REFERENCES expense_groups (group_id) ON DELETE CASCADE;
ALTER TABLE balance_postings
    ADD CONSTRAINT balance_postings_expense_id_fkey
    FOREIGN KEY (expense_id) REFERENCES expenses (expense_id) ON DELETE CASCADE;
ALTER TABLE balance_postings
    ADD CONSTRAINT balance_postings_settlement_id_fkey
    FOREIGN KEY (settlement_id) REFERENCES settlements (settlement_id) ON DELETE CASCADE;
ALTER TABLE expense_outbox
    ADD CONSTRAINT expense_outbox_group_id_fkey
    FOREIGN KEY (group_id) REFERENCES expense_groups (group_id);
ALTER TABLE recurring_expense_schedules
    ADD CONSTRAINT recurring_expense_schedules_group_id_fkey
    FOREIGN KEY (group_id) REFERENCES expense_groups (group_id) ON DELETE CASCADE;
ALTER TABLE recurring_expense_occurrences
    ADD CONSTRAINT recurring_expense_occurrences_schedule_id_fkey
    FOREIGN KEY (schedule_id) REFERENCES recurring_expense_schedules (schedule_id) ON DELETE CASCADE;
ALTER TABLE recurring_expense_occurrences
    ADD CONSTRAINT recurring_expense_occurrences_expense_id_fkey
    FOREIGN KEY (expense_id) REFERENCES expenses (expense_id) ON DELETE SET NULL;
ALTER TABLE expense_idempotency
    ADD CONSTRAINT expense_idempotency_group_id_fkey
    FOREIGN KEY (group_id) REFERENCES expense_groups (group_id) ON DELETE CASCADE;
ALTER TABLE expense_idempotency
    ADD CONSTRAINT expense_idempotency_expense_id_fkey
    FOREIGN KEY (expense_id) REFERENCES expenses (expense_id) ON DELETE CASCADE;
