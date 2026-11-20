package com.returnright.model.dto.response;

import java.time.Instant;

/** A received parcel waiting for inspection. */
public record WarehouseQueueItemResponse(
    String rmaNumber,
    String orderNumber,
    String customerName,
    Instant requestedAt,
    int itemCount) {}
