package com.returnright.model.dto.response;

import com.returnright.model.enums.RefundStatus;
import com.returnright.model.enums.ReturnStatus;

/**
 * Outcome of an inspection or rejection.
 *
 * @param status {@code REFUNDED} when the refund was paid at once, {@code
 *     AWAITING_FINANCE_APPROVAL} above ₹10,000, {@code REFUND_PENDING} if the payout is still being
 *     retried, or {@code REJECTED}
 * @param refundStatus the payout result, or {@code null} when no payout was attempted
 */
public record InspectionResultResponse(
    String rmaNumber,
    ReturnStatus status,
    long refundAmountPaise,
    boolean financeApprovalRequired,
    RefundStatus refundStatus) {}
