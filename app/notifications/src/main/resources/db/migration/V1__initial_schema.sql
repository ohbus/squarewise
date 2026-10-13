CREATE TABLE notification_preferences (
    subject VARCHAR(200) PRIMARY KEY,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE notification_inbox_items (
    notification_id UUID PRIMARY KEY,
    subject VARCHAR(200) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_notification_inbox_subject_cursor
    ON notification_inbox_items (subject, occurred_at DESC, notification_id DESC);

CREATE TABLE notification_processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX notification_processed_events_processed_at_idx
    ON notification_processed_events (processed_at);

CREATE TABLE notification_email_deliveries (
    notification_id UUID PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX notification_email_deliveries_retention_idx
    ON notification_email_deliveries (status, updated_at);
