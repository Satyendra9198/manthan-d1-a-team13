package com.returnright.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.returnright.model.dto.response.ErrorResponse;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-20T06:30:00Z");

  private final GlobalExceptionHandler handler =
      new GlobalExceptionHandler(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void illegalTransitionIsReportedAsConflict() {
    ResponseEntity<ErrorResponse> response =
        handler.handleIllegalTransition(
            new IllegalTransitionException(ReturnStatus.CANCELLED, ReturnEvent.RECEIVE));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().code()).isEqualTo("ILLEGAL_TRANSITION");
    assertThat(response.getBody().timestamp()).isEqualTo(NOW);
  }

  @Test
  void transitionByWrongRoleIsReportedAsForbidden() {
    ResponseEntity<ErrorResponse> response =
        handler.handleTransitionNotPermitted(
            new TransitionNotPermittedException(ReturnEvent.FINANCE_APPROVE, Role.CUSTOMER));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().code()).isEqualTo("TRANSITION_NOT_PERMITTED");
  }

  @Test
  void reusedIdempotencyKeyIsReportedAsConflict() {
    ResponseEntity<ErrorResponse> response =
        handler.handleIdempotencyConflict(new IdempotencyConflictException("key-1"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().code()).isEqualTo("IDEMPOTENCY_CONFLICT");
  }

  @Test
  void invalidArgumentIsReportedAsBadRequest() {
    ResponseEntity<ErrorResponse> response =
        handler.handleInvalidRequest(new IllegalArgumentException("amount must be positive"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
    assertThat(response.getBody().message()).isEqualTo("amount must be positive");
  }
}
