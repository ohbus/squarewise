CREATE TABLE settlements (
    settlement_id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    from_participant_id UUID NOT NULL,
    to_participant_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL,
    reversal_reason VARCHAR(240),
    status VARCHAR(16) NOT NULL,
    CONSTRAINT settlements_positive_amount CHECK (amount_minor > 0),
    CONSTRAINT settlements_distinct_participants CHECK (from_participant_id <> to_participant_id),
    CONSTRAINT settlements_status CHECK (status IN ('RECORDED', 'REVERSED'))
);

CREATE INDEX settlements_group_id_idx ON settlements (group_id, settlement_id);
