CREATE INDEX expense_outbox_retention_idx
    ON expense_outbox (status, occurred_at);
