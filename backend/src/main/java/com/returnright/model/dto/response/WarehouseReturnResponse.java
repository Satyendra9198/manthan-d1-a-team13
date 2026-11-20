package com.returnright.model.dto.response;

import com.returnright.model.enums.RefundMethod;
import com.returnright.model.enums.RejectionReason;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A return on the warehouse inspection screen.
 *
 * @param customerFlagged the customer was held for review at request time (A-11)
 * @param customerReturnsLast30Days non-cancelled requests in the rolling 30 days
 * @param receivedAt when the parcel was scanned in; {@code null} before that
 * @param allowedActions what the signed-in agent can do now, e.g. {@code RECEIVE} or {@code
 *     COMPLETE_INSPECTION}; drives which buttons are enabled
 */
public record WarehouseReturnResponse(
    UUID returnId,
    String rmaNumber,
    ReturnStatus status,
    String orderNumber,
    String customerName,
    boolean customerFlagged,
    int customerReturnsLast30Days,
    RefundMethod refundMethod,
    Instant createdAt,
    Instant receivedAt,
    Long refundAmountPaise,
    RejectionReason rejectionReason,
    Set<ReturnEvent> allowedActions,
    List<WarehouseItemResponse> items) {}
