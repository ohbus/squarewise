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
