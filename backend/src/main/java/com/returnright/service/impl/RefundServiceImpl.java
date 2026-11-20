package com.returnright.service.impl;

import com.returnright.client.PaymentGatewayClient;
import com.returnright.client.dto.GatewayRefundRequest;
import com.returnright.client.dto.GatewayRefundResponse;
import com.returnright.exception.GatewayTimeoutException;
import com.returnright.exception.IdempotencyConflictException;
import com.returnright.model.domain.IssueRefundCommand;
import com.returnright.model.entity.Refund;
import com.returnright.model.enums.RefundMethod;
import com.returnright.model.enums.RefundStatus;
import com.returnright.repository.RefundRepository;
import com.returnright.service.RefundService;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Exactly-once refunds (ADR-001). The refund row is committed as {@code PENDING} before the gateway
 * is called, so a crash leaves a record that reconciliation can finish. The gateway call is never
 * made inside a database transaction.
 */
@Service
public class RefundServiceImpl implements RefundService {

  private static final Set<RefundStatus> FINAL_STATUSES =
      EnumSet.of(RefundStatus.SUCCEEDED, RefundStatus.FAILED);
  private static final Set<RefundStatus> UNRESOLVED_STATUSES =
      EnumSet.of(RefundStatus.PENDING, RefundStatus.UNKNOWN);

  private final RefundRepository refundRepository;
  private final PaymentGatewayClient paymentGatewayClient;

  public RefundServiceImpl(
      RefundRepository refundRepository, PaymentGatewayClient paymentGatewayClient) {
    this.refundRepository = refundRepository;
    this.paymentGatewayClient = paymentGatewayClient;
  }

  @Override
  public Refund issueRefund(IssueRefundCommand command) {
    validate(command);

    Optional<Refund> sameKey = refundRepository.findByIdempotencyKey(command.idempotencyKey());
    if (sameKey.isPresent()) {
      if (!isSameRequest(sameKey.get(), command)) {
        throw new IdempotencyConflictException(command.idempotencyKey());
      }
      return settle(sameKey.get());
    }

    Optional<Refund> sameReturn = refundRepository.findByReturnId(command.returnId());
    if (sameReturn.isPresent()) {
      return settle(sameReturn.get());
    }

    Refund refund = Refund.pending(command);
    try {
      refundRepository.saveAndFlush(refund);
    } catch (DataIntegrityViolationException raceLost) {
      Refund winner =
          refundRepository.findByReturnId(command.returnId()).orElseThrow(() -> raceLost);
      return settle(winner);
    }
    return settle(refund);
  }

  @Override
  public int reconcileUnresolvedRefunds() {
    int settled = 0;
    for (Refund refund : refundRepository.findByStatusIn(UNRESOLVED_STATUSES)) {
      if (FINAL_STATUSES.contains(settle(refund).getStatus())) {
        settled++;
      }
    }
    return settled;
  }

  /** Drives a refund to a final state if it is not already in one. */
  private Refund settle(Refund refund) {
    if (FINAL_STATUSES.contains(refund.getStatus())) {
      return refund;
    }
    if (refund.getMethod() == RefundMethod.STORE_CREDIT) {
      refund.markSucceeded(null);
      return refundRepository.saveAndFlush(refund);
    }
    try {
      GatewayRefundResponse response =
          paymentGatewayClient.refund(
              new GatewayRefundRequest(
                  refund.getIdempotencyKey(), refund.getReturnId(), refund.getAmountPaise()));
      if (response.succeeded()) {
        refund.markSucceeded(response.transactionId());
      } else {
        refund.markFailed(response.declineReason());
      }
    } catch (GatewayTimeoutException timeout) {
      refund.markUnknown();
    }
    return refundRepository.saveAndFlush(refund);
  }

  private static boolean isSameRequest(Refund refund, IssueRefundCommand command) {
    return refund.getReturnId().equals(command.returnId())
        && refund.getAmountPaise() == command.amountPaise()
        && refund.getMethod() == command.method();
  }

  private static void validate(IssueRefundCommand command) {
    if (command.returnId() == null || command.method() == null) {
      throw new IllegalArgumentException("Return and refund method are required");
    }
    if (command.amountPaise() <= 0) {
      throw new IllegalArgumentException("Refund amount must be positive");
    }
    if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
      throw new IllegalArgumentException("An idempotency key is required");
    }
  }
}
