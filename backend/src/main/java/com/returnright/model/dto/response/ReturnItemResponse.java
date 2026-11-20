package com.returnright.model.dto.response;

import com.returnright.model.enums.InspectionGrade;
import com.returnright.model.enums.ProductCategory;
import com.returnright.model.enums.ReturnReason;
import java.util.UUID;

/** A returned line. Inspection fields are {@code null} until the warehouse inspects it. */
public record ReturnItemResponse(
    UUID returnItemId,
    UUID orderItemId,
    String productName,
    ProductCategory category,
    int quantity,
    long unitPricePaise,
    ReturnReason reason,
    String note,
    Integer acceptedQuantity,
    InspectionGrade grade,
    Long lineRefundPaise) {}
