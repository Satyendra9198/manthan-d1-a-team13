package com.returnright.model.dto.response;

import com.returnright.model.enums.ReturnStatus;
import java.time.Instant;
import java.util.UUID;

/** One row in a staff work queue. */
public record ReturnSummaryResponse(
    UUID id,
    String returnNumber,
    String orderNumber,
    String customerName,
    ReturnStatus status,
    boolean flagged,
    Long refundAmountPaise,
    Instant createdAt) {}
