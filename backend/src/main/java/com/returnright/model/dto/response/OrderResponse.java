package com.returnright.model.dto.response;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
    String orderNumber,
    String customerName,
    Instant orderedAt,
    Instant deliveredAt,
    List<OrderItemResponse> items) {}
