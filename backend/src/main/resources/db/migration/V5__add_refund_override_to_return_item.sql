-- Partial accept by amount (A-20): the agent may refund less than the graded amount.
ALTER TABLE return_item
    ADD COLUMN refund_override_paise BIGINT,
    ADD CONSTRAINT ck_return_item_refund_override_positive
        CHECK (refund_override_paise IS NULL OR refund_override_paise > 0);
