CREATE TABLE expense_groups (
    group_id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT expense_groups_name_not_blank CHECK (TRIM(name) <> ''),
    CONSTRAINT expense_groups_kind CHECK (kind IN ('HOUSEHOLD', 'COUPLE', 'TRIP')),
    CONSTRAINT expense_groups_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT expense_groups_revision_nonnegative CHECK (revision >= 0)
);

CREATE TABLE group_memberships (
    membership_id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES expense_groups (group_id) ON DELETE CASCADE,
    subject VARCHAR(200) NOT NULL,
    CONSTRAINT group_memberships_subject_not_blank CHECK (TRIM(subject) <> ''),
    CONSTRAINT group_memberships_group_subject_key UNIQUE (group_id, subject)
);

CREATE INDEX group_memberships_subject_idx ON group_memberships (subject, membership_id);

CREATE TABLE group_invitations (
    token VARCHAR(64) PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES expense_groups (group_id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    claimed_by VARCHAR(200),
    CONSTRAINT group_invitations_token_format CHECK (token ~ '^[a-f0-9]{64}$'),
    CONSTRAINT group_invitations_claim_state CHECK (
        (claimed_at IS NULL AND claimed_by IS NULL) OR
        (claimed_at IS NOT NULL AND claimed_by IS NOT NULL)
    )
);

CREATE INDEX group_invitations_group_id_idx ON group_invitations (group_id);
