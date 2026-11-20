package com.returnright.model.dto.response;

import com.returnright.model.enums.InspectionGrade;
import com.returnright.model.enums.RejectionReason;
import com.returnright.model.enums.ReturnReason;
import java.util.UUID;

/**
 * One item on a tracked return. Inspection fields are {@code null} until the warehouse inspects.
 *
 * @param itemId the order item id
 * @param lineTotalPaise unit price × quantity returned, before inspection
 * @param refundPaise refund for this line after inspection
 * @param rejectionReason why the line was not fully accepted
 * @param inspectionNote the inspector's note, shown only when this line's refund was reduced
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
    Long refundPaise,
    RejectionReason rejectionReason,
    String inspectionNote) {}
