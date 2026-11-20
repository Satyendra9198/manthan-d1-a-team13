package com.returnright.client;

import com.returnright.client.dto.GatewayRefundRequest;
import com.returnright.client.dto.GatewayRefundResponse;
import com.returnright.exception.GatewayTimeoutException;

/**
 * Client for the mock payment gateway service. The gateway is idempotent on {@link
 * GatewayRefundRequest#idempotencyKey()}: repeating a key returns the original result.
 */
public interface PaymentGatewayClient {

  /**
   * @throws GatewayTimeoutException when no response arrives in time; the payout may or may not
   *     have happened
   */
  GatewayRefundResponse refund(GatewayRefundRequest request);
}
