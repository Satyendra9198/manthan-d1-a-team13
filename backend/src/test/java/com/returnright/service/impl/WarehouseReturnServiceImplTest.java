package com.returnright.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.returnright.exception.IllegalTransitionException;
import com.returnright.exception.InvalidInspectionException;
import com.returnright.exception.PhotoNotFoundException;
import com.returnright.model.domain.PhotoContent;
import com.returnright.model.domain.StaffActor;
import com.returnright.model.dto.request.InspectedItemRequest;
import com.returnright.model.dto.request.InspectionRequest;
import com.returnright.model.dto.request.RejectReturnRequest;
import com.returnright.model.dto.response.InspectionResultResponse;
import com.returnright.model.dto.response.InspectionSummaryResponse;
import com.returnright.model.dto.response.WarehouseReturnResponse;
import com.returnright.model.entity.CustomerOrder;
import com.returnright.model.entity.OrderItem;
import com.returnright.model.entity.ReturnItem;
import com.returnright.model.entity.ReturnPhoto;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.InspectionGrade;
import com.returnright.model.enums.ProductCategory;
import com.returnright.model.enums.RefundMethod;
import com.returnright.model.enums.RefundStatus;
import com.returnright.model.enums.RejectionReason;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnReason;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import com.returnright.repository.ReturnRequestRepository;
import com.returnright.service.RefundPayoutService;
import com.returnright.support.InMemoryPhotoStorage;
import com.returnright.support.NoOpTransactionManager;
import com.returnright.support.TestImages;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WarehouseReturnServiceImplTest {

  /** 20 Nov 2026, 12:00 IST. */
  private static final Instant NOW = Instant.parse("2026-11-20T06:30:00Z");

  private static final String RMA = "RMA-2026-00731";
  private static final StaffActor AGENT = new StaffActor(UUID.randomUUID(), Role.WAREHOUSE_AGENT);

  private final ReturnRequestRepository returnRequestRepository =
      mock(ReturnRequestRepository.class);
  private final RefundPayoutService refundPayoutService = mock(RefundPayoutService.class);
  private final InMemoryPhotoStorage photoStorage = new InMemoryPhotoStorage();

  private WarehouseReturnServiceImpl service;
  private ReturnRequest returnRequest;

  /** Two linen shirts at ₹1,899 each. */
  private ReturnItem shirts;

  /** One mug set at ₹1,249. */
  private ReturnItem mugs;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    ReturnStateMachineServiceImpl stateMachine = new ReturnStateMachineServiceImpl();
    service =
        new WarehouseReturnServiceImpl(
            returnRequestRepository,
            new ReturnTransitions(stateMachine, clock),
            stateMachine,
            new RefundCalculationServiceImpl(),
            new FinanceApprovalServiceImpl(),
            refundPayoutService,
            photoStorage,
            NoOpTransactionManager.template(),
            clock);

    returnRequest =
        receivedReturn(line("Linen shirt", 189_900, 2), line("Ceramic mug set", 124_900, 1));
    shirts = returnRequest.getItems().get(0);
    mugs = returnRequest.getItems().get(1);

    when(returnRequestRepository.saveAndFlush(any(ReturnRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(refundPayoutService.payOut(any()))
        .thenAnswer(
            invocation -> {
              returnRequest.moveTo(ReturnStatus.REFUNDED, NOW, Role.SYSTEM, null);
              return Optional.of(RefundStatus.SUCCEEDED);
            });
  }

  @Test
  void receivingAnApprovedParcelMovesItToReceived() {
    ReturnRequest approved = approvedReturn(line("Linen shirt", 189_900, 1));

    WarehouseReturnResponse response = service.receive(RMA, AGENT);

    assertThat(response.status()).isEqualTo(ReturnStatus.RECEIVED);
    assertThat(response.receivedAt()).isEqualTo(NOW);
    assertThat(approved.getStatusHistory().get(approved.getStatusHistory().size() - 1).getActorId())
        .isEqualTo(AGENT.id());
  }

  @Test
  void scanningAReceivedParcelAgainIsRejectedAsIllegal() {
    assertThatThrownBy(() -> service.receive(RMA, AGENT))
        .isInstanceOf(IllegalTransitionException.class);
  }

  @Test
  void receivedReturnOffersTheInspectionOutcomesAndPhotoUrls() {
    UUID photoId = attachPhoto(shirts);

    WarehouseReturnResponse response = service.findReturn(" rma-2026-00731 ", AGENT);

    assertThat(response.allowedActions())
        .containsExactlyInAnyOrder(
            ReturnEvent.COMPLETE_INSPECTION, ReturnEvent.REJECT_AFTER_INSPECTION);
    assertThat(response.items().get(0).photos())
        .singleElement()
        .satisfies(
            photo ->
                assertThat(photo.url())
                    .isEqualTo("/api/v1/warehouse/returns/" + RMA + "/photos/" + photoId));
  }

  @Test
  void allLinesGradedAAreRefundedInFullAndPaidAtOnce() {
    InspectionResultResponse result =
        inspect(accept(shirts, 2, InspectionGrade.A), accept(mugs, 1, InspectionGrade.A));

    assertThat(result.refundAmountPaise()).isEqualTo(2 * 189_900 + 124_900);
    assertThat(result.financeApprovalRequired()).isFalse();
    assertThat(result.status()).isEqualTo(ReturnStatus.REFUNDED);
    assertThat(result.refundStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    verify(refundPayoutService).payOut(returnRequest.getId());
    assertThat(returnRequest.getInspectedBy()).isEqualTo(AGENT.id());
    assertThat(returnRequest.getInspectedAt()).isEqualTo(NOW);
  }

  @Test
  void gradeCLineIsRefundedAtHalfPrice() {
    InspectionResultResponse result =
        inspect(accept(shirts, 2, InspectionGrade.C), accept(mugs, 1, InspectionGrade.B));

    assertThat(result.refundAmountPaise()).isEqualTo(189_900 + 124_900);
    assertThat(shirts.getGrade()).isEqualTo(InspectionGrade.C);
  }

  @Test
  void partialAcceptRefundsOnlyTheAcceptedUnitsAndKeepsTheReason() {
    InspectionResultResponse result =
        inspect(
            new InspectedItemRequest(
                shirts.getId(),
                InspectionGrade.A,
                1,
                RejectionReason.USED_OR_WORN,
                "Stained",
                null),
            accept(mugs, 1, InspectionGrade.A));

    assertThat(result.refundAmountPaise()).isEqualTo(189_900 + 124_900);
    assertThat(shirts.getAcceptedQuantity()).isEqualTo(1);
    assertThat(shirts.getRejectionReason()).isEqualTo(RejectionReason.USED_OR_WORN);
    assertThat(shirts.getInspectionNote()).isEqualTo("Stained");
    assertThat(mugs.getRejectionReason()).isNull();
  }

  @Test
  void partialAcceptByAmountRefundsTheChosenAmount() {
    InspectionResultResponse result =
        inspect(
            new InspectedItemRequest(
                shirts.getId(),
                InspectionGrade.B,
                2,
                RejectionReason.OTHER,
                "Original box missing",
                300_000L),
            accept(mugs, 1, InspectionGrade.A));

    assertThat(result.refundAmountPaise()).isEqualTo(300_000 + 124_900);
    assertThat(shirts.getRefundOverridePaise()).isEqualTo(300_000);
    assertThat(shirts.getRejectionReason()).isEqualTo(RejectionReason.OTHER);
    assertThat(mugs.getRefundOverridePaise()).isNull();
  }

  @Test
  void refundAmountAboveTheGradedRefundIsRefused() {
    assertThatThrownBy(
            () ->
                inspect(
                    new InspectedItemRequest(
                        shirts.getId(),
                        InspectionGrade.C,
                        2,
                        RejectionReason.USED_OR_WORN,
                        null,
                        200_000L),
                    accept(mugs, 1, InspectionGrade.A)))
        .isInstanceOfSatisfying(
            InvalidInspectionException.class,
            ex ->
                assertThat(ex.getProblems())
                    .containsExactly(
                        shirts.getId() + ": The refund can be at most ₹1,899 for grade C."));
  }

  @Test
  void refundAmountWithoutAReasonIsRefused() {
    assertThatThrownBy(
            () ->
                inspect(
                    new InspectedItemRequest(
                        shirts.getId(), InspectionGrade.A, 2, null, null, 100_000L),
                    accept(mugs, 1, InspectionGrade.A)))
        .isInstanceOfSatisfying(
            InvalidInspectionException.class,
            ex ->
                assertThat(ex.getProblems())
                    .containsExactly(
                        shirts.getId() + ": Choose why the item wasn't fully accepted."));
  }

  @Test
  void refundAmountWithNothingAcceptedIsRefused() {
    assertThatThrownBy(
            () ->
                inspect(
                    new InspectedItemRequest(
                        shirts.getId(), null, 0, RejectionReason.USED_OR_WORN, null, 100_000L),
                    accept(mugs, 1, InspectionGrade.A)))
        .isInstanceOfSatisfying(
            InvalidInspectionException.class,
            ex ->
                assertThat(ex.getProblems())
                    .containsExactly(
                        shirts.getId() + ": A refund amount needs at least one accepted unit."));
  }

  @Test
  void refundAbove10000RupeesWaitsForFinanceAndIsNotPaid() {
    returnRequest = receivedReturn(line("Laptop", 1_000_001, 1));

    InspectionResultResponse result =
        inspect(accept(returnRequest.getItems().get(0), 1, InspectionGrade.A));

    assertThat(result.status()).isEqualTo(ReturnStatus.AWAITING_FINANCE_APPROVAL);
    assertThat(result.financeApprovalRequired()).isTrue();
    assertThat(result.refundStatus()).isNull();
    verify(refundPayoutService, never()).payOut(any());
  }

  @Test
  void refundOfExactly10000RupeesIsPaidWithoutFinance() {
    returnRequest = receivedReturn(line("Tablet", 1_000_000, 1));

    InspectionResultResponse result =
        inspect(accept(returnRequest.getItems().get(0), 1, InspectionGrade.A));

    assertThat(result.financeApprovalRequired()).isFalse();
    verify(refundPayoutService).payOut(returnRequest.getId());
  }

  @Test
  void acceptingNothingRejectsTheReturnWithTheGivenReason() {
    InspectionResultResponse result =
        inspect(
            new InspectedItemRequest(
                shirts.getId(), null, 0, RejectionReason.MISSING_PARTS_OR_TAGS, "No tags", null),
            new InspectedItemRequest(
                mugs.getId(), null, 0, RejectionReason.MISSING_PARTS_OR_TAGS, null, null));

    assertThat(result.status()).isEqualTo(ReturnStatus.REJECTED);
    assertThat(result.refundAmountPaise()).isZero();
    assertThat(returnRequest.getRejectionReason()).isEqualTo(RejectionReason.MISSING_PARTS_OR_TAGS);
    verify(refundPayoutService, never()).payOut(any());
  }

  @Test
  void everyInspectionProblemIsReportedTogether() {
    UUID strangerId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                inspect(
                    new InspectedItemRequest(shirts.getId(), null, 2, null, null, null),
                    new InspectedItemRequest(strangerId, InspectionGrade.A, 1, null, null, null)))
        .isInstanceOfSatisfying(
            InvalidInspectionException.class,
            ex ->
                assertThat(ex.getProblems())
                    .containsExactlyInAnyOrder(
                        shirts.getId() + ": Choose a condition grade.",
                        strangerId + ": This line is not part of the return.",
                        mugs.getId() + ": This line has not been inspected."));
    verify(returnRequestRepository, never()).saveAndFlush(any());
  }

  @Test
  void partialAcceptWithoutAReasonIsRefused() {
    assertThatThrownBy(
            () ->
                inspect(
                    new InspectedItemRequest(
                        shirts.getId(), InspectionGrade.A, 1, null, null, null),
                    accept(mugs, 1, InspectionGrade.A)))
        .isInstanceOfSatisfying(
            InvalidInspectionException.class,
            ex ->
                assertThat(ex.getProblems())
                    .containsExactly(
                        shirts.getId() + ": Choose why the item wasn't fully accepted."));
  }

  @Test
  void otherReasonWithoutANoteIsRefused() {
    assertThatThrownBy(
            () ->
                inspect(
                    new InspectedItemRequest(
                        shirts.getId(), InspectionGrade.A, 1, RejectionReason.OTHER, " ", null),
                    accept(mugs, 1, InspectionGrade.A)))
        .isInstanceOfSatisfying(
            InvalidInspectionException.class,
            ex ->
                assertThat(ex.getProblems())
                    .containsExactly(shirts.getId() + ": Add a note when the reason is Other."));
  }

  @Test
  void acceptingMoreUnitsThanReturnedIsRefused() {
    assertThatThrownBy(
            () -> inspect(accept(shirts, 3, InspectionGrade.A), accept(mugs, 1, InspectionGrade.A)))
        .isInstanceOfSatisfying(
            InvalidInspectionException.class,
            ex ->
                assertThat(ex.getProblems())
                    .containsExactly(
                        shirts.getId() + ": At most 2 units can be accepted for this line."));
  }

  @Test
  void sameLineInspectedTwiceIsRefused() {
    assertThatThrownBy(
            () ->
                inspect(
                    accept(shirts, 2, InspectionGrade.A),
                    accept(shirts, 2, InspectionGrade.A),
                    accept(mugs, 1, InspectionGrade.A)))
        .isInstanceOfSatisfying(
            InvalidInspectionException.class,
            ex ->
                assertThat(ex.getProblems())
                    .containsExactly(shirts.getId() + ": This line was inspected more than once."));
  }

  @Test
  void inspectingBeforeTheParcelIsReceivedIsRejectedAsIllegal() {
    returnRequest = approvedReturn(line("Linen shirt", 189_900, 1));

    assertThatThrownBy(() -> inspect(accept(returnRequest.getItems().get(0), 1, InspectionGrade.A)))
        .isInstanceOf(IllegalTransitionException.class);
  }

  @Test
  void rejectingTheReturnRecordsTheReasonAndCountsAsInspected() {
    InspectionResultResponse result =
        service.reject(
            RMA, new RejectReturnRequest(RejectionReason.USED_OR_WORN, "Worn soles"), AGENT);

    assertThat(result.status()).isEqualTo(ReturnStatus.REJECTED);
    assertThat(returnRequest.getRejectionReason()).isEqualTo(RejectionReason.USED_OR_WORN);
    assertThat(returnRequest.getRejectionNote()).isEqualTo("Worn soles");
    assertThat(returnRequest.getInspectedAt()).isEqualTo(NOW);
    assertThat(returnRequest.getRefundAmountPaise()).isZero();
    assertThat(shirts.getAcceptedQuantity()).isZero();
  }

  @Test
  void warehouseCannotRejectWithAFinanceOnlyReason() {
    assertThatThrownBy(
            () ->
                service.reject(
                    RMA, new RejectReturnRequest(RejectionReason.REFUND_DECLINED, null), AGENT))
        .isInstanceOf(InvalidInspectionException.class);
    assertThat(returnRequest.getStatus()).isEqualTo(ReturnStatus.RECEIVED);
  }

  @Test
  void rejectingForAnOtherReasonNeedsANote() {
    assertThatThrownBy(
            () -> service.reject(RMA, new RejectReturnRequest(RejectionReason.OTHER, ""), AGENT))
        .isInstanceOf(InvalidInspectionException.class);
  }

  @Test
  void photoOnTheReturnIsServedFromStorage() {
    UUID photoId = attachPhoto(shirts);

    PhotoContent photo = service.photo(RMA, photoId);

    assertThat(photo.contentType()).isEqualTo("image/jpeg");
    assertThat(photo.content()).isEqualTo(TestImages.JPEG);
  }

  @Test
  void photoFromAnotherReturnIsNotFound() {
    assertThatThrownBy(() -> service.photo(RMA, UUID.randomUUID()))
        .isInstanceOf(PhotoNotFoundException.class);
  }

  @Test
  void todaysCountStartsAtIndianMidnight() {
    Instant from = Instant.parse("2026-11-19T18:30:00Z");
    Instant to = Instant.parse("2026-11-20T18:30:00Z");
    when(returnRequestRepository.countByInspectedAtGreaterThanEqualAndInspectedAtLessThan(from, to))
        .thenReturn(27L);
    when(returnRequestRepository
            .countByInspectedByAndInspectedAtGreaterThanEqualAndInspectedAtLessThan(
                AGENT.id(), from, to))
        .thenReturn(9L);

    InspectionSummaryResponse summary = service.todaySummary(AGENT);

    assertThat(summary.date()).isEqualTo(LocalDate.of(2026, 11, 20));
    assertThat(summary.inspectedToday()).isEqualTo(27);
    assertThat(summary.inspectedByYouToday()).isEqualTo(9);
  }

  private InspectionResultResponse inspect(InspectedItemRequest... verdicts) {
    return service.completeInspection(RMA, new InspectionRequest(List.of(verdicts)), AGENT);
  }

  private static InspectedItemRequest accept(ReturnItem item, int quantity, InspectionGrade grade) {
    return new InspectedItemRequest(item.getId(), grade, quantity, null, null, null);
  }

  private UUID attachPhoto(ReturnItem item) {
    UUID photoId = UUID.randomUUID();
    String key = "returns/" + returnRequest.getId() + "/" + photoId + ".jpg";
    item.addPhoto(new ReturnPhoto(photoId, key, "image/jpeg", TestImages.JPEG.length, NOW));
    photoStorage.store(key, TestImages.JPEG);
    return photoId;
  }

  private ReturnRequest approvedReturn(OrderItem... orderItems) {
    CustomerOrder order =
        new CustomerOrder(
            UUID.randomUUID(), "RR-10482", "aditi.sharma@example.com", "Aditi Sharma", NOW);
    ReturnRequest created =
        new ReturnRequest(UUID.randomUUID(), RMA, order, RefundMethod.ORIGINAL_PAYMENT, false, NOW);
    for (OrderItem orderItem : orderItems) {
      order.addItem(orderItem);
      created.addItem(
          new ReturnItem(
              UUID.randomUUID(),
              orderItem,
              orderItem.getQuantity(),
              ReturnReason.CHANGED_MY_MIND,
              null));
    }
    created.moveTo(ReturnStatus.APPROVED, NOW, Role.SYSTEM, null);
    when(returnRequestRepository.findByRmaNumber(RMA)).thenReturn(Optional.of(created));
    returnRequest = created;
    return created;
  }

  private ReturnRequest receivedReturn(OrderItem... orderItems) {
    ReturnRequest created = approvedReturn(orderItems);
    created.moveTo(ReturnStatus.RECEIVED, NOW, Role.WAREHOUSE_AGENT, AGENT.id());
    return created;
  }

  private static OrderItem line(String productName, long unitPricePaise, int quantity) {
    return new OrderItem(
        UUID.randomUUID(),
        1,
        "SKU-" + productName,
        productName,
        null,
        ProductCategory.APPAREL,
        unitPricePaise,
        quantity,
        NOW);
  }
}
