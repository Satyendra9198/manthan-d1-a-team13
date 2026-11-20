-- One refund per return; the unique keys are the database guard against paying twice (ADR-001).
-- return_id gets its foreign key once the return table exists.
CREATE TABLE refund (
    id                     UUID         PRIMARY KEY,
    return_id              UUID         NOT NULL,
    amount_paise           BIGINT       NOT NULL,
    method                 VARCHAR(32)  NOT NULL,
    idempotency_key        VARCHAR(255) NOT NULL,
    status                 VARCHAR(32)  NOT NULL,
    gateway_transaction_id VARCHAR(255),
    failure_reason         VARCHAR(500),
    CONSTRAINT uq_refund_return_id UNIQUE (return_id),
    CONSTRAINT uq_refund_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_refund_amount_positive CHECK (amount_paise > 0),
    CONSTRAINT ck_refund_method CHECK (method IN ('ORIGINAL_PAYMENT', 'STORE_CREDIT')),
    CONSTRAINT ck_refund_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'UNKNOWN'))
);
