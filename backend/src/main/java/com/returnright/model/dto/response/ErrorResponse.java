package com.returnright.model.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Error body returned by every endpoint.
 *
 * @param code stable machine-readable code, e.g. {@code ILLEGAL_TRANSITION}
 * @param message human-readable explanation
 * @param details field-level problems for validation errors, otherwise empty
 */
public record ErrorResponse(String code, String message, List<String> details, Instant timestamp) {}
