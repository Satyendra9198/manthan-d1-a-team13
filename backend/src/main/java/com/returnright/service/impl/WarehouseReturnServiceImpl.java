package com.returnright.service.impl;

import com.returnright.exception.InvalidInspectionException;
import com.returnright.exception.PhotoNotFoundException;
import com.returnright.exception.ReturnNotFoundException;
import com.returnright.model.domain.InspectedLine;
import com.returnright.model.domain.PhotoContent;
import com.returnright.model.domain.StaffActor;
import com.returnright.model.dto.request.InspectedItemRequest;
import com.returnright.model.dto.request.InspectionRequest;
import com.returnright.model.dto.request.RejectReturnRequest;
import com.returnright.model.dto.response.InspectionResultResponse;
import com.returnright.model.dto.response.InspectionSummaryResponse;
import com.returnright.model.dto.response.PhotoResponse;
import com.returnright.model.dto.response.WarehouseItemResponse;
import com.returnright.model.dto.response.WarehouseQueueItemResponse;
import com.returnright.model.dto.response.WarehouseReturnResponse;
import com.returnright.model.entity.ReturnItem;
import com.returnright.model.entity.ReturnPhoto;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.entity.ReturnStatusChange;
import com.returnright.model.enums.InspectionGrade;
import com.returnright.model.enums.RefundStatus;
import com.returnright.model.enums.RejectionReason;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import com.returnright.repository.ReturnRequestRepository;
import com.returnright.service.FinanceApprovalService;
import com.returnright.service.PhotoStorage;
import com.returnright.service.RefundCalculationService;
import com.returnright.service.RefundPayoutService;
import com.returnright.service.ReturnStateMachineService;
import com.returnright.service.WarehouseReturnService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Inspection and rejection save their state change in one short transaction; the refund payout runs
 * afterwards, outside it, so a slow or failed gateway call never rolls back the inspection.
 */
@Service
public class WarehouseReturnServiceImpl implements WarehouseReturnService {

