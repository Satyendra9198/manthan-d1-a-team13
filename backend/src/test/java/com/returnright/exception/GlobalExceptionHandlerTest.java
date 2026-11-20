package com.returnright.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.returnright.model.dto.response.ErrorResponse;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

  @Test
  void orderNotFoundIsReportedAsNotFound() {
    ResponseEntity<ErrorResponse> response =
        handler.handleOrderNotFound(new OrderNotFoundException());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo("ORDER_NOT_FOUND");
  }

  @Test
  void rejectedSelectionIsReportedAsUnprocessableWithEveryProblem() {
    List<String> problems = List.of("item-1: not part of the order", "item-2: window closed");

    ResponseEntity<ErrorResponse> response =
        handler.handleItemSelectionRejected(new ItemSelectionRejectedException(problems));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody().code()).isEqualTo("SELECTION_NOT_RETURNABLE");
    assertThat(response.getBody().details()).isEqualTo(problems);
  }

  @Test
  void invalidPhotosAreReportedAsUnprocessableWithEveryProblem() {
    List<String> problems = List.of("item-1: Photos must be JPEG, PNG or WebP images.");

    ResponseEntity<ErrorResponse> response =
        handler.handleInvalidPhotos(new InvalidPhotosException(problems));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody().code()).isEqualTo("INVALID_PHOTOS");
    assertThat(response.getBody().details()).isEqualTo(problems);
  }

  @Test
  void oversizedUploadIsReportedAsPayloadTooLarge() {
    ResponseEntity<ErrorResponse> response =
        handler.handleUploadTooLarge(new MaxUploadSizeExceededException(5 * 1024 * 1024));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    assertThat(response.getBody().code()).isEqualTo("UPLOAD_TOO_LARGE");
  }
}
