CREATE INDEX notification_email_deliveries_retention_idx
    ON notification_email_deliveries (status, updated_at);
