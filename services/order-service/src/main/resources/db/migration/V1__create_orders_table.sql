CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE orders (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          VARCHAR(255) NOT NULL,
    idempotency_key  VARCHAR(255) NOT NULL UNIQUE,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    total_amount     DECIMAL(12, 2) NOT NULL,
    shipping_address TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status  ON orders(status);
