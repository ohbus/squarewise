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
