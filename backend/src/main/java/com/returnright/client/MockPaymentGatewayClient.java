package com.returnright.client;

import com.returnright.client.dto.GatewayRefundRequest;
import com.returnright.client.dto.GatewayRefundResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * In-process stand-in for the payment gateway, for local runs and demos. Like the real gateway it
 * is idempotent on the key: repeating a key returns the first answer without paying again. Every
 * payout succeeds. State lives in memory and is lost on restart.
 */
@Component
public class MockPaymentGatewayClient implements PaymentGatewayClient {

  private final Map<String, GatewayRefundResponse> responsesByKey = new ConcurrentHashMap<>();
  private final AtomicLong transactionCounter = new AtomicLong();

  @Override
  public GatewayRefundResponse refund(GatewayRefundRequest request) {
    return responsesByKey.computeIfAbsent(
        request.idempotencyKey(),
        key ->
            GatewayRefundResponse.success(
                String.format("MOCK-TXN-%06d", transactionCounter.incrementAndGet())));
  }
}
