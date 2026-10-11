ALTER TABLE recurring_expense_schedules
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';
