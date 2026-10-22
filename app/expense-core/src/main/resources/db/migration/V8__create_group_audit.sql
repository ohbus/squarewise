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
