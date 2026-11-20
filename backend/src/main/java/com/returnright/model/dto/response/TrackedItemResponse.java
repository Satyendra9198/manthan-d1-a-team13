package com.returnright.model.dto.response;

import com.returnright.model.enums.InspectionGrade;
import com.returnright.model.enums.ReturnReason;
import java.util.UUID;

/**
 * One item on a tracked return. Inspection fields are {@code null} until the warehouse inspects.
 *
 * @param itemId the order item id
 * @param lineTotalPaise unit price × quantity returned, before inspection
 * @param refundPaise grade-adjusted refund for this line after inspection
 */
public record TrackedItemResponse(
    UUID itemId,
    String productName,
    String variant,
    int quantity,
    ReturnReason reason,
    long lineTotalPaise,
    InspectionGrade grade,
    Integer acceptedQuantity,
    Long refundPaise) {}
