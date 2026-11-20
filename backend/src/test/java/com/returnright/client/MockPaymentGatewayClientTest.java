package com.returnright.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.returnright.client.dto.GatewayRefundRequest;
import com.returnright.client.dto.GatewayRefundResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MockPaymentGatewayClientTest {

  private final MockPaymentGatewayClient gateway = new MockPaymentGatewayClient();

  @Test
  void refundSucceedsWithATransactionId() {
    GatewayRefundResponse response =
        gateway.refund(new GatewayRefundRequest("key-1", UUID.randomUUID(), 189_900));

    assertThat(response.succeeded()).isTrue();
    assertThat(response.transactionId()).isEqualTo("MOCK-TXN-000001");
  }

  @Test
  void repeatedKeyReturnsTheFirstAnswerWithoutANewTransaction() {
    UUID returnId = UUID.randomUUID();
    GatewayRefundResponse first = gateway.refund(new GatewayRefundRequest("key-1", returnId, 100));
    GatewayRefundResponse second = gateway.refund(new GatewayRefundRequest("key-1", returnId, 100));

    assertThat(second).isEqualTo(first);
  }

  @Test
  void differentKeysGetDifferentTransactions() {
    GatewayRefundResponse first =
        gateway.refund(new GatewayRefundRequest("key-1", UUID.randomUUID(), 100));
    GatewayRefundResponse second =
        gateway.refund(new GatewayRefundRequest("key-2", UUID.randomUUID(), 100));

    assertThat(second.transactionId()).isNotEqualTo(first.transactionId());
  }
}
