package com.returnright.refunds;

/**
 * Refund lifecycle. {@code UNKNOWN} means the gateway outcome is not known (timeout); it is never
 * treated as failed (assumption A-16).
 */
public enum RefundStatus {
  PENDING,
  SUCCEEDED,
  FAILED,
  UNKNOWN
}
