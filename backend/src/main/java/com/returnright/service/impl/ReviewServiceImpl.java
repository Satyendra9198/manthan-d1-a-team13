package com.returnright.service.impl;

import com.returnright.exception.ReturnNotFoundException;
import com.returnright.model.domain.StaffActor;
import com.returnright.model.dto.response.FinanceQueueItemResponse;
import com.returnright.model.dto.response.FlaggedReturnResponse;
import com.returnright.model.dto.response.ReviewDecisionResponse;
import com.returnright.model.entity.Refund;
import com.returnright.model.entity.ReturnItem;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.entity.StaffUser;
import com.returnright.model.enums.RefundStatus;
import com.returnright.model.enums.RejectionReason;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.repository.RefundRepository;
import com.returnright.repository.ReturnRequestRepository;
import com.returnright.repository.StaffUserRepository;
import com.returnright.service.RefundPayoutService;
import com.returnright.service.ReviewService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
  private final StaffUserRepository staffUserRepository;
  private final RefundRepository refundRepository;
  private final ReturnTransitions returnTransitions;
  private final RefundPayoutService refundPayoutService;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  public ReviewServiceImpl(
      ReturnRequestRepository returnRequestRepository,
      StaffUserRepository staffUserRepository,
      RefundRepository refundRepository,
      ReturnTransitions returnTransitions,
      RefundPayoutService refundPayoutService,
      TransactionTemplate transactionTemplate,
      Clock clock) {
    this.returnRequestRepository = returnRequestRepository;
    this.staffUserRepository = staffUserRepository;
    this.refundRepository = refundRepository;
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
                    returnRequest.isFlaggedForReview(),
                    requestsInLast30Days(returnRequest),
                    returnRequest.getItems().stream()
                        .map(item -> item.getOrderItem().getProductName())
                        .toList(),
                    returnRequest.getItems().stream()
                        .map(ReturnItem::getGrade)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null),
                    returnRequest.getItems().stream().anyMatch(ReviewServiceImpl::partial),
                    returnRequest.getRefundAmountPaise(),
                    requestedPaise(returnRequest),
                    returnRequest.getRefundMethod(),
                    staffName(returnRequest.getInspectedBy()),
                    returnRequest.getInspectedAt(),
                    RefundPayoutServiceImpl.refundKeyFor(returnRequest.getId()),
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
    String reference =
        refundRepository
            .findByReturnId(approved.getId())
            .map(Refund::getGatewayTransactionId)
            .orElse(null);
    return new ReviewDecisionResponse(
        approved.getRmaNumber(),
        statusNow,
        refundStatus,
        approved.getRefundAmountPaise(),
        reference,
        clock.instant());
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
    return decision(declined);
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
                    requestsInLast30Days(returnRequest),
                    returnRequest.getItems().size(),
                    requestedPaise(returnRequest)))
        .toList();
  }

  @Override
  @Transactional
  public ReviewDecisionResponse approveReturn(String rmaNumber, StaffActor manager) {
    return decision(decide(rmaNumber, ReturnEvent.APPROVE, manager, null, null));
  }

  @Override
  @Transactional
  public ReviewDecisionResponse declineReturn(String rmaNumber, String note, StaffActor manager) {
    return decision(
        decide(rmaNumber, ReturnEvent.REJECT, manager, RejectionReason.REQUEST_DECLINED, note));
  }

  private ReviewDecisionResponse decision(ReturnRequest returnRequest) {
    return new ReviewDecisionResponse(
        returnRequest.getRmaNumber(), returnRequest.getStatus(), null, null, null, clock.instant());
  }

  private int requestsInLast30Days(ReturnRequest returnRequest) {
    return returnRequestRepository
        .findRequestTimes(
            returnRequest.getCustomerEmail(),
            ReturnStatus.CANCELLED,
            clock.instant().minus(FLAG_LOOKBACK))
        .size();
  }

  private String staffName(UUID staffId) {
    return staffId == null
        ? null
        : staffUserRepository.findById(staffId).map(StaffUser::getFullName).orElse(null);
  }

  private static boolean partial(ReturnItem item) {
    return item.getAcceptedQuantity() != null
        && (item.getAcceptedQuantity() < item.getQuantity()
            || item.getRefundOverridePaise() != null);
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
