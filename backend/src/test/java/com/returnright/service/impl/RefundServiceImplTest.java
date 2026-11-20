package com.returnright.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.returnright.client.dto.GatewayRefundRequest;
import com.returnright.exception.IdempotencyConflictException;
import com.returnright.model.domain.IssueRefundCommand;
import com.returnright.model.entity.Refund;
import com.returnright.model.enums.RefundMethod;
import com.returnright.model.enums.RefundStatus;
import com.returnright.support.FakePaymentGatewayClient;
import com.returnright.support.FakePaymentGatewayClient.Behaviour;
import com.returnright.support.InMemoryRefundRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefundServiceImplTest {

  private static final UUID RETURN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final long AMOUNT = 450_000;

  private final InMemoryRefundRepository repository = InMemoryRefundRepository.create();
  private final FakePaymentGatewayClient gateway = new FakePaymentGatewayClient();
  private final RefundServiceImpl service = new RefundServiceImpl(repository, gateway);

  @Test
  void refundToOriginalPaymentSucceedsAndPaysOnce() {
    Refund refund = service.issueRefund(command("key-1"));

    assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    assertThat(refund.getGatewayTransactionId()).isNotBlank();
    assertThat(repository.get(refund.getId()).getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    assertThat(gateway.payoutCount()).isEqualTo(1);
    assertThat(gateway.totalPaidPaise()).isEqualTo(AMOUNT);
  }

  @Test
  void gatewayReceivesTheClientIdempotencyKey() {
    service.issueRefund(command("key-1"));

    assertThat(gateway.calls())
        .extracting(GatewayRefundRequest::idempotencyKey)
        .containsOnly("key-1");
  }

  @Test
  void doubleClickWithTheSameKeyPaysOnlyOnce() {
    Refund first = service.issueRefund(command("key-1"));
    Refund second = service.issueRefund(command("key-1"));

    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(gateway.calls()).hasSize(1);
    assertThat(gateway.payoutCount()).isEqualTo(1);
  }

  @Test
  void secondRefundForTheSameReturnWithANewKeyReturnsTheExistingRefund() {
    Refund first = service.issueRefund(command("key-1"));
    Refund second = service.issueRefund(command("key-2"));

    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(repository.count()).isEqualTo(1);
    assertThat(gateway.payoutCount()).isEqualTo(1);
  }

  @Test
  void reusingAKeyForADifferentAmountIsRejectedAsAConflict() {
    service.issueRefund(command("key-1"));

    assertThatThrownBy(
            () ->
                service.issueRefund(
                    new IssueRefundCommand(
                        RETURN_ID, AMOUNT + 1, RefundMethod.ORIGINAL_PAYMENT, "key-1")))
        .isInstanceOf(IdempotencyConflictException.class);
    assertThat(gateway.payoutCount()).isEqualTo(1);
  }

  @Test
  void reusingAKeyForADifferentReturnIsRejectedAsAConflict() {
    service.issueRefund(command("key-1"));

    assertThatThrownBy(
            () ->
                service.issueRefund(
                    new IssueRefundCommand(
                        UUID.randomUUID(), AMOUNT, RefundMethod.ORIGINAL_PAYMENT, "key-1")))
        .isInstanceOf(IdempotencyConflictException.class);
  }

  @Test
  void gatewayTimeoutLeavesTheRefundUnknownNotFailed() {
    gateway.nextCalls(Behaviour.TIMEOUT_BEFORE_PROCESSING);

    Refund refund = service.issueRefund(command("key-1"));

    assertThat(refund.getStatus()).isEqualTo(RefundStatus.UNKNOWN);
    assertThat(repository.get(refund.getId()).getStatus()).isEqualTo(RefundStatus.UNKNOWN);
  }

  @Test
  void retryAfterATimeoutReusesTheSameGatewayKeyAndPaysOnce() {
    gateway.nextCalls(Behaviour.TIMEOUT_BEFORE_PROCESSING);
    service.issueRefund(command("key-1"));

    Refund retried = service.issueRefund(command("key-1"));

    assertThat(retried.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    assertThat(gateway.calls()).hasSize(2);
    assertThat(gateway.calls())
        .extracting(GatewayRefundRequest::idempotencyKey)
        .containsOnly("key-1");
    assertThat(gateway.payoutCount()).isEqualTo(1);
  }

  @Test
  void lostGatewayResponseIsRecoveredOnRetryWithoutPayingTwice() {
    gateway.nextCalls(Behaviour.TIMEOUT_AFTER_PROCESSING);
    Refund first = service.issueRefund(command("key-1"));

    Refund retried = service.issueRefund(command("key-1"));

    assertThat(first.getStatus()).isEqualTo(RefundStatus.UNKNOWN);
    assertThat(retried.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    assertThat(retried.getGatewayTransactionId()).isEqualTo("txn-1");
    assertThat(gateway.payoutCount()).isEqualTo(1);
    assertThat(gateway.totalPaidPaise()).isEqualTo(AMOUNT);
  }

  @Test
  void retryWithANewClientKeyAfterATimeoutStillSendsTheOriginalGatewayKey() {
    gateway.nextCalls(Behaviour.TIMEOUT_AFTER_PROCESSING);
    service.issueRefund(command("key-1"));

    Refund retried = service.issueRefund(command("key-2"));

    assertThat(retried.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    assertThat(gateway.calls())
        .extracting(GatewayRefundRequest::idempotencyKey)
        .containsOnly("key-1");
    assertThat(gateway.payoutCount()).isEqualTo(1);
  }

  @Test
  void declinedRefundIsMarkedFailedWithTheGatewayReason() {
    gateway.nextCalls(Behaviour.DECLINE);

    Refund refund = service.issueRefund(command("key-1"));

    assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
    assertThat(refund.getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
    assertThat(gateway.payoutCount()).isZero();
  }

  @Test
  void retryOfAFailedRefundReturnsItWithoutCallingTheGatewayAgain() {
    gateway.nextCalls(Behaviour.DECLINE);
    service.issueRefund(command("key-1"));

    Refund retried = service.issueRefund(command("key-1"));

    assertThat(retried.getStatus()).isEqualTo(RefundStatus.FAILED);
    assertThat(gateway.calls()).hasSize(1);
  }

  @Test
  void storeCreditRefundSucceedsWithoutCallingTheGateway() {
    Refund refund =
        service.issueRefund(
            new IssueRefundCommand(RETURN_ID, AMOUNT, RefundMethod.STORE_CREDIT, "key-1"));

    assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    assertThat(gateway.calls()).isEmpty();
  }

  @Test
  void requestThatLosesTheUniqueConstraintRaceReturnsTheWinningRefund() {
    Refund winner =
        new Refund(
            UUID.randomUUID(),
            RETURN_ID,
            AMOUNT,
            RefundMethod.ORIGINAL_PAYMENT,
            "winner-key",
            RefundStatus.SUCCEEDED,
            "txn-winner",
            null);
    repository.simulateConcurrentInsert(winner);

    Refund refund = service.issueRefund(command("loser-key"));

    assertThat(refund.getId()).isEqualTo(winner.getId());
    assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    assertThat(gateway.calls()).isEmpty();
  }

  @Test
  void zeroAmountRefundIsRefused() {
    assertThatThrownBy(
            () ->
                service.issueRefund(
                    new IssueRefundCommand(RETURN_ID, 0, RefundMethod.ORIGINAL_PAYMENT, "key-1")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refundWithoutAnIdempotencyKeyIsRefused() {
    assertThatThrownBy(
            () ->
                service.issueRefund(
                    new IssueRefundCommand(RETURN_ID, AMOUNT, RefundMethod.ORIGINAL_PAYMENT, " ")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reconciliationSettlesAnUnknownRefundWithItsStoredKey() {
    gateway.nextCalls(Behaviour.TIMEOUT_AFTER_PROCESSING);
    Refund refund = service.issueRefund(command("key-1"));

    int settled = service.reconcileUnresolvedRefunds();

    assertThat(settled).isEqualTo(1);
    assertThat(repository.get(refund.getId()).getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    assertThat(gateway.calls())
        .extracting(GatewayRefundRequest::idempotencyKey)
        .containsOnly("key-1");
    assertThat(gateway.payoutCount()).isEqualTo(1);
  }

  @Test
  void reconciliationLeavesTheRefundUnknownWhileTheGatewayStillTimesOut() {
    gateway.nextCalls(Behaviour.TIMEOUT_BEFORE_PROCESSING, Behaviour.TIMEOUT_BEFORE_PROCESSING);
    Refund refund = service.issueRefund(command("key-1"));

    int settled = service.reconcileUnresolvedRefunds();

    assertThat(settled).isZero();
    assertThat(repository.get(refund.getId()).getStatus()).isEqualTo(RefundStatus.UNKNOWN);
    assertThat(gateway.payoutCount()).isZero();
  }

  @Test
  void reconciliationCompletesARefundLeftPendingByACrashBeforeTheGatewayCall() {
    Refund pending = Refund.pending(command("key-1"));
    repository.save(pending);

    int settled = service.reconcileUnresolvedRefunds();

    assertThat(settled).isEqualTo(1);
    assertThat(repository.get(pending.getId()).getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    assertThat(gateway.payoutCount()).isEqualTo(1);
  }

  @Test
  void reconciliationIgnoresSettledRefunds() {
    service.issueRefund(command("key-1"));

    int settled = service.reconcileUnresolvedRefunds();

    assertThat(settled).isZero();
    assertThat(gateway.calls()).hasSize(1);
  }

  private static IssueRefundCommand command(String idempotencyKey) {
    return new IssueRefundCommand(RETURN_ID, AMOUNT, RefundMethod.ORIGINAL_PAYMENT, idempotencyKey);
  }
}
