package com.returnright.refunds;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Idempotent in-memory gateway whose behaviour can be scripted call by call. */
class FakePaymentGateway implements PaymentGateway {

  enum Behaviour {
    SUCCEED,
    DECLINE,
    /** Times out before the payout happens. */
    TIMEOUT_BEFORE_PROCESSING,
    /** Pays out, then the response is lost. */
    TIMEOUT_AFTER_PROCESSING
  }

  private final Deque<Behaviour> script = new ArrayDeque<>();
  private final Map<String, GatewayRefundResponse> processedByKey = new HashMap<>();
  private final List<GatewayRefundRequest> calls = new ArrayList<>();
  private int payoutCount;
  private long totalPaidPaise;

  void nextCalls(Behaviour... behaviours) {
    script.addAll(List.of(behaviours));
  }

  @Override
  public GatewayRefundResponse refund(GatewayRefundRequest request) {
    calls.add(request);
    Behaviour behaviour = script.isEmpty() ? Behaviour.SUCCEED : script.poll();
    if (behaviour == Behaviour.TIMEOUT_BEFORE_PROCESSING) {
      throw new GatewayTimeoutException("timed out before processing");
    }
    GatewayRefundResponse response =
        processedByKey.computeIfAbsent(
            request.idempotencyKey(), key -> process(request, behaviour));
    if (behaviour == Behaviour.TIMEOUT_AFTER_PROCESSING) {
      throw new GatewayTimeoutException("response lost");
    }
    return response;
  }

  private GatewayRefundResponse process(GatewayRefundRequest request, Behaviour behaviour) {
    if (behaviour == Behaviour.DECLINE) {
      return GatewayRefundResponse.declined("INSUFFICIENT_FUNDS");
    }
    payoutCount++;
    totalPaidPaise += request.amountPaise();
    return GatewayRefundResponse.success("txn-" + payoutCount);
  }

  List<GatewayRefundRequest> calls() {
    return calls;
  }

  int payoutCount() {
    return payoutCount;
  }

  long totalPaidPaise() {
    return totalPaidPaise;
  }
}
