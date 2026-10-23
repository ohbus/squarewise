CREATE TABLE account_profiles (
    account_id UUID PRIMARY KEY,
    subject VARCHAR(200) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    default_currency VARCHAR(3) NOT NULL,
    deletion_requested BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE account_deletion_requests (
    subject VARCHAR(200) PRIMARY KEY,
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT account_deletion_requests_status CHECK (status IN ('REQUESTED', 'CANCELLED', 'COMPLETED'))
);

CREATE INDEX account_deletion_requests_status_idx ON account_deletion_requests (status);

CREATE TABLE account_export_requests (
    export_id UUID PRIMARY KEY,
    subject VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT account_export_requests_status CHECK (status IN ('REQUESTED', 'READY', 'EXPIRED'))
);

CREATE INDEX account_export_requests_subject_idx ON account_export_requests (subject, requested_at DESC);

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
CREATE INDEX auth_login_credentials_expires_at_idx
    ON auth_login_credentials (expires_at);
CREATE INDEX auth_login_credentials_consumed_at_idx
    ON auth_login_credentials (consumed_at);

CREATE TABLE auth_sessions (
    session_id UUID PRIMARY KEY,
    account_id UUID,
    subject VARCHAR(200),
    family_id UUID NOT NULL,
    refresh_token_digest BYTEA NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    absolute_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    replaced_by_session_id UUID,
    device_label VARCHAR(120),
    client_kind VARCHAR(16) NOT NULL,
    CONSTRAINT auth_sessions_client_kind_check CHECK (client_kind IN ('BROWSER', 'NATIVE', 'SERVICE')),
    CONSTRAINT auth_sessions_expiry_check CHECK (expires_at > created_at),
    CONSTRAINT auth_sessions_absolute_expiry_check CHECK (absolute_expires_at >= expires_at AND absolute_expires_at > created_at)
);

CREATE INDEX auth_sessions_family_idx ON auth_sessions (family_id, created_at DESC);
CREATE INDEX auth_sessions_account_idx ON auth_sessions (account_id, created_at DESC);
CREATE INDEX auth_sessions_subject_idx ON auth_sessions (subject);
CREATE INDEX auth_sessions_expiry_idx ON auth_sessions (expires_at, revoked_at);

CREATE TABLE auth_email_outbox (
    outbox_id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    recipient VARCHAR(254) NOT NULL,
    template VARCHAR(16) NOT NULL,
    encrypted_credential VARCHAR(4096) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT auth_email_outbox_template_check CHECK (template IN ('LOGIN_LINK', 'LOGIN_CODE')),
    CONSTRAINT auth_email_outbox_status_check CHECK (status IN ('PENDING', 'CLAIMED', 'PUBLISHED', 'PARKED')),
    CONSTRAINT auth_email_outbox_attempts_check CHECK (attempts >= 0)
);

CREATE INDEX auth_email_outbox_available_idx
    ON auth_email_outbox (status, available_at, created_at);
CREATE INDEX auth_email_outbox_retention_idx
    ON auth_email_outbox (status, created_at);

CREATE TABLE account_identities (
    identity_id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    issuer VARCHAR(255) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_account_identities_issuer_subject UNIQUE (issuer, provider_subject)
);

CREATE INDEX idx_account_identities_account_id ON account_identities (account_id);
CREATE INDEX idx_account_identities_email ON account_identities (email);

-- Same-service integrity guards retained in the current schema.
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
