CREATE INDEX auth_sessions_expiry_idx ON auth_sessions (expires_at, revoked_at);
