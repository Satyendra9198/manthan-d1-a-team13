package com.returnright.refunds;

/** The idempotency key was already used for a different refund request. */
public class IdempotencyConflictException extends RuntimeException {

  public IdempotencyConflictException(String idempotencyKey) {
    super("Idempotency key " + idempotencyKey + " was already used with a different request");
  }
}
