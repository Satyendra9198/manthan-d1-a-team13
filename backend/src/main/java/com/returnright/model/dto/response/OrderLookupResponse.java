package com.returnright.model.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An order with the return eligibility of each line, for the "Which items are you returning?" step.
 *
 * @param returnableItemCount number of lines the customer can select
 */
public record OrderLookupResponse(
    UUID orderId,
    String orderNumber,
    Instant placedAt,
    int returnableItemCount,
    List<OrderItemResponse> items) {}
