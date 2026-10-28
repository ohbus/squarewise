ALTER TABLE notification_preferences
    ALTER COLUMN subject TYPE VARCHAR(256);

ALTER TABLE notification_inbox_items
    ALTER COLUMN subject TYPE VARCHAR(256);
