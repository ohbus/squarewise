CREATE TABLE auth_login_credentials (
    credential_id UUID PRIMARY KEY,
    canonical_email VARCHAR(254) NOT NULL,
    credential_digest BYTEA NOT NULL UNIQUE,
    credential_kind VARCHAR(16) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    remaining_attempts INTEGER NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT auth_login_credentials_kind_check CHECK (credential_kind IN ('LINK', 'CODE')),
    CONSTRAINT auth_login_credentials_attempts_check CHECK (remaining_attempts >= 0),
    CONSTRAINT auth_login_credentials_expiry_check CHECK (expires_at > issued_at)
);

CREATE INDEX auth_login_credentials_email_idx
    ON auth_login_credentials (canonical_email, issued_at DESC);

CREATE TABLE auth_sessions (
    session_id UUID PRIMARY KEY,
    account_id UUID,
    family_id UUID NOT NULL,
    refresh_token_digest BYTEA NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    replaced_by_session_id UUID,
    device_label VARCHAR(120),
    client_kind VARCHAR(16) NOT NULL,
    CONSTRAINT auth_sessions_client_kind_check CHECK (client_kind IN ('BROWSER', 'NATIVE', 'SERVICE')),
    CONSTRAINT auth_sessions_expiry_check CHECK (expires_at > created_at),
    CONSTRAINT auth_sessions_replacement_fk FOREIGN KEY (replaced_by_session_id)
        REFERENCES auth_sessions (session_id)
);

CREATE INDEX auth_sessions_family_idx ON auth_sessions (family_id, created_at DESC);
CREATE INDEX auth_sessions_account_idx ON auth_sessions (account_id, created_at DESC);
