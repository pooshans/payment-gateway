-- V1__Create_initial_schema.sql
-- Create customers table
CREATE TABLE IF NOT EXISTS customers (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20),
    company VARCHAR(255),
    address VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100),
    customer_profile_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

-- Create payment_methods table
CREATE TABLE IF NOT EXISTS payment_methods (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    payment_profile_id VARCHAR(255),
    last4_digits VARCHAR(4),
    card_type VARCHAR(50),
    expiration_month VARCHAR(2),
    expiration_year VARCHAR(4),
    cardholder_name VARCHAR(255),
    account_type VARCHAR(50),
    bank_name VARCHAR(255),
    routing_number VARCHAR(50),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

-- Create payments table
CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    payment_id VARCHAR(255) NOT NULL UNIQUE,
    customer_id BIGINT,
    amount DECIMAL(19, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    payment_gateway VARCHAR(50),
    description VARCHAR(255),
    correlation_id VARCHAR(255),
    idempotency_key VARCHAR(255),
    auth_code VARCHAR(50),
    last4_digits VARCHAR(4),
    card_type VARCHAR(50),
    subscription_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

-- Create payment_transactions table
CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(255) NOT NULL UNIQUE,
    payment_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    original_transaction_id VARCHAR(255),
    amount DECIMAL(19, 4) NOT NULL,
    status VARCHAR(50) NOT NULL,
    response_code VARCHAR(50),
    response_message VARCHAR(255),
    auth_code VARCHAR(50),
    correlation_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    FOREIGN KEY (payment_id) REFERENCES payments(id)
);

-- Create refund_transactions table
CREATE TABLE IF NOT EXISTS refund_transactions (
    id BIGSERIAL PRIMARY KEY,
    refund_id VARCHAR(255) NOT NULL UNIQUE,
    original_transaction_id VARCHAR(255) NOT NULL,
    payment_id BIGINT NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    status VARCHAR(50) NOT NULL,
    response_code VARCHAR(50),
    response_message VARCHAR(255),
    reason VARCHAR(255),
    correlation_id VARCHAR(255),
    idempotency_key VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    FOREIGN KEY (payment_id) REFERENCES payments(id)
);

-- Create subscriptions table
CREATE TABLE IF NOT EXISTS subscriptions (
    id BIGSERIAL PRIMARY KEY,
    subscription_id VARCHAR(255) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    payment_method_id BIGINT,
    status VARCHAR(50) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    interval_length INT NOT NULL,
    interval_unit VARCHAR(20) NOT NULL,
    total_cycles INT,
    completed_cycles INT DEFAULT 0,
    start_date TIMESTAMP,
    next_billing_date TIMESTAMP,
    end_date TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancel_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(id),
    FOREIGN KEY (payment_method_id) REFERENCES payment_methods(id)
);
