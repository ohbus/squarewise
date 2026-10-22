ALTER TABLE expense_groups ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE expense_groups ADD CONSTRAINT expense_groups_status CHECK (status IN ('ACTIVE', 'ARCHIVED'));

ALTER TABLE group_memberships ALTER COLUMN subject DROP NOT NULL;
ALTER TABLE group_memberships ADD COLUMN display_name VARCHAR(120);
ALTER TABLE group_memberships ADD COLUMN is_placeholder BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE group_memberships ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE group_memberships ADD CONSTRAINT group_memberships_status CHECK (status IN ('ACTIVE', 'REMOVED'));

ALTER TABLE group_invitations ADD COLUMN revoked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE group_invitations ADD COLUMN placeholder_id UUID REFERENCES group_memberships (membership_id) ON DELETE SET NULL;
