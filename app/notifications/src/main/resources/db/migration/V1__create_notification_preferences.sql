CREATE TABLE notification_preferences (
    subject VARCHAR(200) PRIMARY KEY,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0
);
