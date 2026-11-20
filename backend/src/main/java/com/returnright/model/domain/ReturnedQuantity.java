package com.returnright.model.domain;

import java.util.UUID;

/** Units of one order item already on non-cancelled returns. */
public record ReturnedQuantity(UUID orderItemId, long quantity) {}
