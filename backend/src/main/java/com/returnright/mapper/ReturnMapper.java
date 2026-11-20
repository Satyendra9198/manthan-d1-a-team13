package com.returnright.mapper;

import com.returnright.model.dto.response.IneligibleItemResponse;
import com.returnright.model.dto.response.LabelResponse;
import com.returnright.model.dto.response.RefundResponse;
import com.returnright.model.dto.response.ReturnItemResponse;
import com.returnright.model.dto.response.ReturnResponse;
import com.returnright.model.dto.response.ReturnSummaryResponse;
import com.returnright.model.dto.response.TimelineEventResponse;
import com.returnright.model.entity.AuditLogEntry;
import com.returnright.model.entity.Refund;
import com.returnright.model.entity.ReturnItem;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.CustomerTimelineStep;
import com.returnright.service.ReturnStateMachineService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Maps entities to response DTOs. Must be called inside a transaction (lazy associations). */
@Component
public class ReturnMapper {

  private static final String QR_PREFIX = "returnright://label/";

  private final ReturnStateMachineService stateMachine;

  public ReturnMapper(ReturnStateMachineService stateMachine) {
    this.stateMachine = stateMachine;
  }

  public ReturnResponse toResponse(
      ReturnRequest returnRequest,
      Refund refund,
      List<AuditLogEntry> history,
      List<IneligibleItemResponse> ineligibleItems) {
    return new ReturnResponse(
        returnRequest.getId(),
        returnRequest.getReturnNumber(),
        returnRequest.getOrder().getOrderNumber(),
        returnRequest.getCustomer().getFullName(),
        returnRequest.getStatus(),
        stateMachine.timelineStepFor(returnRequest.getStatus()),
        returnRequest.getRefundMethod(),
        returnRequest.isFlagged(),
        returnRequest.getRefundAmountPaise(),
        refund == null ? null : refund.getStatus(),
        toLabel(returnRequest),
        returnRequest.getItems().stream().map(ReturnMapper::toItem).toList(),
        ineligibleItems,
        toTimeline(history),
        returnRequest.getCreatedAt(),
        returnRequest.getUpdatedAt());
  }

  public ReturnSummaryResponse toSummary(ReturnRequest returnRequest) {
    return new ReturnSummaryResponse(
        returnRequest.getId(),
        returnRequest.getReturnNumber(),
        returnRequest.getOrder().getOrderNumber(),
        returnRequest.getCustomer().getFullName(),
        returnRequest.getStatus(),
        returnRequest.isFlagged(),
        returnRequest.getRefundAmountPaise(),
        returnRequest.getCreatedAt());
  }

  public RefundResponse toRefund(Refund refund) {
    return new RefundResponse(
        refund.getId(),
        refund.getReturnId(),
        refund.getAmountPaise(),
        refund.getMethod(),
        refund.getStatus(),
        refund.getGatewayTransactionId(),
        refund.getFailureReason());
  }

  public LabelResponse toLabel(ReturnRequest returnRequest) {
    if (returnRequest.getLabelCode() == null) {
      return null;
    }
    return new LabelResponse(
        returnRequest.getLabelCode(),
        QR_PREFIX + returnRequest.getLabelCode(),
        returnRequest.getLabelExpiresAt());
  }

  private static ReturnItemResponse toItem(ReturnItem item) {
    return new ReturnItemResponse(
        item.getId(),
        item.getOrderItem().getId(),
        item.getOrderItem().getProduct().getName(),
        item.getOrderItem().getProduct().getCategory(),
        item.getQuantity(),
        item.getOrderItem().getUnitPricePaise(),
        item.getReason(),
        item.getReasonNote(),
        item.getAcceptedQuantity(),
        item.getGrade(),
        item.getLineRefundPaise());
  }

  /** One entry per customer-facing step reached, in order; internal-only moves are collapsed. */
  private List<TimelineEventResponse> toTimeline(List<AuditLogEntry> history) {
    List<TimelineEventResponse> timeline = new ArrayList<>();
    CustomerTimelineStep previous = null;
    for (AuditLogEntry entry : history) {
      CustomerTimelineStep step = stateMachine.timelineStepFor(entry.getToStatus());
      if (step != previous) {
        timeline.add(new TimelineEventResponse(step, entry.getCreatedAt()));
        previous = step;
      }
    }
    return timeline;
  }
}
