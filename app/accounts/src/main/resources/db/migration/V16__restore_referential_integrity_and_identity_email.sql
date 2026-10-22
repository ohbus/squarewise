-- Restore durable same-service relationships after the historical performance
-- migration and retain the current canonical-email uniqueness rule.
CREATE UNIQUE INDEX uq_account_identities_email
    ON account_identities (email);

ALTER TABLE account_identities
    ADD CONSTRAINT account_identities_account_id_fkey
    FOREIGN KEY (account_id)
    REFERENCES account_profiles (account_id)
    ON DELETE CASCADE;

ALTER TABLE auth_sessions
    ADD CONSTRAINT auth_sessions_replacement_fk
    FOREIGN KEY (replaced_by_session_id)
    REFERENCES auth_sessions (session_id)
    ON DELETE SET NULL;
