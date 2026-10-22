ALTER TABLE auth_sessions DROP CONSTRAINT auth_sessions_replacement_fk;
ALTER TABLE auth_sessions ADD CONSTRAINT auth_sessions_replacement_fk
    FOREIGN KEY (replaced_by_session_id)
    REFERENCES auth_sessions (session_id)
    ON DELETE SET NULL;
