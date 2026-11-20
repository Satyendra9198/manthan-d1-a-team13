package com.returnright.service.impl;

import com.returnright.exception.ReturnNotFoundException;
import com.returnright.model.domain.InspectedLine;
import com.returnright.model.dto.request.TrackReturnRequest;
import com.returnright.model.dto.response.RefundSummaryResponse;
import com.returnright.model.dto.response.RejectionResponse;
import com.returnright.model.dto.response.ReturnLabelResponse;
import com.returnright.model.dto.response.ReturnTrackingResponse;
import com.returnright.model.dto.response.TimelineEntryResponse;
import com.returnright.model.dto.response.TrackedItemResponse;
import com.returnright.model.entity.Refund;
import com.returnright.model.entity.ReturnItem;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.entity.ReturnStatusChange;
import com.returnright.model.enums.CustomerRefundStatus;
import com.returnright.model.enums.CustomerTimelineStep;
import com.returnright.model.enums.InspectionGrade;
import com.returnright.model.enums.RefundMethod;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.TimelineEntryState;
import com.returnright.repository.RefundRepository;
import com.returnright.repository.ReturnRequestRepository;
import com.returnright.service.RefundCalculationService;
import com.returnright.service.ReturnStateMachineService;
import com.returnright.service.ReturnTrackingService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the customer's return page from the return, its status history and inspection result. */
@Service
public class ReturnTrackingServiceImpl implements ReturnTrackingService {

  /** Labels expire 14 days after approval (A-13). */
  static final int LABEL_VALID_DAYS = 14;

  static final String WAREHOUSE_ADDRESS =
      "ReturnRight Returns Centre, Unit 4, Bhiwandi Logistics Park, Thane, Maharashtra 421302";

  private static final List<CustomerTimelineStep> HAPPY_PATH =
      List.of(
          CustomerTimelineStep.REQUESTED,
          CustomerTimelineStep.APPROVED,
          CustomerTimelineStep.IN_TRANSIT,
          CustomerTimelineStep.RECEIVED,
          CustomerTimelineStep.INSPECTED,
          CustomerTimelineStep.REFUNDED);

  private static final Set<CustomerTimelineStep> FINAL_STEPS =
      EnumSet.of(
          CustomerTimelineStep.REFUNDED,
          CustomerTimelineStep.REJECTED,
          CustomerTimelineStep.CANCELLED);

  private static final Set<ReturnStatus> LABEL_STATUSES =
      EnumSet.of(ReturnStatus.APPROVED, ReturnStatus.IN_TRANSIT);

  private static final DateTimeFormatter CUSTOMER_DATE =
      DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

  private final ReturnRequestRepository returnRequestRepository;
  private final ReturnStateMachineService returnStateMachineService;
  private final RefundCalculationService refundCalculationService;
  private final RefundRepository refundRepository;

  public ReturnTrackingServiceImpl(
      ReturnRequestRepository returnRequestRepository,
      ReturnStateMachineService returnStateMachineService,
      RefundCalculationService refundCalculationService,
      RefundRepository refundRepository) {
    this.refundRepository = refundRepository;
    this.returnRequestRepository = returnRequestRepository;
    this.returnStateMachineService = returnStateMachineService;
    this.refundCalculationService = refundCalculationService;
  }

  @Override
  @Transactional(readOnly = true)
  public ReturnTrackingResponse track(TrackReturnRequest request) {
    ReturnRequest returnRequest =
        returnRequestRepository
            .findByRmaNumberAndCustomerEmail(
                ReturnTransitions.normaliseRmaNumber(request.rmaNumber()),
                request.email().trim().toLowerCase(Locale.ROOT))
            .orElseThrow(ReturnNotFoundException::forCustomer);

    CustomerTimelineStep status =
        returnStateMachineService.timelineStepFor(returnRequest.getStatus());
    List<TrackedItemResponse> items = returnRequest.getItems().stream().map(this::toItem).toList();
    long estimatedRefundPaise = items.stream().mapToLong(TrackedItemResponse::lineTotalPaise).sum();
    ReturnLabelResponse label = labelFor(returnRequest);
    RefundSummaryResponse refund = refundFor(returnRequest, estimatedRefundPaise);

    return new ReturnTrackingResponse(
        returnRequest.getRmaNumber(),
        returnRequest.getOrder().getOrderNumber(),
        status,
        statusMessage(returnRequest, label, refund),
        returnRequest.getRefundMethod(),
        returnRequest.getCreatedAt(),
        estimatedRefundPaise,
        timeline(returnRequest, status),
        items,
        label,
        refund,
        rejectionFor(returnRequest));
  }

