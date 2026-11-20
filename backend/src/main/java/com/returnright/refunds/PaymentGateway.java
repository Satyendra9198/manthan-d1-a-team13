package com.returnright.refunds;

/**
 * Port to the mock payment gateway. The gateway is idempotent on {@link
 * GatewayRefundRequest#idempotencyKey()}: repeating a key returns the original result.
 */
public interface PaymentGateway {

  /**
   * @throws GatewayTimeoutException when no response arrives in time; the payout may or may not
   *     have happened
   */
  GatewayRefundResponse refund(GatewayRefundRequest request);
}
