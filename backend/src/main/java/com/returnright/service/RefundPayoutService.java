package com.returnright.service;

import com.returnright.model.dto.response.ReconciliationResponse;
import java.util.UUID;

/**
 * Pays out a return in {@code REFUND_PENDING} and moves it to {@code REFUNDED} once the refund
 * succeeds. The gateway call happens outside any database transaction.
 */
public interface RefundPayoutService {

  /**
   * @return true if the return reached {@code REFUNDED}
   */
  boolean payout(UUID returnId, String idempotencyKey);

  /** Re-drives unresolved refunds and completes returns whose refund has succeeded. */
  ReconciliationResponse reconcile();

  /** Key used when the system starts a refund on its own (no finance approval needed). */
  static String systemIdempotencyKey(UUID returnId) {
    return "refund-" + returnId;
  }
}
