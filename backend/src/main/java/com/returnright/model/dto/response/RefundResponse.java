package com.returnright.model.dto.response;

import com.returnright.model.enums.RefundMethod;
import com.returnright.model.enums.RefundStatus;
import java.util.UUID;

public record RefundResponse(
    UUID id,
    UUID returnId,
    long amountPaise,
    RefundMethod method,
    RefundStatus status,
    String gatewayTransactionId,
    String failureReason) {}
