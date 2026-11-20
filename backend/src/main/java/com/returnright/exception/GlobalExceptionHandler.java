package com.returnright.exception;

import com.returnright.model.dto.response.ErrorResponse;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps exceptions to the shared {@link ErrorResponse} format. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private final Clock clock;

  public GlobalExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(IllegalTransitionException.class)
  public ResponseEntity<ErrorResponse> handleIllegalTransition(IllegalTransitionException ex) {
    return error(HttpStatus.CONFLICT, "ILLEGAL_TRANSITION", ex.getMessage(), List.of());
  }

  @ExceptionHandler(TransitionNotPermittedException.class)
  public ResponseEntity<ErrorResponse> handleTransitionNotPermitted(
      TransitionNotPermittedException ex) {
    return error(HttpStatus.FORBIDDEN, "TRANSITION_NOT_PERMITTED", ex.getMessage(), List.of());
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex) {
    return error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", ex.getMessage(), List.of());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleInvalidRequest(IllegalArgumentException ex) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), List.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    List<String> details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
            .toList();
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request is invalid", details);
  }

  @ExceptionHandler({
    MissingRequestHeaderException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class
  })
  public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex) {
    return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", ex.getMessage(), List.of());
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), List.of());
  }

  @ExceptionHandler(BusinessRuleException.class)
  public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex) {
    return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getCode(), ex.getMessage(), ex.getDetails());
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ErrorResponse> handleConcurrentUpdate(
      OptimisticLockingFailureException ex) {
    return error(
        HttpStatus.CONFLICT,
        "CONCURRENT_UPDATE",
        "The return was changed by someone else. Reload and try again.",
        List.of());
  }

  private ResponseEntity<ErrorResponse> error(
      HttpStatus status, String code, String message, List<String> details) {
    return ResponseEntity.status(status)
        .body(new ErrorResponse(code, message, details, clock.instant()));
  }
}
