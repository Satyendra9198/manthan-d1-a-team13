package com.returnright.model.dto.response;

import com.returnright.model.enums.CustomerTimelineStep;
import com.returnright.model.enums.RefundMethod;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The "Return requested" confirmation screen.
 *
 * @param status {@code APPROVED} when the label is ready, {@code REQUESTED} while the return waits
 *     for review
 * @param labelReady true when the customer can download the shipping label
 * @param estimatedRefundPaise before inspection; grade C items are later refunded at 50%
 */
public record ReturnCreatedResponse(
    UUID returnId,
    String rmaNumber,
    CustomerTimelineStep status,
    boolean labelReady,
    RefundMethod refundMethod,
    long estimatedRefundPaise,
    Instant createdAt,
    List<ReturnLineResponse> items) {}
