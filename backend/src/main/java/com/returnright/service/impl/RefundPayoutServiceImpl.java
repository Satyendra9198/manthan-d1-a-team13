package com.returnright.service.impl;

import com.returnright.model.domain.IssueRefundCommand;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.RefundStatus;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import com.returnright.repository.ReturnRequestRepository;
import com.returnright.service.RefundPayoutService;
import com.returnright.service.RefundService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs outside any caller's transaction: the return is read in one short transaction, the refund
 * service commits its own rows around the gateway call, and the return is confirmed in a second
 * short transaction. A crash or timeout in between leaves the return in {@code REFUND_PENDING}, and
 * the scheduled job finishes it with the same refund key.
 */
@Service
public class RefundPayoutServiceImpl implements RefundPayoutService {

  private static final Logger log = LoggerFactory.getLogger(RefundPayoutServiceImpl.class);

  private final ReturnRequestRepository returnRequestRepository;
  private final RefundService refundService;
  private final ReturnTransitions returnTransitions;
  private final TransactionTemplate transactionTemplate;

  public RefundPayoutServiceImpl(
      ReturnRequestRepository returnRequestRepository,
      RefundService refundService,
      ReturnTransitions returnTransitions,
      TransactionTemplate transactionTemplate) {
    this.returnRequestRepository = returnRequestRepository;
    this.refundService = refundService;
    this.returnTransitions = returnTransitions;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public Optional<RefundStatus> payOut(UUID returnId) {
    Optional<IssueRefundCommand> command =
        transactionTemplate.execute(status -> refundCommandFor(returnId));
    if (command == null || command.isEmpty()) {
      return Optional.empty();
    }
    RefundStatus refundStatus =
        command.get().amountPaise() == 0
            ? RefundStatus.SUCCEEDED
            : refundService.issueRefund(command.get()).getStatus();
    if (refundStatus == RefundStatus.SUCCEEDED) {
      transactionTemplate.executeWithoutResult(status -> confirm(returnId));
    }
    return Optional.of(refundStatus);
  }

  @Override
  @Scheduled(
      initialDelayString = "${returnright.refunds.reconcile-interval:PT1M}",
      fixedDelayString = "${returnright.refunds.reconcile-interval:PT1M}")
  public int payOutPendingRefunds() {
    List<UUID> pending =
        transactionTemplate.execute(
            status -> returnRequestRepository.findIdsByStatus(ReturnStatus.REFUND_PENDING));
    int refunded = 0;
    for (UUID returnId : pending == null ? List.<UUID>of() : pending) {
      try {
        if (payOut(returnId).filter(RefundStatus.SUCCEEDED::equals).isPresent()) {
          refunded++;
        }
      } catch (RuntimeException ex) {
        log.warn("Refund payout for return {} failed; it will be retried", returnId, ex);
      }
    }
    return refunded;
  }

  private Optional<IssueRefundCommand> refundCommandFor(UUID returnId) {
    return returnRequestRepository
        .findById(returnId)
        .filter(returnRequest -> returnRequest.getStatus() == ReturnStatus.REFUND_PENDING)
        .map(
            returnRequest ->
                new IssueRefundCommand(
                    returnRequest.getId(),
                    returnRequest.getRefundAmountPaise(),
                    returnRequest.getRefundMethod(),
                    refundKeyFor(returnRequest.getId())));
  }

  private void confirm(UUID returnId) {
    ReturnRequest returnRequest = returnRequestRepository.findById(returnId).orElseThrow();
    if (returnRequest.getStatus() == ReturnStatus.REFUND_PENDING) {
      returnTransitions.apply(returnRequest, ReturnEvent.CONFIRM_REFUND, Role.SYSTEM, null);
    }
  }

  /** One key per return, so every attempt for the return reaches the gateway with the same key. */
  public static String refundKeyFor(UUID returnId) {
    return "return-" + returnId;
  }
}
