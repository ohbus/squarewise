CREATE TABLE account_profiles (
    account_id UUID PRIMARY KEY,
    subject VARCHAR(200) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    default_currency CHAR(3) NOT NULL,
    deletion_requested BOOLEAN NOT NULL DEFAULT FALSE
);