  /**
   * Reached steps in happy-path order with the time first reached; a skipped step (e.g. no
   * in-transit scan, A-17) is left out. A rejection or cancellation ends the timeline; otherwise
   * the remaining happy-path steps follow as upcoming.
   */
  private List<TimelineEntryResponse> timeline(
      ReturnRequest returnRequest, CustomerTimelineStep current) {
    Map<CustomerTimelineStep, Instant> reachedAt = new EnumMap<>(CustomerTimelineStep.class);
    for (ReturnStatusChange change : returnRequest.getStatusHistory()) {
      reachedAt.putIfAbsent(
          returnStateMachineService.timelineStepFor(change.getStatus()), change.getChangedAt());
    }
    boolean finished = FINAL_STEPS.contains(current);

    List<TimelineEntryResponse> entries = new ArrayList<>();
    for (CustomerTimelineStep step : HAPPY_PATH) {
      if (reachedAt.containsKey(step)) {
        TimelineEntryState state =
            step == current && !finished
                ? TimelineEntryState.CURRENT
                : TimelineEntryState.COMPLETED;
        entries.add(new TimelineEntryResponse(step, state, reachedAt.get(step)));
      }
    }
    if (current == CustomerTimelineStep.REJECTED || current == CustomerTimelineStep.CANCELLED) {
      entries.add(
          new TimelineEntryResponse(current, TimelineEntryState.COMPLETED, reachedAt.get(current)));
    } else if (!finished) {
      for (CustomerTimelineStep step :
          HAPPY_PATH.subList(HAPPY_PATH.indexOf(current) + 1, HAPPY_PATH.size())) {
        entries.add(new TimelineEntryResponse(step, TimelineEntryState.UPCOMING, null));
      }
    }
    return entries;
  }

  private TrackedItemResponse toItem(ReturnItem item) {
    long unitPricePaise = item.getOrderItem().getUnitPricePaise();
    return new TrackedItemResponse(
        item.getOrderItem().getId(),
        item.getOrderItem().getProductName(),
        item.getOrderItem().getVariant(),
        item.getQuantity(),
        item.getReason(),
        unitPricePaise * item.getQuantity(),
        item.getGrade(),
        item.getAcceptedQuantity(),
        item.getAcceptedQuantity() == null ? null : lineRefund(item),
        item.getRejectionReason(),
        reduced(item) ? item.getInspectionNote() : null);
  }

  /** The line refunds less than its full price: grade C, fewer units, or a chosen amount. */
  private static boolean reduced(ReturnItem item) {
    return item.getAcceptedQuantity() != null
        && (item.getAcceptedQuantity() < item.getQuantity()
            || item.getRefundOverridePaise() != null
            || (item.getGrade() != null && item.getGrade().getRefundPercent() < 100));
  }

  private long lineRefund(ReturnItem item) {
    if (item.getRefundOverridePaise() != null) {
      return item.getRefundOverridePaise();
    }
    InspectionGrade grade = item.getGrade() == null ? InspectionGrade.A : item.getGrade();
    return refundCalculationService.lineRefundPaise(
        new InspectedLine(
            item.getOrderItem().getUnitPricePaise(),
            item.getQuantity(),
            item.getAcceptedQuantity(),
            grade));
  }

  private static ReturnLabelResponse labelFor(ReturnRequest returnRequest) {
    if (!LABEL_STATUSES.contains(returnRequest.getStatus())
        || returnRequest.getApprovedAt() == null) {
      return null;
    }
    LocalDate dropOffBy =
        LocalDate.ofInstant(returnRequest.getApprovedAt(), EligibilityServiceImpl.STORE_ZONE)
            .plusDays(LABEL_VALID_DAYS);
    return new ReturnLabelResponse(
        returnRequest.getRmaNumber(),
        dropOffBy,
        WAREHOUSE_ADDRESS,
        "Show this QR code at any partner drop-off point, or print the label and stick it on the"
            + " parcel.");
  }

