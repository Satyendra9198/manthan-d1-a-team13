package com.returnright.model.dto.response;

import com.returnright.model.enums.CustomerTimelineStep;
import com.returnright.model.enums.RefundMethod;
import com.returnright.model.enums.RefundStatus;
import com.returnright.model.enums.ReturnStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full view of a return.
 *
 * @param status internal state (staff screens)
 * @param timelineStep customer-facing step
 * @param label {@code null} until the return is approved
 * @param refundStatus {@code null} until a refund has been attempted
 * @param ineligibleItems only filled in the response to creating a return
 */
public record ReturnResponse(
    UUID id,
    String returnNumber,
    String orderNumber,
    String customerName,
    ReturnStatus status,
    CustomerTimelineStep timelineStep,
    RefundMethod refundMethod,
    boolean flagged,
    Long refundAmountPaise,
    RefundStatus refundStatus,
    LabelResponse label,
    List<ReturnItemResponse> items,
    List<IneligibleItemResponse> ineligibleItems,
    List<TimelineEventResponse> timeline,
    Instant createdAt,
    Instant updatedAt) {}
