CREATE TABLE notification_email_deliveries (
    notification_id UUID PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
