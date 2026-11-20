CREATE TABLE customer_order (
    id             UUID         PRIMARY KEY,
    order_number   VARCHAR(32)  NOT NULL,
    customer_email VARCHAR(254) NOT NULL,
    customer_name  VARCHAR(120) NOT NULL,
    placed_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_customer_order_order_number UNIQUE (order_number),
    CONSTRAINT ck_customer_order_order_number_upper CHECK (order_number = UPPER(order_number)),
    CONSTRAINT ck_customer_order_email_lower CHECK (customer_email = LOWER(customer_email))
);

CREATE TABLE order_item (
    id               UUID         PRIMARY KEY,
    order_id         UUID         NOT NULL REFERENCES customer_order (id),
    line_number      INTEGER      NOT NULL,
    sku              VARCHAR(64)  NOT NULL,
    product_name     VARCHAR(200) NOT NULL,
    variant          VARCHAR(120),
    category         VARCHAR(32)  NOT NULL,
    unit_price_paise BIGINT       NOT NULL,
    quantity         INTEGER      NOT NULL,
    delivered_at     TIMESTAMPTZ,
    CONSTRAINT uq_order_item_order_line UNIQUE (order_id, line_number),
    CONSTRAINT ck_order_item_category CHECK (category IN
        ('ELECTRONICS', 'APPAREL', 'HOME_KITCHEN', 'BEAUTY', 'INNERWEAR', 'PERISHABLES')),
    CONSTRAINT ck_order_item_unit_price_non_negative CHECK (unit_price_paise >= 0),
    CONSTRAINT ck_order_item_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX ix_order_item_order_id ON order_item (order_id);
