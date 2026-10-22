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
