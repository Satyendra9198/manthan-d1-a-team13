package com.returnright.service;

import com.returnright.model.domain.IssueRefundCommand;
import com.returnright.model.entity.Refund;

/**
 * Issues refunds exactly once per return (assumptions A-15, A-16; ADR-001).
 *
 * <p>Rules: a repeated idempotency key returns the stored refund; a second refund for the same
 * return returns the existing one; every gateway attempt reuses the stored refund's key; a timeout
 * leaves the refund {@code UNKNOWN} so it can be safely re-driven.
 */
public interface RefundService {

  Refund issueRefund(IssueRefundCommand command);

  /**
   * Re-drives every {@code PENDING} or {@code UNKNOWN} refund through the gateway with its stored
   * key.
   *
   * @return number of refunds that reached a final state
   */
  int reconcileUnresolvedRefunds();
}
