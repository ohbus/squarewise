CREATE TABLE expense_groups (
    group_id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT expense_groups_name_not_blank CHECK (TRIM(name) <> ''),
    CONSTRAINT expense_groups_kind CHECK (kind IN ('HOUSEHOLD', 'COUPLE', 'TRIP')),
    CONSTRAINT expense_groups_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT expense_groups_revision_nonnegative CHECK (revision >= 0),
    CONSTRAINT expense_groups_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE group_memberships (
    membership_id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    subject VARCHAR(200),
    display_name VARCHAR(120),
    is_placeholder BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT group_memberships_subject_not_blank CHECK (subject IS NULL OR TRIM(subject) <> ''),
    CONSTRAINT group_memberships_group_subject_key UNIQUE (group_id, subject),
    CONSTRAINT group_memberships_status CHECK (status IN ('ACTIVE', 'REMOVED'))
);

CREATE INDEX group_memberships_subject_idx ON group_memberships (subject, membership_id);
CREATE INDEX idx_group_memberships_group_status ON group_memberships (group_id, status);
CREATE INDEX idx_group_memberships_lookup ON group_memberships (group_id, membership_id, status);

CREATE TABLE group_invitations (
    token VARCHAR(64) PRIMARY KEY,
    group_id UUID NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    claimed_by VARCHAR(200),
    revoked_at TIMESTAMP WITH TIME ZONE,
    placeholder_id UUID,
    CONSTRAINT group_invitations_token_format CHECK (token ~ '^[a-f0-9]{64}$'),
    CONSTRAINT group_invitations_claim_state CHECK (
        (claimed_at IS NULL AND claimed_by IS NULL) OR
        (claimed_at IS NOT NULL AND claimed_by IS NOT NULL)
    )
);

CREATE INDEX group_invitations_group_id_idx ON group_invitations (group_id);

CREATE TABLE group_audit (
    audit_id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    subject VARCHAR(200) NOT NULL,
    action VARCHAR(80) NOT NULL,
    revision BIGINT NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_group_audit_group_revision ON group_audit (group_id, revision);

CREATE TABLE settlements (
    settlement_id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    from_participant_id UUID NOT NULL,
    to_participant_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reversal_reason VARCHAR(240),
    status VARCHAR(16) NOT NULL,
    CONSTRAINT settlements_positive_amount CHECK (amount_minor > 0),
    CONSTRAINT settlements_distinct_participants CHECK (from_participant_id <> to_participant_id),
    CONSTRAINT settlements_status CHECK (status IN ('RECORDED', 'REVERSED'))
);

CREATE INDEX settlements_group_id_idx ON settlements (group_id, settlement_id);

CREATE TABLE expenses (
    expense_id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    description VARCHAR(240) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'other',
    currency VARCHAR(3) NOT NULL,
    amount_minor BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    allocation_mode VARCHAR(32) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT expenses_positive_amount CHECK (amount_minor > 0),
    CONSTRAINT expenses_description_not_blank CHECK (TRIM(description) <> ''),
    CONSTRAINT expenses_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT expenses_version_positive CHECK (version >= 1)
);

CREATE INDEX expenses_group_idx ON expenses (group_id, created_at);
CREATE INDEX expenses_group_deleted_cursor_idx ON expenses (group_id, deleted, expense_id);

CREATE TABLE expense_payers (
    payer_id UUID PRIMARY KEY,
    expense_id UUID NOT NULL,
    participant_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    CONSTRAINT expense_payers_positive_amount CHECK (amount_minor > 0),
    CONSTRAINT uk_expense_payers_expense_participant UNIQUE (expense_id, participant_id)
);

CREATE INDEX expense_payers_expense_idx ON expense_payers (expense_id);

CREATE TABLE expense_allocations (
    allocation_id UUID PRIMARY KEY,
    expense_id UUID NOT NULL,
    participant_id UUID NOT NULL,
    allocated_minor BIGINT NOT NULL,
    CONSTRAINT expense_allocations_nonnegative_amount CHECK (allocated_minor >= 0),
    CONSTRAINT uk_expense_allocations_expense_participant UNIQUE (expense_id, participant_id)
);

CREATE INDEX expense_allocations_expense_idx ON expense_allocations (expense_id);

CREATE TABLE balance_postings (
    posting_id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    expense_id UUID,
    settlement_id UUID,
    participant_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount_minor BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT balance_postings_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX balance_postings_group_participant_idx
    ON balance_postings (group_id, participant_id, currency);
CREATE INDEX balance_postings_settlement_idx
    ON balance_postings (settlement_id);
CREATE INDEX balance_postings_expense_idx
    ON balance_postings (expense_id);
CREATE INDEX idx_balance_postings_covering
    ON balance_postings (group_id, participant_id, currency, amount_minor);

CREATE TABLE expense_outbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    aggregate_id UUID NOT NULL,
    group_id UUID NOT NULL,
    group_revision BIGINT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    lease_until TIMESTAMP WITH TIME ZONE,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT expense_outbox_group_revision_positive CHECK (group_revision > 0),
    CONSTRAINT expense_outbox_attempts_nonnegative CHECK (attempts >= 0),
    CONSTRAINT expense_outbox_status CHECK (status IN ('PENDING', 'CLAIMED', 'PUBLISHED', 'PARKED')),
    CONSTRAINT expense_outbox_lease_state CHECK (
        (status = 'CLAIMED' AND lease_until IS NOT NULL) OR
        (status <> 'CLAIMED' AND lease_until IS NULL)
    )
);

CREATE INDEX expense_outbox_claim_idx
    ON expense_outbox (status, available_at, lease_until, occurred_at, event_id);
CREATE INDEX expense_outbox_group_idx
    ON expense_outbox (group_id, group_revision);
CREATE INDEX expense_outbox_retention_idx
    ON expense_outbox (status, occurred_at);

CREATE TABLE sync_changes (
    change_id UUID PRIMARY KEY,
    group_id VARCHAR(100) NOT NULL,
    revision BIGINT NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    payload TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_sync_changes_group_revision UNIQUE (group_id, revision)
);

CREATE INDEX idx_sync_changes_group_rev ON sync_changes (group_id, revision);

CREATE TABLE recurring_expense_schedules (
    schedule_id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    description VARCHAR(240) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    frequency VARCHAR(16) NOT NULL,
    day_of_month INT,
    start_date DATE NOT NULL,
    end_date DATE,
    next_occurrence_date DATE NOT NULL,
    paused BOOLEAN NOT NULL DEFAULT FALSE,
    custom_specification TEXT,
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 1
);

CREATE INDEX idx_recurring_schedules_due ON recurring_expense_schedules (paused, next_occurrence_date);
CREATE INDEX idx_recurring_schedules_group ON recurring_expense_schedules (group_id);

CREATE TABLE recurring_expense_occurrences (
    occurrence_id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL,
    occurrence_date DATE NOT NULL,
    expense_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_recurring_occurrences_schedule_date UNIQUE (schedule_id, occurrence_date)
);

CREATE INDEX idx_recurring_occurrences_schedule ON recurring_expense_occurrences (schedule_id);
CREATE INDEX idx_recurring_occurrences_expense ON recurring_expense_occurrences (expense_id);

CREATE TABLE expense_idempotency (
    idempotency_id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    actor_subject VARCHAR(255) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    expense_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT expense_idempotency_scope_unique
        UNIQUE (group_id, actor_subject, operation, idempotency_key)
);

CREATE INDEX expense_idempotency_expense_idx ON expense_idempotency (expense_id);
CREATE INDEX expense_idempotency_created_at_idx ON expense_idempotency (created_at);
