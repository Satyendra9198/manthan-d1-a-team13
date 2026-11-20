package com.returnright.model.dto.response;

import com.returnright.model.enums.RefundStatus;
import com.returnright.model.enums.ReturnStatus;
import java.time.Instant;

/**
 * Outcome of a finance or operations decision.
 *
 * @param refundStatus the payout result after a finance approval; otherwise {@code null}
 * @param refundAmountPaise the amount paid after a finance approval; otherwise {@code null}
 * @param refundReference gateway transaction id once paid; otherwise {@code null}
 */
public record ReviewDecisionResponse(
    String rmaNumber,
    ReturnStatus status,
    RefundStatus refundStatus,
    Long refundAmountPaise,
    String refundReference,
    Instant decidedAt) {}
