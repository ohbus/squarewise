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
