CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE payments (
    id                    UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id              VARCHAR(255) NOT NULL,
    idempotency_key       VARCHAR(255) NOT NULL UNIQUE,
    gateway               VARCHAR(20)  NOT NULL,
    gateway_payment_id    VARCHAR(255),
    gateway_client_secret VARCHAR(512),
    amount                DECIMAL(12, 2) NOT NULL,
    currency              VARCHAR(3)   NOT NULL DEFAULT 'USD',
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    failure_reason        TEXT,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_order_id           ON payments (order_id);
CREATE INDEX idx_payments_gateway_payment_id ON payments (gateway_payment_id);
