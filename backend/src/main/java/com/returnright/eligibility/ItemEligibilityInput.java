package com.returnright.eligibility;

import com.returnright.catalog.ProductCategory;
import java.time.Instant;

/**
 * Everything the eligibility engine needs about one order line.
 *
 * @param deliveredAt delivery timestamp, or {@code null} if the item has not been delivered
 * @param quantityAlreadyReturned units on other non-cancelled returns
 */
public record ItemEligibilityInput(
    ProductCategory category,
    Instant deliveredAt,
    int quantityOrdered,
    int quantityAlreadyReturned,
    int quantityRequested) {}
