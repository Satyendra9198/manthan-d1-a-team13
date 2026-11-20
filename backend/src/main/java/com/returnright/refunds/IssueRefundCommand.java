package com.returnright.refunds;

import java.util.UUID;

/**
 * Request to refund a return.
 *
 * @param idempotencyKey client-supplied key from the {@code Idempotency-Key} header
 */
public record IssueRefundCommand(
    UUID returnId, long amountPaise, RefundMethod method, String idempotencyKey) {}
