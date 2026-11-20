package com.returnright.service.impl;

import com.returnright.exception.ResourceNotFoundException;
import com.returnright.mapper.ReturnMapper;
import com.returnright.model.domain.Actor;
import com.returnright.model.dto.response.ReconciliationResponse;
import com.returnright.model.dto.response.RefundResponse;
import com.returnright.model.dto.response.ReturnResponse;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.RefundStatus;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.repository.RefundRepository;
import com.returnright.repository.ReturnRequestRepository;
import com.returnright.service.FinanceService;
import com.returnright.service.RefundPayoutService;
import com.returnright.service.ReturnQueryService;
import com.returnright.service.ReturnTransitionService;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FinanceServiceImpl implements FinanceService {

  /** Already approved: a repeated approve call only re-drives the (idempotent) payout. */
  private static final Set<ReturnStatus> ALREADY_APPROVED =
      EnumSet.of(ReturnStatus.REFUND_PENDING, ReturnStatus.REFUNDED);

  private final ReturnRequestRepository returnRequestRepository;
  private final RefundRepository refundRepository;
  private final ReturnTransitionService transitionService;
  private final RefundPayoutService refundPayoutService;
  private final ReturnQueryService returnQueryService;
  private final ReturnMapper mapper;
  private final TransactionTemplate transactionTemplate;

  public FinanceServiceImpl(
      ReturnRequestRepository returnRequestRepository,
      RefundRepository refundRepository,
      ReturnTransitionService transitionService,
      RefundPayoutService refundPayoutService,
      ReturnQueryService returnQueryService,
      ReturnMapper mapper,
      TransactionTemplate transactionTemplate) {
    this.returnRequestRepository = returnRequestRepository;
    this.refundRepository = refundRepository;
    this.transitionService = transitionService;
    this.refundPayoutService = refundPayoutService;
    this.returnQueryService = returnQueryService;
    this.mapper = mapper;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public ReturnResponse approve(UUID returnId, String idempotencyKey, Actor actor) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Idempotency-Key header is required");
    }
    transactionTemplate.executeWithoutResult(
        status -> {
          ReturnRequest returnRequest = load(returnId);
          if (!ALREADY_APPROVED.contains(returnRequest.getStatus())) {
            transitionService.apply(
                returnRequest, ReturnEvent.FINANCE_APPROVE, actor, "Approved by finance");
          }
        });
    refundPayoutService.payout(returnId, idempotencyKey);
    return returnQueryService.get(returnId);
  }

  @Override
  public ReturnResponse reject(UUID returnId, String reason, Actor actor) {
    transactionTemplate.executeWithoutResult(
        status ->
            transitionService.apply(load(returnId), ReturnEvent.FINANCE_REJECT, actor, reason));
    return returnQueryService.get(returnId);
  }

  @Override
  public List<RefundResponse> listRefunds(RefundStatus status) {
    Set<RefundStatus> statuses =
        status == null ? EnumSet.allOf(RefundStatus.class) : EnumSet.of(status);
    return refundRepository.findByStatusIn(statuses).stream().map(mapper::toRefund).toList();
  }

  @Override
  public ReconciliationResponse reconcile() {
    return refundPayoutService.reconcile();
  }

  private ReturnRequest load(UUID returnId) {
    return returnRequestRepository
        .findById(returnId)
        .orElseThrow(() -> new ResourceNotFoundException("Return " + returnId + " not found"));
  }
}
