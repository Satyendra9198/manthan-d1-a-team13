package com.returnright.model.dto.response;

import com.returnright.model.enums.InspectionGrade;
import com.returnright.model.enums.RefundMethod;
import java.time.Instant;
import java.util.List;

/**
 * A refund above ₹10,000 waiting for a finance reviewer (A-08).
 *
 * @param customerFlagged the customer was held for review at request time (A-11)
 * @param customerReturnsLast30Days non-cancelled requests in the rolling 30 days
 * @param itemNames product names on the return
 * @param grade grade of the first accepted line
 * @param partiallyAccepted some units were not accepted or a lower amount was chosen
 * @param refundAmountPaise amount decided at inspection
 * @param requestedAmountPaise what the customer asked for before inspection
 * @param refundKey idempotency key the payout uses; the same on every attempt
 */
public record FinanceQueueItemResponse(
    String rmaNumber,
    String orderNumber,
    String customerName,
    boolean customerFlagged,
    int customerReturnsLast30Days,
    List<String> itemNames,
    InspectionGrade grade,
    boolean partiallyAccepted,
    long refundAmountPaise,
    long requestedAmountPaise,
    RefundMethod refundMethod,
    String inspectedByName,
    Instant inspectedAt,
    String refundKey,
    int itemCount) {}