  private final ReturnRequestRepository returnRequestRepository;
  private final ReturnTransitions returnTransitions;
  private final ReturnStateMachineService returnStateMachineService;
  private final RefundCalculationService refundCalculationService;
  private final FinanceApprovalService financeApprovalService;
  private final RefundPayoutService refundPayoutService;
  private final PhotoStorage photoStorage;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  public WarehouseReturnServiceImpl(
      ReturnRequestRepository returnRequestRepository,
      ReturnTransitions returnTransitions,
      ReturnStateMachineService returnStateMachineService,
      RefundCalculationService refundCalculationService,
      FinanceApprovalService financeApprovalService,
      RefundPayoutService refundPayoutService,
      PhotoStorage photoStorage,
      TransactionTemplate transactionTemplate,
      Clock clock) {
    this.returnRequestRepository = returnRequestRepository;
    this.returnTransitions = returnTransitions;
    this.returnStateMachineService = returnStateMachineService;
    this.refundCalculationService = refundCalculationService;
    this.financeApprovalService = financeApprovalService;
    this.refundPayoutService = refundPayoutService;
    this.photoStorage = photoStorage;
    this.transactionTemplate = transactionTemplate;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public List<WarehouseQueueItemResponse> awaitingInspection() {
    return returnRequestRepository.findByStatusOrderByCreatedAtAsc(ReturnStatus.RECEIVED).stream()
        .map(
            returnRequest ->
                new WarehouseQueueItemResponse(
                    returnRequest.getRmaNumber(),
                    returnRequest.getOrder().getOrderNumber(),
                    returnRequest.getOrder().getCustomerName(),
                    returnRequest.getCreatedAt(),
                    returnRequest.getItems().size()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public WarehouseReturnResponse findReturn(String rmaNumber, StaffActor agent) {
    return toResponse(load(rmaNumber), agent);
  }

  @Override
  @Transactional
  public WarehouseReturnResponse receive(String rmaNumber, StaffActor agent) {
    ReturnRequest returnRequest = load(rmaNumber);
    returnTransitions.apply(returnRequest, ReturnEvent.RECEIVE, agent.role(), agent.id());
    returnRequestRepository.saveAndFlush(returnRequest);
    return toResponse(returnRequest, agent);
  }

  @Override
  public InspectionResultResponse completeInspection(
      String rmaNumber, InspectionRequest request, StaffActor agent) {
    InspectionResultResponse saved =
        transactionTemplate.execute(status -> saveInspection(rmaNumber, request, agent));
    return payOutIfDue(saved);
  }

  @Override
  public InspectionResultResponse reject(
      String rmaNumber, RejectReturnRequest request, StaffActor agent) {
    List<String> problems = new ArrayList<>();
    checkReason(request.reason(), request.note(), "The return", problems);
    if (!problems.isEmpty()) {
      throw new InvalidInspectionException(problems);
    }
    return transactionTemplate.execute(
        status -> {
          ReturnRequest returnRequest = load(rmaNumber);
          requireEvent(returnRequest, ReturnEvent.REJECT_AFTER_INSPECTION, agent);
          String note = blankToNull(request.note());
          for (ReturnItem item : returnRequest.getItems()) {
            item.recordInspection(null, 0, request.reason(), null);
          }
          returnRequest.recordRejection(request.reason(), note);
          returnRequest.recordInspection(agent.id(), clock.instant(), 0);
          returnTransitions.apply(
              returnRequest, ReturnEvent.REJECT_AFTER_INSPECTION, agent.role(), agent.id());
          returnRequestRepository.saveAndFlush(returnRequest);
          return new InspectionResultResponse(
              returnRequest.getRmaNumber(), returnRequest.getStatus(), 0, false, null);
        });
  }

  @Override
  @Transactional(readOnly = true)
  public PhotoContent photo(String rmaNumber, UUID photoId) {
    ReturnPhoto photo =
        load(rmaNumber).getItems().stream()
            .flatMap(item -> item.getPhotos().stream())
            .filter(candidate -> candidate.getId().equals(photoId))
            .findFirst()
            .orElseThrow(() -> new PhotoNotFoundException(photoId));
    byte[] content =
        photoStorage
            .load(photo.getStorageKey())
            .orElseThrow(() -> new PhotoNotFoundException(photoId));
    return new PhotoContent(photo.getContentType(), content);
  }

  @Override
  @Transactional(readOnly = true)
  public InspectionSummaryResponse todaySummary(StaffActor agent) {
    LocalDate today = LocalDate.now(clock.withZone(EligibilityServiceImpl.STORE_ZONE));
    Instant from = today.atStartOfDay(EligibilityServiceImpl.STORE_ZONE).toInstant();
    Instant to =
        ZonedDateTime.of(today.plusDays(1).atStartOfDay(), EligibilityServiceImpl.STORE_ZONE)
            .toInstant();
    return new InspectionSummaryResponse(
        today,
        returnRequestRepository.countByInspectedAtGreaterThanEqualAndInspectedAtLessThan(from, to),
        returnRequestRepository
            .countByInspectedByAndInspectedAtGreaterThanEqualAndInspectedAtLessThan(
                agent.id(), from, to));
  }

  private InspectionResultResponse saveInspection(
      String rmaNumber, InspectionRequest request, StaffActor agent) {
    ReturnRequest returnRequest = load(rmaNumber);
    requireEvent(returnRequest, ReturnEvent.COMPLETE_INSPECTION, agent);
    Map<UUID, InspectedItemRequest> verdicts = validate(returnRequest, request);

    long refundPaise = 0;
    int acceptedUnits = 0;
    for (ReturnItem item : returnRequest.getItems()) {
      InspectedItemRequest verdict = verdicts.get(item.getId());
      boolean partial =
          verdict.acceptedQuantity() < item.getQuantity() || verdict.refundAmountPaise() != null;
      item.recordInspection(
          verdict.acceptedQuantity() == 0 ? null : verdict.grade(),
          verdict.acceptedQuantity(),
          partial ? verdict.rejectionReason() : null,
          blankToNull(verdict.note()),
          verdict.refundAmountPaise());
      refundPaise +=
          verdict.refundAmountPaise() != null
              ? verdict.refundAmountPaise()
              : gradedRefund(item, verdict);
      acceptedUnits += verdict.acceptedQuantity();
    }

    Instant now = clock.instant();
    returnRequest.recordInspection(agent.id(), now, refundPaise);

    boolean financeApprovalRequired = false;
    if (acceptedUnits == 0) {
      InspectedItemRequest first = verdicts.get(returnRequest.getItems().get(0).getId());
      returnRequest.recordRejection(first.rejectionReason(), blankToNull(first.note()));
      returnTransitions.apply(
          returnRequest, ReturnEvent.REJECT_AFTER_INSPECTION, agent.role(), agent.id());
    } else {
      returnTransitions.apply(
          returnRequest, ReturnEvent.COMPLETE_INSPECTION, agent.role(), agent.id());
      financeApprovalRequired = financeApprovalService.requiresApproval(refundPaise);
      returnTransitions.apply(
          returnRequest,
          financeApprovalRequired ? ReturnEvent.SEND_TO_FINANCE : ReturnEvent.START_REFUND,
          Role.SYSTEM,
          null);
    }
    returnRequestRepository.saveAndFlush(returnRequest);
    return new InspectionResultResponse(
        returnRequest.getRmaNumber(),
        returnRequest.getStatus(),
        refundPaise,
        financeApprovalRequired,
        null);
  }

  /** Every line exactly once, with a consistent grade, quantity and reason. */
  private Map<UUID, InspectedItemRequest> validate(
      ReturnRequest returnRequest, InspectionRequest request) {
    Map<UUID, ReturnItem> itemsById =
        returnRequest.getItems().stream()
            .collect(Collectors.toMap(ReturnItem::getId, Function.identity()));
    List<String> problems = new ArrayList<>();
    Set<UUID> seen = new HashSet<>();

    for (InspectedItemRequest verdict : request.items()) {
      ReturnItem item = itemsById.get(verdict.returnItemId());
      String label = verdict.returnItemId().toString();
      if (item == null) {
        problems.add(label + ": This line is not part of the return.");
        continue;
      }
      if (!seen.add(verdict.returnItemId())) {
        problems.add(label + ": This line was inspected more than once.");
        continue;
      }
      if (verdict.acceptedQuantity() > item.getQuantity()) {
        problems.add(
            label + ": At most " + item.getQuantity() + " units can be accepted for this line.");
        continue;
      }
      if (verdict.acceptedQuantity() > 0 && verdict.grade() == null) {
        problems.add(label + ": Choose a condition grade.");
      }
      if (verdict.refundAmountPaise() != null) {
        checkRefundAmount(item, verdict, label, problems);
      }
      if (verdict.acceptedQuantity() < item.getQuantity() || verdict.refundAmountPaise() != null) {
        if (verdict.rejectionReason() == null) {
          problems.add(label + ": Choose why the item wasn't fully accepted.");
        } else {
          checkReason(verdict.rejectionReason(), verdict.note(), label, problems);
        }
      }
    }
    for (UUID itemId : itemsById.keySet()) {
      if (!seen.contains(itemId)) {
        problems.add(itemId + ": This line has not been inspected.");
      }
    }
    if (!problems.isEmpty()) {
      throw new InvalidInspectionException(problems);
    }
    return request.items().stream()
        .collect(Collectors.toMap(InspectedItemRequest::returnItemId, Function.identity()));
  }

  /** A partial accept by amount: at least one unit accepted, at most the graded refund (A-20). */
  private void checkRefundAmount(
      ReturnItem item, InspectedItemRequest verdict, String label, List<String> problems) {
    if (verdict.acceptedQuantity() == 0) {
      problems.add(label + ": A refund amount needs at least one accepted unit.");
    } else if (verdict.grade() != null) {
      long maximum = gradedRefund(item, verdict);
      if (verdict.refundAmountPaise() > maximum) {
        problems.add(
            label
                + ": The refund can be at most "
                + Rupees.format(maximum)
                + " for grade "
                + verdict.grade()
                + ".");
      }
    }
  }

  private long gradedRefund(ReturnItem item, InspectedItemRequest verdict) {
    return refundCalculationService.lineRefundPaise(
        new InspectedLine(
            item.getOrderItem().getUnitPricePaise(),
            item.getQuantity(),
            verdict.acceptedQuantity(),
            verdict.grade() == null ? InspectionGrade.A : verdict.grade()));
  }

  private static void checkReason(
      RejectionReason reason, String note, String label, List<String> problems) {
    if (!reason.isSelectableByWarehouse()) {
      problems.add(label + ": " + reason + " can't be chosen by the warehouse.");
    } else if (reason.isNoteRequired() && (note == null || note.isBlank())) {
      problems.add(label + ": Add a note when the reason is Other.");
    }
  }

  private InspectionResultResponse payOutIfDue(InspectionResultResponse saved) {
    if (saved.status() != ReturnStatus.REFUND_PENDING) {
      return saved;
    }
    UUID returnId = transactionTemplate.execute(status -> load(saved.rmaNumber()).getId());
    RefundStatus refundStatus = refundPayoutService.payOut(returnId).orElse(null);
    ReturnStatus statusNow =
        transactionTemplate.execute(status -> load(saved.rmaNumber()).getStatus());
    return new InspectionResultResponse(
        saved.rmaNumber(),
        statusNow,
        saved.refundAmountPaise(),
        saved.financeApprovalRequired(),
        refundStatus);
  }

  /** Fails with 409 before any validation if the event isn't possible in the current state. */
  private void requireEvent(ReturnRequest returnRequest, ReturnEvent event, StaffActor agent) {
    returnStateMachineService.transition(returnRequest.getStatus(), event, agent.role());
  }

  private ReturnRequest load(String rmaNumber) {
    String normalised = ReturnTransitions.normaliseRmaNumber(rmaNumber);
    return returnRequestRepository
        .findByRmaNumber(normalised)
        .orElseThrow(() -> ReturnNotFoundException.forStaff(normalised));
  }

  private WarehouseReturnResponse toResponse(ReturnRequest returnRequest, StaffActor agent) {
    Instant receivedAt =
        returnRequest.getStatusHistory().stream()
            .filter(change -> change.getStatus() == ReturnStatus.RECEIVED)
            .map(ReturnStatusChange::getChangedAt)
            .findFirst()
            .orElse(null);
    return new WarehouseReturnResponse(
        returnRequest.getId(),
        returnRequest.getRmaNumber(),
        returnRequest.getStatus(),
        returnRequest.getOrder().getOrderNumber(),
        returnRequest.getOrder().getCustomerName(),
        returnRequest.isFlaggedForReview(),
        returnRequestRepository
            .findRequestTimes(
                returnRequest.getCustomerEmail(),
                ReturnStatus.CANCELLED,
                clock.instant().minus(Duration.ofDays(30)))
            .size(),
        returnRequest.getRefundMethod(),
        returnRequest.getCreatedAt(),
        receivedAt,
        returnRequest.getRefundAmountPaise(),
        returnRequest.getRejectionReason(),
        returnStateMachineService.allowedEvents(returnRequest.getStatus(), agent.role()),
        returnRequest.getItems().stream()
            .map(item -> toItem(returnRequest.getRmaNumber(), item))
            .toList());
  }

  private static WarehouseItemResponse toItem(String rmaNumber, ReturnItem item) {
    List<PhotoResponse> photos =
        item.getPhotos().stream()
            .map(
                photo ->
                    new PhotoResponse(
                        photo.getId(),
                        photo.getContentType(),
                        "/api/v1/warehouse/returns/" + rmaNumber + "/photos/" + photo.getId()))
            .toList();
    return new WarehouseItemResponse(
        item.getId(),
        item.getOrderItem().getId(),
        item.getOrderItem().getSku(),
        item.getOrderItem().getProductName(),
        item.getOrderItem().getVariant(),
        item.getOrderItem().getCategory(),
        item.getOrderItem().getUnitPricePaise(),
        item.getQuantity(),
        item.getReason(),
        item.getCustomerComment(),
        photos,
        item.getGrade(),
        item.getAcceptedQuantity(),
        item.getRejectionReason(),
        item.getInspectionNote());
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.strip();
  }
}
