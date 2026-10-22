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
