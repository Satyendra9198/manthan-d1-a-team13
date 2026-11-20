package com.returnright.model.dto.response;

import com.returnright.model.enums.ProductCategory;
import java.util.UUID;

/** An order line with how many units can still be returned and whether one unit is eligible now. */
public record OrderItemResponse(
    UUID orderItemId,
    String productName,
    ProductCategory category,
    int quantity,
    long unitPricePaise,
    int returnableQuantity,
    EligibilityResponse eligibility) {}
