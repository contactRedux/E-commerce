CREATE TABLE order_items (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id     UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id   VARCHAR(255) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity     INTEGER NOT NULL CHECK (quantity > 0),
    unit_price   DECIMAL(12, 2) NOT NULL,
    subtotal     DECIMAL(12, 2) NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
