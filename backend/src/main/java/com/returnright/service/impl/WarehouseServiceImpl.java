package com.returnright.service.impl;

import com.returnright.exception.BusinessRuleException;
import com.returnright.exception.ResourceNotFoundException;
import com.returnright.model.domain.Actor;
import com.returnright.model.domain.InspectedLine;
import com.returnright.model.dto.request.InspectionLineRequest;
import com.returnright.model.dto.request.InspectionRequest;
import com.returnright.model.dto.response.ReturnResponse;
import com.returnright.model.entity.ReturnItem;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.repository.ReturnRequestRepository;
import com.returnright.service.FinanceApprovalService;
import com.returnright.service.RefundCalculationService;
import com.returnright.service.RefundPayoutService;
import com.returnright.service.ReturnQueryService;
import com.returnright.service.ReturnTransitionService;
import com.returnright.service.WarehouseService;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WarehouseServiceImpl implements WarehouseService {

  private final ReturnRequestRepository returnRequestRepository;
  private final ReturnTransitionService transitionService;
  private final RefundCalculationService refundCalculationService;
  private final FinanceApprovalService financeApprovalService;
  private final RefundPayoutService refundPayoutService;
  private final ReturnQueryService returnQueryService;
  private final TransactionTemplate transactionTemplate;

  public WarehouseServiceImpl(
      ReturnRequestRepository returnRequestRepository,
      ReturnTransitionService transitionService,
      RefundCalculationService refundCalculationService,
      FinanceApprovalService financeApprovalService,
      RefundPayoutService refundPayoutService,
      ReturnQueryService returnQueryService,
      TransactionTemplate transactionTemplate) {
    this.returnRequestRepository = returnRequestRepository;
    this.transitionService = transitionService;
    this.refundCalculationService = refundCalculationService;
    this.financeApprovalService = financeApprovalService;
    this.refundPayoutService = refundPayoutService;
    this.returnQueryService = returnQueryService;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public ReturnResponse receive(String labelCode, Actor actor) {
    UUID returnId =
        transactionTemplate.execute(
            status -> {
              ReturnRequest returnRequest =
                  returnRequestRepository
                      .findByLabelCode(labelCode.trim())
                      .orElseThrow(
                          () -> new ResourceNotFoundException("No return matches this label."));
              transitionService.apply(returnRequest, ReturnEvent.RECEIVE, actor, "Label scanned");
              return returnRequest.getId();
            });
    return returnQueryService.get(returnId);
  }

  @Override
  public ReturnResponse inspect(UUID returnId, InspectionRequest request, Actor actor) {
    ReturnStatus outcome =
        transactionTemplate.execute(status -> recordInspection(returnId, request, actor));
    if (outcome == ReturnStatus.REFUND_PENDING) {
      refundPayoutService.payout(returnId, RefundPayoutService.systemIdempotencyKey(returnId));
    }
    return returnQueryService.get(returnId);
  }

  private ReturnStatus recordInspection(UUID returnId, InspectionRequest request, Actor actor) {
    ReturnRequest returnRequest =
        returnRequestRepository
            .findDetailedById(returnId)
            .orElseThrow(() -> new ResourceNotFoundException("Return " + returnId + " not found"));
    Map<UUID, InspectionLineRequest> lines = indexLines(request);
    Set<UUID> itemIds =
        returnRequest.getItems().stream().map(ReturnItem::getId).collect(Collectors.toSet());
    if (!lines.keySet().equals(itemIds)) {
      throw new BusinessRuleException(
          "INSPECTION_INCOMPLETE", "Inspect every item in the return exactly once.");
    }

    long total = 0;
    for (ReturnItem item : returnRequest.getItems()) {
      InspectionLineRequest line = lines.get(item.getId());
      long lineRefund =
          refundCalculationService.lineRefundPaise(
              new InspectedLine(
                  item.getOrderItem().getUnitPricePaise(),
                  item.getQuantity(),
                  line.acceptedQuantity(),
                  line.grade()));
      item.recordInspection(line.acceptedQuantity(), line.grade(), line.notes(), lineRefund);
      total += lineRefund;
    }
    returnRequest.setRefundAmountPaise(total);

    if (total == 0) {
      return transitionService.apply(
          returnRequest,
          ReturnEvent.REJECT_AFTER_INSPECTION,
          actor,
          "All items rejected at inspection");
    }
    transitionService.apply(
        returnRequest, ReturnEvent.COMPLETE_INSPECTION, actor, "Refund due: " + total + " paise");
    if (financeApprovalService.requiresApproval(total)) {
      return transitionService.apply(
          returnRequest,
          ReturnEvent.SEND_TO_FINANCE,
          Actor.system(),
          "Refund above 10,000 INR needs finance approval");
    }
    return transitionService.apply(
        returnRequest, ReturnEvent.START_REFUND, Actor.system(), "Below finance threshold");
  }

  private static Map<UUID, InspectionLineRequest> indexLines(InspectionRequest request) {
    Map<UUID, InspectionLineRequest> lines = new HashMap<>();
    for (InspectionLineRequest line : request.lines()) {
      if (lines.put(line.returnItemId(), line) != null) {
        throw new IllegalArgumentException(
            "Return item " + line.returnItemId() + " appears more than once");
      }
    }
    return lines;
  }
}
