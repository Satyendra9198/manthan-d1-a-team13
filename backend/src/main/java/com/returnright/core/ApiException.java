package com.returnright.core;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Business error rendered as {@code {"error":{code,message,details,correlation_id}}}. */
public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Map<String, Object> details;

    public ApiException(String code, String message, HttpStatus status) {
        this(code, message, status, Map.of());
    }

    public ApiException(String code, String message, HttpStatus status, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details == null ? Map.of() : details;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public Map<String, Object> details() {
        return details;
    }

    public static ApiException notFound(String what) {
        return new ApiException("NOT_FOUND", what + " not found.", HttpStatus.NOT_FOUND);
    }

    public static ApiException conflict(String code, String message, Map<String, Object> details) {
        return new ApiException(code, message, HttpStatus.CONFLICT, details);
    }

    public static ApiException unprocessable(String code, String message, Map<String, Object> details) {
        return new ApiException(code, message, HttpStatus.UNPROCESSABLE_CONTENT, details);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(code, message, HttpStatus.BAD_REQUEST);
    }
}
