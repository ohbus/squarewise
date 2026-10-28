ALTER TABLE notification_email_deliveries
    ADD COLUMN IF NOT EXISTS recipient_email VARCHAR(254);
