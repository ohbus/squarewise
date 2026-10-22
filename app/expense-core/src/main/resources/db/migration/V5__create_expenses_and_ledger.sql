CREATE TABLE expenses (
    expense_id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES expense_groups (group_id) ON DELETE CASCADE,
    description VARCHAR(240) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'other',
    currency VARCHAR(3) NOT NULL,
    amount_minor BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    allocation_mode VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT expenses_positive_amount CHECK (amount_minor > 0),
    CONSTRAINT expenses_description_not_blank CHECK (TRIM(description) <> ''),
    CONSTRAINT expenses_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT expenses_version_positive CHECK (version >= 1)
);

CREATE INDEX expenses_group_idx ON expenses (group_id, created_at);

CREATE TABLE expense_payers (
    payer_id UUID PRIMARY KEY,
    expense_id UUID NOT NULL REFERENCES expenses (expense_id) ON DELETE CASCADE,
    participant_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    CONSTRAINT expense_payers_positive_amount CHECK (amount_minor > 0),
    CONSTRAINT uk_expense_payers_expense_participant UNIQUE (expense_id, participant_id)
);

CREATE INDEX expense_payers_expense_idx ON expense_payers (expense_id);

CREATE TABLE expense_allocations (
    allocation_id UUID PRIMARY KEY,
    expense_id UUID NOT NULL REFERENCES expenses (expense_id) ON DELETE CASCADE,
    participant_id UUID NOT NULL,
    allocated_minor BIGINT NOT NULL,
    CONSTRAINT expense_allocations_nonnegative_amount CHECK (allocated_minor >= 0),
    CONSTRAINT uk_expense_allocations_expense_participant UNIQUE (expense_id, participant_id)
);

CREATE INDEX expense_allocations_expense_idx ON expense_allocations (expense_id);

CREATE TABLE balance_postings (
    posting_id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES expense_groups (group_id) ON DELETE CASCADE,
    expense_id UUID REFERENCES expenses (expense_id) ON DELETE CASCADE,
    participant_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount_minor BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT balance_postings_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX balance_postings_group_participant_idx
    ON balance_postings (group_id, participant_id, currency);
