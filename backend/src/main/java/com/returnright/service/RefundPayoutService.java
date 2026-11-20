package com.returnright.service;

import com.returnright.model.enums.RefundStatus;
import java.util.Optional;
import java.util.UUID;

/** Pays out refunds for returns in {@code REFUND_PENDING} and confirms them once paid. */
public interface RefundPayoutService {

  /**
   * Issues the return's refund and moves the return to {@code REFUNDED} when it succeeds. Safe to
   * call again: the refund is keyed on the return, so a repeat never pays twice.
   *
   * @return the refund's status, or empty if the return is not waiting for a refund
   */
  Optional<RefundStatus> payOut(UUID returnId);

  /**
   * Re-drives every return still in {@code REFUND_PENDING}, e.g. after a gateway timeout.
   *
   * @return number of returns that reached {@code REFUNDED}
   */
  int payOutPendingRefunds();
}
