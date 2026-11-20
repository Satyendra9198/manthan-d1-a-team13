package com.returnright.model.dto.response;

import java.time.Instant;

/**
 * A return held for review because the customer returns often (A-10, A-11).
 *
 * @param requestsInLast30Days the customer's non-cancelled requests in the rolling 30 days,
 *     including this one
 */
public record FlaggedReturnResponse(
    String rmaNumber,
    String orderNumber,
    String customerName,
    String customerEmail,
    Instant requestedAt,
    int requestsInLast30Days,
    int itemCount,
    long estimatedRefundPaise) {}
