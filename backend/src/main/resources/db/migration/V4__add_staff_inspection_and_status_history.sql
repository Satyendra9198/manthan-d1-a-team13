CREATE TABLE staff_user (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    active        BOOLEAN      NOT NULL,
    CONSTRAINT uq_staff_user_email UNIQUE (email),
    CONSTRAINT ck_staff_user_email_lower CHECK (email = LOWER(email)),
    CONSTRAINT ck_staff_user_role CHECK (role IN
        ('WAREHOUSE_AGENT', 'FINANCE_REVIEWER', 'OPERATIONS_MANAGER'))
);

ALTER TABLE return_request
    ADD COLUMN inspected_at        TIMESTAMPTZ,
    ADD COLUMN inspected_by        UUID REFERENCES staff_user (id),
    ADD COLUMN refund_amount_paise BIGINT,
    ADD COLUMN rejection_reason    VARCHAR(32),
    ADD COLUMN rejection_note      VARCHAR(500),
    ADD CONSTRAINT ck_return_request_refund_amount_non_negative
        CHECK (refund_amount_paise IS NULL OR refund_amount_paise >= 0),
    ADD CONSTRAINT ck_return_request_rejection_reason CHECK (rejection_reason IN
        ('NOT_AS_DESCRIBED', 'USED_OR_WORN', 'MISSING_PARTS_OR_TAGS', 'OUTSIDE_RETURN_WINDOW',
         'OTHER', 'REQUEST_DECLINED', 'REFUND_DECLINED'));

CREATE INDEX ix_return_request_status ON return_request (status);
CREATE INDEX ix_return_request_inspected_at ON return_request (inspected_at);

ALTER TABLE return_item
    ADD COLUMN grade             VARCHAR(1),
    ADD COLUMN accepted_quantity INTEGER,
    ADD COLUMN rejection_reason  VARCHAR(32),
    ADD COLUMN inspection_note   VARCHAR(500),
    ADD CONSTRAINT ck_return_item_grade CHECK (grade IN ('A', 'B', 'C')),
    ADD CONSTRAINT ck_return_item_accepted_quantity
        CHECK (accepted_quantity IS NULL OR accepted_quantity BETWEEN 0 AND quantity),
    ADD CONSTRAINT ck_return_item_rejection_reason CHECK (rejection_reason IN
        ('NOT_AS_DESCRIBED', 'USED_OR_WORN', 'MISSING_PARTS_OR_TAGS', 'OUTSIDE_RETURN_WINDOW',
         'OTHER'));

CREATE TABLE return_status_change (
    id         UUID        PRIMARY KEY,
    return_id  UUID        NOT NULL REFERENCES return_request (id),
    status     VARCHAR(32) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    actor_role VARCHAR(32) NOT NULL,
    actor_id   UUID        REFERENCES staff_user (id),
    CONSTRAINT ck_return_status_change_status CHECK (status IN
        ('REQUESTED', 'APPROVED', 'IN_TRANSIT', 'RECEIVED', 'INSPECTED',
         'AWAITING_FINANCE_APPROVAL', 'REFUND_PENDING', 'REFUNDED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_return_status_change_actor_role CHECK (actor_role IN
        ('CUSTOMER', 'WAREHOUSE_AGENT', 'FINANCE_REVIEWER', 'OPERATIONS_MANAGER', 'SYSTEM'))
);

CREATE INDEX ix_return_status_change_return_id ON return_status_change (return_id, changed_at);

-- Backfill history for returns created before this migration. The exact time of later changes
-- was not recorded, so the current status is dated at the approval (or creation) time.
INSERT INTO return_status_change (id, return_id, status, changed_at, actor_role, actor_id)
SELECT gen_random_uuid(), id, 'REQUESTED', created_at, 'CUSTOMER', NULL FROM return_request;

INSERT INTO return_status_change (id, return_id, status, changed_at, actor_role, actor_id)
SELECT gen_random_uuid(), id, 'APPROVED', approved_at, 'SYSTEM', NULL
FROM return_request WHERE approved_at IS NOT NULL;

INSERT INTO return_status_change (id, return_id, status, changed_at, actor_role, actor_id)
SELECT gen_random_uuid(), id, status, COALESCE(approved_at, created_at), 'SYSTEM', NULL
FROM return_request WHERE status NOT IN ('REQUESTED', 'APPROVED');
