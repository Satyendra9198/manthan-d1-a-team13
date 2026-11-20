package com.returnright.refunds;

import java.util.UUID;

/** Raised when the unique refund-per-return constraint is violated (e.g. a concurrent request). */
public class DuplicateRefundException extends RuntimeException {

  public DuplicateRefundException(UUID returnId) {
    super("A refund already exists for return " + returnId);
  }
}
