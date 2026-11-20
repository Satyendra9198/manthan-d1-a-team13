package com.returnright.client.dto;

import java.util.UUID;

/** Payload sent to the payment gateway. */
public record GatewayRefundRequest(String idempotencyKey, UUID returnId, long amountPaise) {}
