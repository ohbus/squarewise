CREATE TABLE account_identities (
    identity_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES account_profiles(account_id) ON DELETE CASCADE,
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

-- Deterministic forward-only backfill from existing account_profiles
INSERT INTO account_identities (identity_id, account_id, issuer, provider_subject, email, email_verified, status, created_at, updated_at)
SELECT
    account_id AS identity_id,
    account_id,
    'squarewise-internal' AS issuer,
    subject AS provider_subject,
    CASE
        WHEN subject LIKE 'internal:%' THEN substring(subject from 10)
        ELSE NULL
    END AS email,
    TRUE AS email_verified,
    'ACTIVE' AS status,
    CURRENT_TIMESTAMP AS created_at,
    CURRENT_TIMESTAMP AS updated_at
FROM account_profiles ap
WHERE NOT EXISTS (
    SELECT 1 FROM account_identities ai
    WHERE ai.issuer = 'squarewise-internal' AND ai.provider_subject = ap.subject
);
