package com.returnright.service.impl;

import com.returnright.exception.ResourceNotFoundException;
import com.returnright.model.domain.Actor;
import com.returnright.model.domain.IssueRefundCommand;
import com.returnright.model.dto.response.ReconciliationResponse;
import com.returnright.model.entity.Refund;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.RefundStatus;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.repository.ReturnRequestRepository;
import com.returnright.service.RefundPayoutService;
import com.returnright.service.RefundService;
import com.returnright.service.ReturnTransitionService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Three steps, each short: read the return (transaction 1), issue the refund through the gateway
 * (no transaction held), confirm the return (transaction 2). A crash between steps leaves a
 * PENDING/UNKNOWN refund or a REFUND_PENDING return that {@link #reconcile()} finishes.
 */
@Service
public class RefundPayoutServiceImpl implements RefundPayoutService {

  private final ReturnRequestRepository returnRequestRepository;
  private final RefundService refundService;
  private final ReturnTransitionService transitionService;
  private final TransactionTemplate transactionTemplate;

  public RefundPayoutServiceImpl(
      ReturnRequestRepository returnRequestRepository,
      RefundService refundService,
      ReturnTransitionService transitionService,
      TransactionTemplate transactionTemplate) {
    this.returnRequestRepository = returnRequestRepository;
    this.refundService = refundService;
    this.transitionService = transitionService;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public boolean payout(UUID returnId, String idempotencyKey) {
    IssueRefundCommand command =
        transactionTemplate.execute(
            status -> {
              ReturnRequest returnRequest = load(returnId);
              if (returnRequest.getStatus() != ReturnStatus.REFUND_PENDING) {
                return null;
              }
              return new IssueRefundCommand(
                  returnId,
                  returnRequest.getRefundAmountPaise(),
                  returnRequest.getRefundMethod(),
                  idempotencyKey);
            });
    if (command == null) {
      return false;
    }

    Refund refund = refundService.issueRefund(command);
    if (refund.getStatus() != RefundStatus.SUCCEEDED) {
      return false;
    }
    return Boolean.TRUE.equals(transactionTemplate.execute(status -> confirm(returnId, refund)));
  }

  @Override
  public ReconciliationResponse reconcile() {
    int refundsSettled = refundService.reconcileUnresolvedRefunds();
    List<UUID> pendingReturns =
        transactionTemplate.execute(
            status ->
                returnRequestRepository
                    .findByStatusOrderByCreatedAtAsc(ReturnStatus.REFUND_PENDING)
                    .stream()
                    .map(ReturnRequest::getId)
                    .toList());

    int returnsCompleted = 0;
    for (UUID returnId : pendingReturns) {
      if (payout(returnId, RefundPayoutService.systemIdempotencyKey(returnId))) {
        returnsCompleted++;
      }
    }
    return new ReconciliationResponse(refundsSettled, returnsCompleted);
  }

  private boolean confirm(UUID returnId, Refund refund) {
    ReturnRequest returnRequest = load(returnId);
    if (returnRequest.getStatus() != ReturnStatus.REFUND_PENDING) {
      return false;
    }
    transitionService.apply(
        returnRequest,
        ReturnEvent.CONFIRM_REFUND,
        Actor.system(),
        "Refund " + refund.getId() + " succeeded (" + refund.getMethod() + ")");
    return true;
  }

  private ReturnRequest load(UUID returnId) {
    return returnRequestRepository
        .findById(returnId)
        .orElseThrow(() -> new ResourceNotFoundException("Return " + returnId + " not found"));
  }
}
