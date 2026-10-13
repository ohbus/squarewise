CREATE INDEX auth_email_outbox_retention_idx
    ON auth_email_outbox (status, created_at);
