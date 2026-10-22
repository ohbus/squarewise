-- Drop remaining foreign key constraints on account_identities and auth_sessions
-- referencing account_profiles and auth_sessions.
-- Eliminates parent table row lock contention during credential enrollment and session rotation
-- under 10M DAU.
-- Referential integrity is strictly validated in the application layer via AccountReferenceValidator.

-- 1. Drop foreign key on account_identities referencing account_profiles
ALTER TABLE account_identities DROP CONSTRAINT IF EXISTS account_identities_account_id_fkey;

-- 2. Drop self-referencing foreign key on auth_sessions
ALTER TABLE auth_sessions DROP CONSTRAINT IF EXISTS auth_sessions_replacement_fk;
