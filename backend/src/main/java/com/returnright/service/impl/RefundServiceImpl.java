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
 * Pays each return at most once (ADR-001). The refund row is saved as {@code PENDING} before the
 * gateway is called, and every gateway attempt reuses the key stored on that row, so a retry after
 * a lost response is answered by the gateway's own idempotency instead of paying again.
 *
 * <p>Deliberately not transactional: each save commits on its own, so the pending row survives a
 * crash during the gateway call and can be reconciled later.
 */
@Service
public class RefundServiceImpl implements RefundService {

  private static final Set<RefundStatus> UNRESOLVED =
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
      if (!matches(sameKey.get(), command)) {
        throw new IdempotencyConflictException(command.idempotencyKey());
      }
      return settle(sameKey.get());
    }

    Optional<Refund> sameReturn = refundRepository.findByReturnId(command.returnId());
    if (sameReturn.isPresent()) {
      return settle(sameReturn.get());
    }

    Refund pending = Refund.pending(command);
    try {
      refundRepository.saveAndFlush(pending);
    } catch (DataIntegrityViolationException lostRace) {
      return settle(
          refundRepository.findByReturnId(command.returnId()).orElseThrow(() -> lostRace));
    }
    return settle(pending);
  }

  @Override
  public int reconcileUnresolvedRefunds() {
    int settled = 0;
    for (Refund refund : refundRepository.findByStatusIn(UNRESOLVED)) {
      if (!UNRESOLVED.contains(settle(refund).getStatus())) {
        settled++;
      }
    }
    return settled;
  }

  /** Drives an unresolved refund through the gateway; a settled one is returned unchanged. */
  private Refund settle(Refund refund) {
    if (!UNRESOLVED.contains(refund.getStatus())) {
      return refund;
    }
    if (refund.getMethod() == RefundMethod.STORE_CREDIT) {
      refund.markSucceeded("STORE-CREDIT-" + refund.getId());
      return refundRepository.save(refund);
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
    return refundRepository.save(refund);
  }

  private static boolean matches(Refund existing, IssueRefundCommand command) {
    return existing.getReturnId().equals(command.returnId())
        && existing.getAmountPaise() == command.amountPaise()
        && existing.getMethod() == command.method();
  }

  private static void validate(IssueRefundCommand command) {
    if (command.amountPaise() <= 0) {
      throw new IllegalArgumentException("Refund amount must be positive");
    }
    if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
      throw new IllegalArgumentException("An idempotency key is required");
    }
  }
}