  private RefundSummaryResponse refundFor(ReturnRequest returnRequest, long requestedPaise) {
    Long amountPaise = returnRequest.getRefundAmountPaise();
    if (amountPaise == null || returnRequest.getStatus() == ReturnStatus.REJECTED) {
      return null;
    }
    boolean refunded = returnRequest.getStatus() == ReturnStatus.REFUNDED;
    Instant refundedAt =
        refunded
            ? returnRequest.getStatusHistory().stream()
                .filter(change -> change.getStatus() == ReturnStatus.REFUNDED)
                .map(ReturnStatusChange::getChangedAt)
                .findFirst()
                .orElse(null)
            : null;
    return new RefundSummaryResponse(
        amountPaise,
        requestedPaise,
        returnRequest.getRefundMethod(),
        refunded ? CustomerRefundStatus.REFUNDED : CustomerRefundStatus.PROCESSING,
        refundedAt,
        returnRequest.getStatusHistory().stream()
            .anyMatch(change -> change.getStatus() == ReturnStatus.AWAITING_FINANCE_APPROVAL),
        refunded
            ? refundRepository
                .findByReturnId(returnRequest.getId())
                .map(Refund::getGatewayTransactionId)
                .orElse(null)
            : null,
        explanation(returnRequest));
  }

  private static List<String> explanation(ReturnRequest returnRequest) {
    List<String> lines = new ArrayList<>();
    for (ReturnItem item : returnRequest.getItems()) {
      Integer accepted = item.getAcceptedQuantity();
      if (accepted == null) {
        continue;
      }
      String product = item.getOrderItem().getProductName();
      if (accepted < item.getQuantity()) {
        String reason =
            item.getRejectionReason() == null
                ? ""
                : " " + item.getRejectionReason().getCustomerMessage();
        lines.add(product + ": " + accepted + " of " + item.getQuantity() + " accepted." + reason);
      }
      if (accepted > 0 && item.getGrade() != null && item.getGrade().getRefundPercent() < 100) {
        lines.add(
            product
                + ": graded "
                + item.getGrade()
                + " at inspection ("
                + item.getGrade().getDescription().toLowerCase(Locale.ENGLISH)
                + "), so it is refunded at "
                + item.getGrade().getRefundPercent()
                + "%.");
      }
    }
    return lines;
  }

  private static RejectionResponse rejectionFor(ReturnRequest returnRequest) {
    if (returnRequest.getStatus() != ReturnStatus.REJECTED
        || returnRequest.getRejectionReason() == null) {
      return null;
    }
    return new RejectionResponse(
        returnRequest.getRejectionReason(),
        returnRequest.getRejectionReason().getCustomerMessage(),
        returnRequest.getRejectionReason().isNoteRequired()
            ? returnRequest.getRejectionNote()
            : null);
  }

  private static String statusMessage(
      ReturnRequest returnRequest, ReturnLabelResponse label, RefundSummaryResponse refund) {
    return switch (returnRequest.getStatus()) {
      case REQUESTED -> "We're reviewing your return request. We'll email you once it's approved.";
      case APPROVED ->
          label == null
              ? "Approved · label ready."
              : "Approved · label ready. Drop off your parcel by "
                  + CUSTOMER_DATE.format(label.dropOffBy())
                  + ".";
      case IN_TRANSIT -> "Your parcel is on its way to our warehouse.";
      case RECEIVED -> "We've received your parcel. Inspection usually takes 1–2 business days.";
      case INSPECTED, AWAITING_FINANCE_APPROVAL, REFUND_PENDING ->
          "Inspection complete. Your refund of "
              + Rupees.format(refund.amountPaise())
              + " is being processed.";
      case REFUNDED ->
          Rupees.format(refund.amountPaise())
              + " refunded to your "
              + (returnRequest.getRefundMethod() == RefundMethod.STORE_CREDIT
                  ? "store credit."
                  : "original payment method.");
      case REJECTED -> "We couldn't accept this return. No refund has been issued.";
      case CANCELLED -> "This return was cancelled.";
    };
  }
}
