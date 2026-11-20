package com.returnright.service.impl;

import com.returnright.exception.ReturnNotFoundException;
import com.returnright.model.domain.StaffActor;
import com.returnright.model.dto.response.FinanceQueueItemResponse;
import com.returnright.model.dto.response.FlaggedReturnResponse;
import com.returnright.model.dto.response.ReviewDecisionResponse;
import com.returnright.model.entity.ReturnItem;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.RefundStatus;
import com.returnright.model.enums.RejectionReason;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.repository.ReturnRequestRepository;
import com.returnright.service.RefundPayoutService;
import com.returnright.service.ReviewService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Finance and operations decisions. A finance approval commits the state change first and then pays
 * out outside that transaction, like the warehouse inspection.
 */
@Service
public class ReviewServiceImpl implements ReviewService {

  private static final Duration FLAG_LOOKBACK = Duration.ofDays(30);

  private final ReturnRequestRepository returnRequestRepository;
  private final ReturnTransitions returnTransitions;
  private final RefundPayoutService refundPayoutService;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  public ReviewServiceImpl(
      ReturnRequestRepository returnRequestRepository,
      ReturnTransitions returnTransitions,
      RefundPayoutService refundPayoutService,
      TransactionTemplate transactionTemplate,
      Clock clock) {
    this.returnRequestRepository = returnRequestRepository;
    this.returnTransitions = returnTransitions;
    this.refundPayoutService = refundPayoutService;
    this.transactionTemplate = transactionTemplate;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public List<FinanceQueueItemResponse> refundsAwaitingFinance() {
    return returnRequestRepository
        .findByStatusOrderByCreatedAtAsc(ReturnStatus.AWAITING_FINANCE_APPROVAL)
        .stream()
        .map(
            returnRequest ->
                new FinanceQueueItemResponse(
                    returnRequest.getRmaNumber(),
                    returnRequest.getOrder().getOrderNumber(),
                    returnRequest.getOrder().getCustomerName(),
                    returnRequest.getRefundAmountPaise(),
                    requestedPaise(returnRequest),
                    returnRequest.getRefundMethod(),
                    returnRequest.getInspectedAt(),
                    returnRequest.getItems().size()))
        .toList();
  }

  @Override
  public ReviewDecisionResponse approveRefund(String rmaNumber, StaffActor reviewer) {
    ReturnRequest approved =
        transactionTemplate.execute(
            status -> decide(rmaNumber, ReturnEvent.FINANCE_APPROVE, reviewer, null, null));
    RefundStatus refundStatus = refundPayoutService.payOut(approved.getId()).orElse(null);
    ReturnStatus statusNow =
        transactionTemplate.execute(status -> load(approved.getRmaNumber()).getStatus());
    return new ReviewDecisionResponse(approved.getRmaNumber(), statusNow, refundStatus);
  }

  @Override
  public ReviewDecisionResponse declineRefund(String rmaNumber, String note, StaffActor reviewer) {
    ReturnRequest declined =
        transactionTemplate.execute(
            status ->
                decide(
                    rmaNumber,
                    ReturnEvent.FINANCE_REJECT,
                    reviewer,
                    RejectionReason.REFUND_DECLINED,
                    note));
    return new ReviewDecisionResponse(declined.getRmaNumber(), declined.getStatus(), null);
  }

  @Override
  @Transactional(readOnly = true)
  public List<FlaggedReturnResponse> flaggedReturns() {
    return returnRequestRepository.findByStatusOrderByCreatedAtAsc(ReturnStatus.REQUESTED).stream()
        .map(
            returnRequest ->
                new FlaggedReturnResponse(
                    returnRequest.getRmaNumber(),
                    returnRequest.getOrder().getOrderNumber(),
                    returnRequest.getOrder().getCustomerName(),
                    returnRequest.getCustomerEmail(),
                    returnRequest.getCreatedAt(),
                    returnRequestRepository
                        .findRequestTimes(
                            returnRequest.getCustomerEmail(),
                            ReturnStatus.CANCELLED,
                            clock.instant().minus(FLAG_LOOKBACK))
                        .size(),
                    returnRequest.getItems().size(),
                    requestedPaise(returnRequest)))
        .toList();
  }

  @Override
  @Transactional
  public ReviewDecisionResponse approveReturn(String rmaNumber, StaffActor manager) {
    ReturnRequest approved = decide(rmaNumber, ReturnEvent.APPROVE, manager, null, null);
    return new ReviewDecisionResponse(approved.getRmaNumber(), approved.getStatus(), null);
  }

  @Override
  @Transactional
  public ReviewDecisionResponse declineReturn(String rmaNumber, String note, StaffActor manager) {
    ReturnRequest declined =
        decide(rmaNumber, ReturnEvent.REJECT, manager, RejectionReason.REQUEST_DECLINED, note);
    return new ReviewDecisionResponse(declined.getRmaNumber(), declined.getStatus(), null);
  }

  private ReturnRequest decide(
      String rmaNumber,
      ReturnEvent event,
      StaffActor actor,
      RejectionReason rejectionReason,
      String note) {
    ReturnRequest returnRequest = load(rmaNumber);
    returnTransitions.apply(returnRequest, event, actor.role(), actor.id());
    if (rejectionReason != null) {
      returnRequest.recordRejection(rejectionReason, note.strip());
    }
    return returnRequestRepository.saveAndFlush(returnRequest);
  }

  private ReturnRequest load(String rmaNumber) {
    String normalised = ReturnTransitions.normaliseRmaNumber(rmaNumber);
    return returnRequestRepository
        .findByRmaNumber(normalised)
        .orElseThrow(() -> ReturnNotFoundException.forStaff(normalised));
  }

  private static long requestedPaise(ReturnRequest returnRequest) {
    long total = 0;
    for (ReturnItem item : returnRequest.getItems()) {
      total += item.getOrderItem().getUnitPricePaise() * item.getQuantity();
    }
    return total;
  }
}
