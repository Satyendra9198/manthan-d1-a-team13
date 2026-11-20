-- ReturnRight core schema. Money is stored as integer paise.

CREATE TABLE customer (
    id          UUID PRIMARY KEY,
    full_name   VARCHAR(200) NOT NULL,
    email       VARCHAR(320) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_email UNIQUE (email)
);

CREATE TABLE product (
    id           UUID PRIMARY KEY,
    sku          VARCHAR(50)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    category     VARCHAR(30)  NOT NULL,
    price_paise  BIGINT       NOT NULL,
    CONSTRAINT uq_product_sku UNIQUE (sku),
    CONSTRAINT ck_product_category CHECK (category IN
        ('ELECTRONICS', 'APPAREL', 'HOME_KITCHEN', 'BEAUTY', 'INNERWEAR', 'PERISHABLES')),
    CONSTRAINT ck_product_price CHECK (price_paise >= 0)
);

CREATE TABLE customer_order (
    id              UUID PRIMARY KEY,
    order_number    VARCHAR(30)  NOT NULL,
    customer_id     UUID         NOT NULL REFERENCES customer (id),
    ordered_at      TIMESTAMPTZ  NOT NULL,
    delivered_at    TIMESTAMPTZ,
    payment_method  VARCHAR(30)  NOT NULL,
    CONSTRAINT uq_customer_order_number UNIQUE (order_number),
    CONSTRAINT ck_customer_order_delivery CHECK (delivered_at IS NULL OR delivered_at >= ordered_at)
);
CREATE INDEX ix_customer_order_customer ON customer_order (customer_id);

CREATE TABLE order_item (
    id                UUID PRIMARY KEY,
    order_id          UUID    NOT NULL REFERENCES customer_order (id),
    product_id        UUID    NOT NULL REFERENCES product (id),
    quantity          INTEGER NOT NULL,
    unit_price_paise  BIGINT  NOT NULL,
    CONSTRAINT ck_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_item_price CHECK (unit_price_paise >= 0)
);
CREATE INDEX ix_order_item_order ON order_item (order_id);

CREATE TABLE return_request (
    id                   UUID PRIMARY KEY,
    return_number        VARCHAR(30)  NOT NULL,
    order_id             UUID         NOT NULL REFERENCES customer_order (id),
    customer_id          UUID         NOT NULL REFERENCES customer (id),
    status               VARCHAR(30)  NOT NULL,
    refund_method        VARCHAR(30)  NOT NULL,
    flagged              BOOLEAN      NOT NULL DEFAULT FALSE,
    label_code           VARCHAR(40),
    label_expires_at     TIMESTAMPTZ,
    refund_amount_paise  BIGINT,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    closed_at            TIMESTAMPTZ,
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_return_request_number UNIQUE (return_number),
    CONSTRAINT uq_return_request_label UNIQUE (label_code),
    CONSTRAINT ck_return_request_status CHECK (status IN
        ('REQUESTED', 'APPROVED', 'IN_TRANSIT', 'RECEIVED', 'INSPECTED',
         'AWAITING_FINANCE_APPROVAL', 'REFUND_PENDING', 'REFUNDED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_return_request_refund_method CHECK (refund_method IN ('ORIGINAL_PAYMENT', 'STORE_CREDIT')),
    CONSTRAINT ck_return_request_refund_amount CHECK (refund_amount_paise IS NULL OR refund_amount_paise >= 0)
);
CREATE INDEX ix_return_request_status ON return_request (status);
CREATE INDEX ix_return_request_customer_created ON return_request (customer_id, created_at);
CREATE INDEX ix_return_request_order ON return_request (order_id);

CREATE TABLE return_item (
    id                 UUID PRIMARY KEY,
    return_id          UUID         NOT NULL REFERENCES return_request (id),
    order_item_id      UUID         NOT NULL REFERENCES order_item (id),
    quantity           INTEGER      NOT NULL,
    reason             VARCHAR(30)  NOT NULL,
    reason_note        VARCHAR(500),
    accepted_quantity  INTEGER,
    grade              VARCHAR(1),
    inspection_notes   VARCHAR(500),
    line_refund_paise  BIGINT,
    CONSTRAINT uq_return_item_order_item UNIQUE (return_id, order_item_id),
    CONSTRAINT ck_return_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_return_item_accepted CHECK (accepted_quantity IS NULL OR accepted_quantity BETWEEN 0 AND quantity),
    CONSTRAINT ck_return_item_grade CHECK (grade IS NULL OR grade IN ('A', 'B', 'C')),
    CONSTRAINT ck_return_item_reason CHECK (reason IN
        ('DAMAGED', 'DEFECTIVE', 'WRONG_ITEM', 'SIZE_OR_FIT', 'NOT_AS_DESCRIBED', 'CHANGED_MIND', 'OTHER'))
);
CREATE INDEX ix_return_item_return ON return_item (return_id);
CREATE INDEX ix_return_item_order_item ON return_item (order_item_id);

-- One refund per return and one refund per idempotency key: the database-level guard against paying twice.
CREATE TABLE refund (
    id                      UUID PRIMARY KEY,
    return_id               UUID         NOT NULL REFERENCES return_request (id),
    amount_paise            BIGINT       NOT NULL,
    method                  VARCHAR(30)  NOT NULL,
    idempotency_key         VARCHAR(100) NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    gateway_transaction_id  VARCHAR(100),
    failure_reason          VARCHAR(200),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_refund_return_id UNIQUE (return_id),
    CONSTRAINT uq_refund_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_refund_amount CHECK (amount_paise > 0),
    CONSTRAINT ck_refund_method CHECK (method IN ('ORIGINAL_PAYMENT', 'STORE_CREDIT')),
    CONSTRAINT ck_refund_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'UNKNOWN'))
);
CREATE INDEX ix_refund_status ON refund (status);

-- Append-only audit trail of every return state change.
CREATE TABLE audit_log (
    id           BIGSERIAL PRIMARY KEY,
    return_id    UUID         NOT NULL REFERENCES return_request (id),
    actor_role   VARCHAR(30)  NOT NULL,
    actor_id     VARCHAR(320) NOT NULL,
    event        VARCHAR(40)  NOT NULL,
    from_status  VARCHAR(30),
    to_status    VARCHAR(30)  NOT NULL,
    details      VARCHAR(1000),
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_audit_log_return ON audit_log (return_id, id);

CREATE FUNCTION reject_audit_log_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION reject_audit_log_change();
