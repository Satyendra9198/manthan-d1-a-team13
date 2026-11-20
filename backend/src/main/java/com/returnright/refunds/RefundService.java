package com.returnright.refunds;

/**
 * Issues refunds exactly once per return (assumptions A-15, A-16; ADR-001).
 *
 * <p>Rules: a repeated idempotency key returns the stored refund; a second refund for the same
 * return returns the existing one; every gateway attempt reuses the stored refund's key; a timeout
 * leaves the refund {@code UNKNOWN} so it can be safely re-driven.
 */
public class RefundService {

  private final RefundRepository refundRepository;
  private final PaymentGateway paymentGateway;

  public RefundService(RefundRepository refundRepository, PaymentGateway paymentGateway) {
    this.refundRepository = refundRepository;
    this.paymentGateway = paymentGateway;
  }

  public Refund issueRefund(IssueRefundCommand command) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  /**
   * Re-drives every {@code PENDING} or {@code UNKNOWN} refund through the gateway with its stored
   * key.
   *
   * @return number of refunds that reached a final state
   */
  public int reconcileUnresolvedRefunds() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
