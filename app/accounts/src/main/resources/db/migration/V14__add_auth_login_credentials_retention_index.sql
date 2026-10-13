CREATE INDEX auth_login_credentials_expires_at_idx
    ON auth_login_credentials (expires_at);

CREATE INDEX auth_login_credentials_consumed_at_idx
    ON auth_login_credentials (consumed_at);
