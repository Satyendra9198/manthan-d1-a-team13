package com.returnright.model.dto.response;

import com.returnright.model.enums.RefundStatus;
import com.returnright.model.enums.ReturnStatus;

/**
 * Outcome of a finance or operations decision.
 *
 * @param refundStatus the payout result after a finance approval; otherwise {@code null}
 */
public record ReviewDecisionResponse(
    String rmaNumber, ReturnStatus status, RefundStatus refundStatus) {}
