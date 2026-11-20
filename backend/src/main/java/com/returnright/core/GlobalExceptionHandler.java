package com.returnright.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Single error envelope for every non-2xx response. Never leaks stack traces. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** DB constraint name -> (code, status, message). Constraint names come from V1/V2 migrations. */
    private static final Map<String, ApiException> CONSTRAINTS = Map.of(
            "uq_return_items_active_unit", ApiException.conflict("UNIT_ALREADY_IN_RETURN",
                    "One of these items is already part of another return.", Map.of()),
            "uq_finance_reviews_return", ApiException.conflict("ILLEGAL_TRANSITION",
                    "This return has already been reviewed by finance.", Map.of()),
            "uq_refunds_return", ApiException.conflict("ILLEGAL_TRANSITION",
                    "A refund already exists for this return.", Map.of()),
            "uq_refund_one_live_attempt", ApiException.conflict("REFUND_ATTEMPT_IN_FLIGHT",
                    "A refund attempt is already in progress or unresolved.", Map.of()),
            "uq_refund_items_return_item", ApiException.conflict("ILLEGAL_TRANSITION",
                    "An item in this return has already been refunded.", Map.of()));

    public static Map<String, Object> envelope(String code, String message, Map<String, Object> details) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("details", details == null ? Map.of() : details);
        error.put("correlation_id", Correlation.id());
        return Map.of("error", error);
    }

    private static ResponseEntity<Map<String, Object>> respond(HttpStatusCode status, String code, String msg,
                                                               Map<String, Object> details) {
        return ResponseEntity.status(status).body(envelope(code, msg, details));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        return respond(e.status(), e.code(), e.getMessage(), e.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        List<Map<String, Object>> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.<String, Object>of("loc", List.of("body",
                                f.getField().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase()),
                        "msg", Optional.ofNullable(f.getDefaultMessage()).orElse("invalid")))
                .toList();
        return respond(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", "Some fields are invalid.",
                Map.of("fields", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> unreadable(Exception e) {
        return respond(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", "The request body or parameters are invalid.",
                Map.of());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> missingHeader(MissingRequestHeaderException e) {
        String code = switch (e.getHeaderName()) {
            case "Idempotency-Key" -> "IDEMPOTENCY_KEY_REQUIRED";
            case "If-Match" -> "PRECONDITION_REQUIRED";
            default -> "BAD_REQUEST";
        };
        return respond(HttpStatus.BAD_REQUEST, code, "Missing required header " + e.getHeaderName() + ".", Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> denied(AccessDeniedException e) {
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "You don't have permission to do that.", Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> noResource(NoResourceFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found.", Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> method(HttpRequestMethodNotSupportedException e) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed.", Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException e) {
        ApiException mapped = mapConstraint(e);
        if (mapped != null) {
            return api(mapped);
        }
        log.warn("unmapped integrity violation: {}", e.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, "CONSTRAINT_VIOLATION",
                "The change conflicts with existing data or a business rule.", Map.of());
    }

    public static ApiException mapConstraint(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
                ApiException m = CONSTRAINTS.get(cve.getConstraintName());
                if (m != null) {
                    return m;
                }
            }
            String msg = String.valueOf(t.getMessage());
            for (Map.Entry<String, ApiException> en : CONSTRAINTS.entrySet()) {
                if (msg.contains(en.getKey())) {
                    return en.getValue();
                }
            }
        }
        return null;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unhandled(Exception e) {
        log.error("unhandled error", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong. Please try again.",
                Map.of());
    }
}
