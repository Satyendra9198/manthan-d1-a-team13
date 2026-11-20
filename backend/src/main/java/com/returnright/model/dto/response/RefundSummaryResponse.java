package com.returnright.model.dto.response;

import com.returnright.model.enums.CustomerRefundStatus;
import com.returnright.model.enums.RefundMethod;
import java.time.Instant;
import java.util.List;

/**
 * The refund decided at inspection, with the "Why ₹949.50 and not ₹1,899?" explanation.
 *
 * @param requestedAmountPaise what the customer asked for before inspection
 * @param refundedAt {@code null} until the money has been paid out
 * @param explanation one customer-facing sentence per adjustment; empty when refunded in full
 */
public record RefundSummaryResponse(
    long amountPaise,
    long requestedAmountPaise,
    RefundMethod method,
    CustomerRefundStatus status,
    Instant refundedAt,
    List<String> explanation) {}
