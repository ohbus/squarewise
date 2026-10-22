ALTER TABLE auth_sessions
    ADD COLUMN absolute_expires_at TIMESTAMP WITH TIME ZONE;

UPDATE auth_sessions
   SET absolute_expires_at = expires_at
 WHERE absolute_expires_at IS NULL;

ALTER TABLE auth_sessions
    ALTER COLUMN absolute_expires_at SET NOT NULL;

ALTER TABLE auth_sessions
    ADD CONSTRAINT auth_sessions_absolute_expiry_check
        CHECK (absolute_expires_at >= expires_at AND absolute_expires_at > created_at);
