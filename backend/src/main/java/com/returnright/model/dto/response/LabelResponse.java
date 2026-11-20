package com.returnright.model.dto.response;

import java.time.Instant;

/**
 * Mock return label. The frontend renders {@code qrPayload} as a QR code; the warehouse scans it.
 */
public record LabelResponse(String labelCode, String qrPayload, Instant expiresAt) {}
