package com.returnright.model.dto.response;

import com.returnright.model.enums.InspectionGrade;
import com.returnright.model.enums.ProductCategory;
import com.returnright.model.enums.RejectionReason;
import com.returnright.model.enums.ReturnReason;
import java.util.List;
import java.util.UUID;

/**
 * One returned line as the warehouse sees it. Inspection fields are {@code null} until inspected.
 *
 * @param returnItemId the id to send back in the inspection request
 */
public record WarehouseItemResponse(
    UUID returnItemId,
    UUID orderItemId,
    String sku,
    String productName,
    String variant,
    ProductCategory category,
    long unitPricePaise,
    int quantity,
    ReturnReason reason,
    String customerComment,
    List<PhotoResponse> photos,
    InspectionGrade grade,
    Integer acceptedQuantity,
    RejectionReason rejectionReason,
    String inspectionNote) {}
