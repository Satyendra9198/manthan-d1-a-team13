package com.returnright.model.dto.response;

import com.returnright.model.enums.EligibilityReason;
import com.returnright.model.enums.ProductCategory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One order line and whether it can be returned.
 *
 * @param unitPricePaise price paid per unit, in paise (₹1 = 100 paise)
 * @param returnableQuantity units still available to return; 0 when not returnable
 * @param eligibilityMessage customer-facing reason shown under the item
 * @param lastReturnDate last day (Asia/Kolkata) a return is accepted, or {@code null} when no
 *     window applies
 */
public record OrderItemResponse(
    UUID itemId,
    String sku,
    String productName,
    String variant,
    ProductCategory category,
    long unitPricePaise,
    int quantity,
    int returnableQuantity,
    Instant deliveredAt,
    boolean returnable,
    EligibilityReason eligibilityReason,
    String eligibilityMessage,
    LocalDate lastReturnDate) {}
