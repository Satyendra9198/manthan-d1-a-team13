CREATE SEQUENCE rma_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE return_request (
    id                 UUID         PRIMARY KEY,
    rma_number         VARCHAR(32)  NOT NULL,
    order_id           UUID         NOT NULL REFERENCES customer_order (id),
    customer_email     VARCHAR(254) NOT NULL,
    status             VARCHAR(32)  NOT NULL,
    refund_method      VARCHAR(32)  NOT NULL,
    flagged_for_review BOOLEAN      NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    approved_at        TIMESTAMPTZ,
    version            BIGINT       NOT NULL,
    CONSTRAINT uq_return_request_rma_number UNIQUE (rma_number),
    CONSTRAINT ck_return_request_status CHECK (status IN
        ('REQUESTED', 'APPROVED', 'IN_TRANSIT', 'RECEIVED', 'INSPECTED',
         'AWAITING_FINANCE_APPROVAL', 'REFUND_PENDING', 'REFUNDED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_return_request_refund_method CHECK (refund_method IN
        ('ORIGINAL_PAYMENT', 'STORE_CREDIT'))
);

CREATE INDEX ix_return_request_order_id ON return_request (order_id);
CREATE INDEX ix_return_request_customer_email_created_at
    ON return_request (customer_email, created_at);

CREATE TABLE return_item (
    id               UUID          PRIMARY KEY,
    return_id        UUID          NOT NULL REFERENCES return_request (id),
    order_item_id    UUID          NOT NULL REFERENCES order_item (id),
    quantity         INTEGER       NOT NULL,
    reason           VARCHAR(32)   NOT NULL,
    customer_comment VARCHAR(500),
    CONSTRAINT uq_return_item_return_order_item UNIQUE (return_id, order_item_id),
    CONSTRAINT ck_return_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_return_item_reason CHECK (reason IN
        ('TOO_BIG', 'TOO_SMALL', 'DAMAGED_ON_ARRIVAL', 'NOT_AS_DESCRIBED', 'CHANGED_MY_MIND'))
);

CREATE INDEX ix_return_item_order_item_id ON return_item (order_item_id);

CREATE TABLE return_photo (
    id             UUID         PRIMARY KEY,
    return_item_id UUID         NOT NULL REFERENCES return_item (id),
    storage_key    VARCHAR(200) NOT NULL,
    content_type   VARCHAR(32)  NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    uploaded_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_return_photo_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_return_photo_size_positive CHECK (size_bytes > 0)
);

CREATE INDEX ix_return_photo_return_item_id ON return_photo (return_item_id);

ALTER TABLE refund
    ADD CONSTRAINT fk_refund_return_id FOREIGN KEY (return_id) REFERENCES return_request (id);
