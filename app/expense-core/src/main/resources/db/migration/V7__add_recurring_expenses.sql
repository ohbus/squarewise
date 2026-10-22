CREATE TABLE recurring_expense_schedules (
    schedule_id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES expense_groups (group_id) ON DELETE CASCADE,
    description VARCHAR(240) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    frequency VARCHAR(16) NOT NULL,
    day_of_month INT,
    start_date DATE NOT NULL,
    end_date DATE,
    next_occurrence_date DATE NOT NULL,
    paused BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 1
);

CREATE TABLE recurring_expense_occurrences (
    occurrence_id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL REFERENCES recurring_expense_schedules (schedule_id) ON DELETE CASCADE,
    occurrence_date DATE NOT NULL,
    expense_id UUID REFERENCES expenses (expense_id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_recurring_occurrences_schedule_date UNIQUE (schedule_id, occurrence_date)
);

CREATE INDEX idx_recurring_schedules_due ON recurring_expense_schedules (paused, next_occurrence_date);
CREATE INDEX idx_recurring_schedules_group ON recurring_expense_schedules (group_id);
CREATE INDEX idx_recurring_occurrences_schedule ON recurring_expense_occurrences (schedule_id);
