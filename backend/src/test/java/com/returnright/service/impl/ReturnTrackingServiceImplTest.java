package com.returnright.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.returnright.exception.ReturnNotFoundException;
import com.returnright.model.dto.request.TrackReturnRequest;
import com.returnright.model.dto.response.ReturnTrackingResponse;
import com.returnright.model.dto.response.TimelineEntryResponse;
import com.returnright.model.entity.CustomerOrder;
import com.returnright.model.entity.OrderItem;
import com.returnright.model.entity.ReturnItem;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.CustomerRefundStatus;
import com.returnright.model.enums.CustomerTimelineStep;
import com.returnright.model.enums.InspectionGrade;
import com.returnright.model.enums.ProductCategory;
import com.returnright.model.enums.RefundMethod;
import com.returnright.model.enums.RejectionReason;
import com.returnright.model.enums.ReturnReason;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import com.returnright.model.enums.TimelineEntryState;
import com.returnright.repository.ReturnRequestRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReturnTrackingServiceImplTest {

  private static final String RMA = "RMA-2026-00731";
  private static final String EMAIL = "aditi.sharma@example.com";

  /** 17 Nov 2026, 10:00 IST. */
  private static final Instant REQUESTED_AT = Instant.parse("2026-11-17T04:30:00Z");

  private static final UUID AGENT_ID = UUID.randomUUID();

  private final ReturnRequestRepository returnRequestRepository =
      mock(ReturnRequestRepository.class);

  private final ReturnTrackingServiceImpl service =
      new ReturnTrackingServiceImpl(
          returnRequestRepository,
          new ReturnStateMachineServiceImpl(),
          new RefundCalculationServiceImpl());

  private ReturnRequest returnRequest;
  private ReturnItem shirtLine;

  @BeforeEach
  void setUp() {
    CustomerOrder order =
        new CustomerOrder(
            UUID.randomUUID(), "RR-10482", EMAIL, "Aditi Sharma", REQUESTED_AT.minus(days(10)));
    OrderItem shirts =
        new OrderItem(
            UUID.randomUUID(),
            1,
            "APP-LINEN-M-SAND",
            "Linen shirt",
            "M / Sand",
            ProductCategory.APPAREL,
            189_900,
            2,
            REQUESTED_AT.minus(days(3)));
    order.addItem(shirts);

    returnRequest =
        new ReturnRequest(
            UUID.randomUUID(), RMA, order, RefundMethod.ORIGINAL_PAYMENT, false, REQUESTED_AT);
    shirtLine = new ReturnItem(UUID.randomUUID(), shirts, 1, ReturnReason.TOO_SMALL, null);
    returnRequest.addItem(shirtLine);
    returnRequest.moveTo(ReturnStatus.APPROVED, REQUESTED_AT, Role.SYSTEM, null);

    when(returnRequestRepository.findByRmaNumberAndCustomerEmail(anyString(), anyString()))
        .thenReturn(Optional.empty());
    when(returnRequestRepository.findByRmaNumberAndCustomerEmail(RMA, EMAIL))
        .thenReturn(Optional.of(returnRequest));
  }

  @Test
  void approvedReturnShowsTheLabelValidFor14Days() {
    ReturnTrackingResponse response = track();

    assertThat(response.status()).isEqualTo(CustomerTimelineStep.APPROVED);
    assertThat(response.label().qrCodeValue()).isEqualTo(RMA);
    assertThat(response.label().dropOffBy()).isEqualTo(LocalDate.of(2026, 12, 1));
    assertThat(response.statusMessage()).contains("Drop off your parcel by 1 Dec 2026");
    assertThat(response.refund()).isNull();
    assertThat(response.rejection()).isNull();
  }

  @Test
  void approvedReturnTimelineShowsApprovedAsCurrentAndTheRestAsUpcoming() {
    assertThat(track().timeline())
        .extracting(TimelineEntryResponse::step, TimelineEntryResponse::state)
        .containsExactly(
            tuple(CustomerTimelineStep.REQUESTED, TimelineEntryState.COMPLETED),
            tuple(CustomerTimelineStep.APPROVED, TimelineEntryState.CURRENT),
            tuple(CustomerTimelineStep.IN_TRANSIT, TimelineEntryState.UPCOMING),
            tuple(CustomerTimelineStep.RECEIVED, TimelineEntryState.UPCOMING),
            tuple(CustomerTimelineStep.INSPECTED, TimelineEntryState.UPCOMING),
            tuple(CustomerTimelineStep.REFUNDED, TimelineEntryState.UPCOMING));
  }

  @Test
  void receivedWithoutAnInTransitScanLeavesInTransitOutOfTheTimeline() {
    returnRequest.moveTo(
        ReturnStatus.RECEIVED, REQUESTED_AT.plus(days(2)), Role.WAREHOUSE_AGENT, AGENT_ID);

    ReturnTrackingResponse response = track();

    assertThat(response.label()).isNull();
    assertThat(response.timeline())
        .extracting(TimelineEntryResponse::step)
        .containsExactly(
            CustomerTimelineStep.REQUESTED,
            CustomerTimelineStep.APPROVED,
            CustomerTimelineStep.RECEIVED,
            CustomerTimelineStep.INSPECTED,
            CustomerTimelineStep.REFUNDED);
  }

  @Test
  void gradeCRefundIsExplainedAsHalfPrice() {
    inspectAndRefund(InspectionGrade.C, 1, null);

    ReturnTrackingResponse response = track();

    assertThat(response.status()).isEqualTo(CustomerTimelineStep.REFUNDED);
    assertThat(response.statusMessage())
        .isEqualTo("₹949.50 refunded to your original payment method.");
    assertThat(response.refund().amountPaise()).isEqualTo(94_950);
    assertThat(response.refund().requestedAmountPaise()).isEqualTo(189_900);
    assertThat(response.refund().status()).isEqualTo(CustomerRefundStatus.REFUNDED);
    assertThat(response.refund().refundedAt()).isEqualTo(REQUESTED_AT.plus(days(4)));
    assertThat(response.refund().explanation())
        .containsExactly(
            "Linen shirt: graded C at inspection (visible wear or damage), so it is refunded at"
                + " 50%.");
    assertThat(response.items().get(0).refundPaise()).isEqualTo(94_950);
  }

  @Test
  void refundedReturnTimelineIsCompleteWithNothingUpcoming() {
    inspectAndRefund(InspectionGrade.A, 1, null);

    assertThat(track().timeline())
        .extracting(TimelineEntryResponse::state)
        .containsOnly(TimelineEntryState.COMPLETED);
  }

  @Test
  void fullRefundNeedsNoExplanation() {
    inspectAndRefund(InspectionGrade.B, 1, null);

    assertThat(track().refund().explanation()).isEmpty();
  }

  @Test
  void refundAwaitingPayoutIsShownAsProcessing() {
    shirtLine.recordInspection(InspectionGrade.A, 1, null, null);
    returnRequest.recordInspection(AGENT_ID, REQUESTED_AT.plus(days(3)), 189_900);
    returnRequest.moveTo(
        ReturnStatus.REFUND_PENDING, REQUESTED_AT.plus(days(3)), Role.SYSTEM, null);

    ReturnTrackingResponse response = track();

    assertThat(response.status()).isEqualTo(CustomerTimelineStep.INSPECTED);
    assertThat(response.refund().status()).isEqualTo(CustomerRefundStatus.PROCESSING);
    assertThat(response.refund().refundedAt()).isNull();
    assertThat(response.statusMessage()).contains("₹1,899 is being processed");
  }

  @Test
  void rejectedReturnShowsTheReasonAndEndsTheTimelineWithRejected() {
    returnRequest.moveTo(
        ReturnStatus.RECEIVED, REQUESTED_AT.plus(days(2)), Role.WAREHOUSE_AGENT, AGENT_ID);
    returnRequest.recordRejection(RejectionReason.USED_OR_WORN, "Deodorant marks on collar");
    returnRequest.moveTo(
        ReturnStatus.REJECTED, REQUESTED_AT.plus(days(3)), Role.WAREHOUSE_AGENT, AGENT_ID);

    ReturnTrackingResponse response = track();

    assertThat(response.rejection().reason()).isEqualTo(RejectionReason.USED_OR_WORN);
    assertThat(response.rejection().message()).isEqualTo("The item shows signs of use or wear.");
    assertThat(response.rejection().note()).isNull();
    assertThat(response.refund()).isNull();
    assertThat(response.timeline())
        .extracting(TimelineEntryResponse::step)
        .endsWith(CustomerTimelineStep.RECEIVED, CustomerTimelineStep.REJECTED);
  }

  @Test
  void rejectionForAnOtherReasonShowsTheTeamsNote() {
    returnRequest.recordRejection(RejectionReason.OTHER, "Serial number doesn't match the order");
    returnRequest.moveTo(ReturnStatus.REJECTED, REQUESTED_AT.plus(days(1)), Role.SYSTEM, null);

    assertThat(track().rejection().note()).isEqualTo("Serial number doesn't match the order");
  }

  @Test
  void rmaAndEmailAreMatchedIgnoringCaseAndSpaces() {
    ReturnTrackingResponse response =
        service.track(new TrackReturnRequest(" rma-2026-00731 ", " Aditi.Sharma@Example.com "));

    assertThat(response.rmaNumber()).isEqualTo(RMA);
  }

  @Test
  void wrongEmailIsReportedAsReturnNotFound() {
    assertThatThrownBy(() -> service.track(new TrackReturnRequest(RMA, "someone@example.com")))
        .isInstanceOf(ReturnNotFoundException.class)
        .hasMessageContaining("couldn't find that return");
  }

  private void inspectAndRefund(
      InspectionGrade grade, int acceptedQuantity, RejectionReason partialReason) {
    returnRequest.moveTo(
        ReturnStatus.RECEIVED, REQUESTED_AT.plus(days(2)), Role.WAREHOUSE_AGENT, AGENT_ID);
    shirtLine.recordInspection(grade, acceptedQuantity, partialReason, null);
    long refund = 189_900L * acceptedQuantity * grade.getRefundPercent() / 100;
    returnRequest.recordInspection(AGENT_ID, REQUESTED_AT.plus(days(3)), refund);
    returnRequest.moveTo(
        ReturnStatus.INSPECTED, REQUESTED_AT.plus(days(3)), Role.WAREHOUSE_AGENT, AGENT_ID);
    returnRequest.moveTo(
        ReturnStatus.REFUND_PENDING, REQUESTED_AT.plus(days(3)), Role.SYSTEM, null);
    returnRequest.moveTo(ReturnStatus.REFUNDED, REQUESTED_AT.plus(days(4)), Role.SYSTEM, null);
  }

  private ReturnTrackingResponse track() {
    return service.track(new TrackReturnRequest(RMA, EMAIL));
  }

  private static Duration days(int days) {
    return Duration.ofDays(days);
  }
}
