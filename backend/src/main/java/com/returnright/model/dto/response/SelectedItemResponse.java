package com.returnright.model.dto.response;

import com.returnright.model.enums.ProductCategory;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One accepted line of the selection, as shown on the later wizard steps.
 *
 * @param lineTotalPaise unit price × selected quantity, before any inspection adjustment
 * @param lastReturnDate last day (Asia/Kolkata) the return must be started
 */
public record SelectedItemResponse(
    UUID itemId,
    String productName,
    String variant,
    ProductCategory category,
    long unitPricePaise,
    int quantity,
    long lineTotalPaise,
    LocalDate lastReturnDate) {}
