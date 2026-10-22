CREATE TABLE expense_idempotency (
    idempotency_id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES expense_groups (group_id) ON DELETE CASCADE,
    actor_subject VARCHAR(255) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    expense_id UUID NOT NULL REFERENCES expenses (expense_id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT expense_idempotency_scope_unique
        UNIQUE (group_id, actor_subject, operation, idempotency_key)
);

CREATE INDEX expense_idempotency_expense_idx ON expense_idempotency (expense_id);
