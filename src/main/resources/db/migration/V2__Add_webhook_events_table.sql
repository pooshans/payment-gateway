-- V2__Add_webhook_events_table.sql
CREATE TABLE IF NOT EXISTS webhook_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    event_source VARCHAR(100),
    payload TEXT,
    signature VARCHAR(1024),
    signature_valid BOOLEAN,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processing_attempts INTEGER DEFAULT 0,
    last_error TEXT,
    correlation_id VARCHAR(255),
    related_payment_id VARCHAR(255),
    related_subscription_id VARCHAR(255),
    related_customer_id BIGINT,
    received_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    
    CONSTRAINT fk_webhook_events_customer
        FOREIGN KEY (related_customer_id)
        REFERENCES customers(id)
);

-- Create indexes for better performance
CREATE INDEX idx_webhook_event_id ON webhook_events(event_id);
CREATE INDEX idx_webhook_event_type ON webhook_events(event_type);
CREATE INDEX idx_webhook_processed ON webhook_events(processed);
CREATE INDEX idx_webhook_related_payment ON webhook_events(related_payment_id);
CREATE INDEX idx_webhook_related_subscription ON webhook_events(related_subscription_id);
CREATE INDEX idx_webhook_related_customer ON webhook_events(related_customer_id);
