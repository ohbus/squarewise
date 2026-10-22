CREATE TABLE auth_rate_limit_buckets (
    rate_key_digest BYTEA PRIMARY KEY,
    window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    request_count INTEGER NOT NULL,
    last_requested_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT auth_rate_limit_buckets_count_check CHECK (request_count >= 0)
);
