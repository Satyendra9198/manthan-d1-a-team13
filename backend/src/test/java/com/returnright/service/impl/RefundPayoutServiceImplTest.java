package com.returnright.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.returnright.client.dto.GatewayRefundRequest;
import com.returnright.model.entity.CustomerOrder;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.RefundMethod;
import com.returnright.model.enums.RefundStatus;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import com.returnright.repository.ReturnRequestRepository;
import com.returnright.support.FakePaymentGatewayClient;
import com.returnright.support.FakePaymentGatewayClient.Behaviour;
import com.returnright.support.InMemoryRefundRepository;
import com.returnright.support.NoOpTransactionManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefundPayoutServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-11-20T06:30:00Z");
  private static final long REFUND_PAISE = 94_950;

  private final ReturnRequestRepository returnRequestRepository =
      mock(ReturnRequestRepository.class);
  private final FakePaymentGatewayClient gateway = new FakePaymentGatewayClient();

  private RefundPayoutServiceImpl service;
  private ReturnRequest returnRequest;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service =
        new RefundPayoutServiceImpl(
            returnRequestRepository,
            new RefundServiceImpl(InMemoryRefundRepository.create(), gateway),
            new ReturnTransitions(new ReturnStateMachineServiceImpl(), clock),
            NoOpTransactionManager.template());
    returnRequest = refundPendingReturn(RefundMethod.ORIGINAL_PAYMENT);
  }

  @Test
  void pendingRefundIsPaidAndTheReturnIsConfirmedAsRefunded() {
    Optional<RefundStatus> result = service.payOut(returnRequest.getId());

    assertThat(result).contains(RefundStatus.SUCCEEDED);
    assertThat(returnRequest.getStatus()).isEqualTo(ReturnStatus.REFUNDED);
    assertThat(gateway.totalPaidPaise()).isEqualTo(REFUND_PAISE);
  }

  @Test
  void everyAttemptForAReturnUsesTheSameRefundKey() {
    gateway.nextCalls(Behaviour.TIMEOUT_BEFORE_PROCESSING);
    service.payOut(returnRequest.getId());
    service.payOut(returnRequest.getId());

    assertThat(gateway.calls())
        .extracting(GatewayRefundRequest::idempotencyKey)
        .containsOnly("return-" + returnRequest.getId());
  }

  @Test
  void gatewayTimeoutLeavesTheReturnWaitingForItsRefund() {
    gateway.nextCalls(Behaviour.TIMEOUT_AFTER_PROCESSING);

    Optional<RefundStatus> result = service.payOut(returnRequest.getId());

    assertThat(result).contains(RefundStatus.UNKNOWN);
    assertThat(returnRequest.getStatus()).isEqualTo(ReturnStatus.REFUND_PENDING);
  }

  @Test
  void reconciliationFinishesATimedOutRefundWithoutPayingTwice() {
    gateway.nextCalls(Behaviour.TIMEOUT_AFTER_PROCESSING);
    service.payOut(returnRequest.getId());
    when(returnRequestRepository.findIdsByStatus(ReturnStatus.REFUND_PENDING))
        .thenReturn(List.of(returnRequest.getId()));

    int refunded = service.payOutPendingRefunds();

    assertThat(refunded).isEqualTo(1);
    assertThat(returnRequest.getStatus()).isEqualTo(ReturnStatus.REFUNDED);
    assertThat(gateway.payoutCount()).isEqualTo(1);
  }

  @Test
  void declinedRefundLeavesTheReturnPendingForFollowUp() {
    gateway.nextCalls(Behaviour.DECLINE);

    assertThat(service.payOut(returnRequest.getId())).contains(RefundStatus.FAILED);
    assertThat(returnRequest.getStatus()).isEqualTo(ReturnStatus.REFUND_PENDING);
  }

  @Test
  void storeCreditIsRefundedWithoutCallingTheGateway() {
    ReturnRequest storeCredit = refundPendingReturn(RefundMethod.STORE_CREDIT);

    assertThat(service.payOut(storeCredit.getId())).contains(RefundStatus.SUCCEEDED);
    assertThat(storeCredit.getStatus()).isEqualTo(ReturnStatus.REFUNDED);
    assertThat(gateway.calls()).isEmpty();
  }

  @Test
  void returnThatIsNotWaitingForARefundIsLeftAlone() {
    returnRequest.moveTo(ReturnStatus.REFUNDED, NOW, Role.SYSTEM, null);

    assertThat(service.payOut(returnRequest.getId())).isEmpty();
    assertThat(gateway.calls()).isEmpty();
  }

  private ReturnRequest refundPendingReturn(RefundMethod method) {
    CustomerOrder order =
        new CustomerOrder(
            UUID.randomUUID(), "RR-10482", "aditi.sharma@example.com", "Aditi Sharma", NOW);
    ReturnRequest created =
        new ReturnRequest(UUID.randomUUID(), "RMA-2026-00731", order, method, false, NOW);
    created.recordInspection(UUID.randomUUID(), NOW, REFUND_PAISE);
    created.moveTo(ReturnStatus.REFUND_PENDING, NOW, Role.SYSTEM, null);
    when(returnRequestRepository.findById(created.getId())).thenReturn(Optional.of(created));
    return created;
  }
}
