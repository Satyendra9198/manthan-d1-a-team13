package com.returnright.model.dto.response;

import com.returnright.model.enums.RefundMethod;
import java.time.Instant;

/**
 * A refund above ₹10,000 waiting for a finance reviewer (A-08).
 *
 * @param refundAmountPaise grade-adjusted amount decided at inspection
 * @param requestedAmountPaise what the customer asked for before inspection
 */
public record FinanceQueueItemResponse(
    String rmaNumber,
    String orderNumber,
    String customerName,
    long refundAmountPaise,
    long requestedAmountPaise,
    RefundMethod refundMethod,
    Instant inspectedAt,
    int itemCount) {}
