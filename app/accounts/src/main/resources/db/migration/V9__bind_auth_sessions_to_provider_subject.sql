ALTER TABLE auth_sessions
    ADD COLUMN subject VARCHAR(200);

UPDATE auth_sessions
   SET subject = (
       SELECT profiles.subject
         FROM account_profiles AS profiles
        WHERE profiles.account_id = auth_sessions.account_id
   )
 WHERE subject IS NULL
   AND account_id IS NOT NULL
   AND EXISTS (
       SELECT 1
         FROM account_profiles AS profiles
        WHERE profiles.account_id = auth_sessions.account_id
   );

CREATE INDEX auth_sessions_subject_idx ON auth_sessions (subject);
