package com.returnright.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.returnright.exception.IllegalTransitionException;
import com.returnright.exception.ReturnNotFoundException;
import com.returnright.exception.TransitionNotPermittedException;
import com.returnright.model.domain.StaffActor;
import com.returnright.model.dto.response.FinanceQueueItemResponse;
import com.returnright.model.dto.response.FlaggedReturnResponse;
import com.returnright.model.dto.response.ReviewDecisionResponse;
import com.returnright.model.entity.CustomerOrder;
import com.returnright.model.entity.OrderItem;
import com.returnright.model.entity.Refund;
import com.returnright.model.entity.ReturnItem;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.entity.StaffUser;
import com.returnright.model.enums.InspectionGrade;
import com.returnright.model.enums.ProductCategory;
import com.returnright.model.enums.RefundMethod;
import com.returnright.model.enums.RefundStatus;
import com.returnright.model.enums.RejectionReason;
import com.returnright.model.enums.ReturnReason;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import com.returnright.repository.RefundRepository;
import com.returnright.repository.ReturnRequestRepository;
import com.returnright.repository.StaffUserRepository;
import com.returnright.service.RefundPayoutService;
import com.returnright.support.NoOpTransactionManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReviewServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-11-20T06:30:00Z");
  private static final String RMA = "RMA-2026-00731";
  private static final String EMAIL = "meera.iyer@example.com";
  private static final StaffActor FINANCE =
      new StaffActor(UUID.randomUUID(), Role.FINANCE_REVIEWER);
  private static final StaffActor OPERATIONS =
      new StaffActor(UUID.randomUUID(), Role.OPERATIONS_MANAGER);

  private final ReturnRequestRepository returnRequestRepository =
      mock(ReturnRequestRepository.class);
  private final RefundPayoutService refundPayoutService = mock(RefundPayoutService.class);
  private final StaffUserRepository staffUserRepository = mock(StaffUserRepository.class);
  private final RefundRepository refundRepository = mock(RefundRepository.class);
  private static final UUID AGENT_ID = UUID.randomUUID();

  private ReviewServiceImpl service;
  private ReturnRequest returnRequest;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service =
        new ReviewServiceImpl(
            returnRequestRepository,
            staffUserRepository,
            refundRepository,
            new ReturnTransitions(new ReturnStateMachineServiceImpl(), clock),
            refundPayoutService,
            NoOpTransactionManager.template(),
            clock);
    returnRequest = newReturn(5_499_900);
    when(returnRequestRepository.findByRmaNumber(anyString())).thenReturn(Optional.empty());
    when(returnRequestRepository.findByRmaNumber(RMA)).thenReturn(Optional.of(returnRequest));
    when(returnRequestRepository.saveAndFlush(any(ReturnRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void financeApprovalPaysTheRefundAtOnce() {
    awaitFinance();
    when(refundPayoutService.payOut(returnRequest.getId()))
        .thenAnswer(
            invocation -> {
              returnRequest.moveTo(ReturnStatus.REFUNDED, NOW, Role.SYSTEM, null);
              return Optional.of(RefundStatus.SUCCEEDED);
            });

    when(refundRepository.findByReturnId(returnRequest.getId()))
        .thenReturn(
            Optional.of(
                new Refund(
                    UUID.randomUUID(),
                    returnRequest.getId(),
                    5_499_900,
                    RefundMethod.ORIGINAL_PAYMENT,
                    "return-" + returnRequest.getId(),
                    RefundStatus.SUCCEEDED,
                    "MOCK-TXN-000001",
                    null)));

    ReviewDecisionResponse response = service.approveRefund(RMA, FINANCE);

    assertThat(response.status()).isEqualTo(ReturnStatus.REFUNDED);
    assertThat(response.refundStatus()).isEqualTo(RefundStatus.SUCCEEDED);
    assertThat(response.refundAmountPaise()).isEqualTo(5_499_900);
    assertThat(response.refundReference()).isEqualTo("MOCK-TXN-000001");
    assertThat(response.decidedAt()).isEqualTo(NOW);
    assertThat(returnRequest.getStatusHistory())
        .anySatisfy(
            change -> {
              assertThat(change.getStatus()).isEqualTo(ReturnStatus.REFUND_PENDING);
              assertThat(change.getActorId()).isEqualTo(FINANCE.id());
            });
  }

  @Test
  void financeDeclineRejectsTheReturnWithTheNote() {
    awaitFinance();

    ReviewDecisionResponse response =
        service.declineRefund(RMA, "  Serial number mismatch  ", FINANCE);

    assertThat(response.status()).isEqualTo(ReturnStatus.REJECTED);
    assertThat(returnRequest.getRejectionReason()).isEqualTo(RejectionReason.REFUND_DECLINED);
    assertThat(returnRequest.getRejectionNote()).isEqualTo("Serial number mismatch");
    verify(refundPayoutService, never()).payOut(any());
  }

  @Test
  void secondFinanceApprovalIsRejectedAsIllegal() {
    awaitFinance();
    returnRequest.moveTo(ReturnStatus.REFUND_PENDING, NOW, Role.FINANCE_REVIEWER, FINANCE.id());

    assertThatThrownBy(() -> service.approveRefund(RMA, FINANCE))
        .isInstanceOf(IllegalTransitionException.class);
    verify(refundPayoutService, never()).payOut(any());
  }

  @Test
  void operationsManagerCannotApproveARefund() {
    awaitFinance();

    assertThatThrownBy(() -> service.approveRefund(RMA, OPERATIONS))
        .isInstanceOf(TransitionNotPermittedException.class);
  }

  @Test
  void financeQueueShowsWhoInspectedTheItemsAndTheRefundKey() {
    awaitFinance();
    when(staffUserRepository.findById(AGENT_ID))
        .thenReturn(
            Optional.of(
                new StaffUser(
                    AGENT_ID,
                    "riya@returnright.example",
                    "Riya Kapoor",
                    Role.WAREHOUSE_AGENT,
                    "x",
                    true)));
    when(returnRequestRepository.findByStatusOrderByCreatedAtAsc(
            ReturnStatus.AWAITING_FINANCE_APPROVAL))
        .thenReturn(List.of(returnRequest));

    FinanceQueueItemResponse item = service.refundsAwaitingFinance().get(0);

    assertThat(item.inspectedByName()).isEqualTo("Riya Kapoor");
    assertThat(item.itemNames()).containsExactly("Laptop 14\"");
    assertThat(item.grade()).isEqualTo(InspectionGrade.A);
    assertThat(item.partiallyAccepted()).isFalse();
    assertThat(item.customerFlagged()).isTrue();
    assertThat(item.refundKey()).isEqualTo("return-" + returnRequest.getId());
  }

  @Test
  void financeQueueShowsTheRefundAndWhatWasRequested() {
    awaitFinance();
    when(returnRequestRepository.findByStatusOrderByCreatedAtAsc(
            ReturnStatus.AWAITING_FINANCE_APPROVAL))
        .thenReturn(List.of(returnRequest));

    List<FinanceQueueItemResponse> queue = service.refundsAwaitingFinance();

    assertThat(queue)
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.rmaNumber()).isEqualTo(RMA);
              assertThat(item.refundAmountPaise()).isEqualTo(5_499_900);
              assertThat(item.requestedAmountPaise()).isEqualTo(5_499_900);
            });
  }

  @Test
  void approvingAFlaggedReturnMakesTheLabelAvailable() {
    ReviewDecisionResponse response = service.approveReturn(RMA, OPERATIONS);

    assertThat(response.status()).isEqualTo(ReturnStatus.APPROVED);
    assertThat(returnRequest.getApprovedAt()).isEqualTo(NOW);
  }

  @Test
  void decliningAFlaggedReturnRejectsIt() {
    ReviewDecisionResponse response =
        service.declineReturn(RMA, "Repeated returns of worn items", OPERATIONS);

    assertThat(response.status()).isEqualTo(ReturnStatus.REJECTED);
    assertThat(returnRequest.getRejectionReason()).isEqualTo(RejectionReason.REQUEST_DECLINED);
  }

  @Test
  void flaggedListShowsHowOftenTheCustomerReturned() {
    when(returnRequestRepository.findByStatusOrderByCreatedAtAsc(ReturnStatus.REQUESTED))
        .thenReturn(List.of(returnRequest));
    when(returnRequestRepository.findRequestTimes(
            eq(EMAIL), eq(ReturnStatus.CANCELLED), eq(NOW.minus(Duration.ofDays(30)))))
        .thenReturn(List.of(NOW, NOW, NOW, NOW));

    List<FlaggedReturnResponse> flagged = service.flaggedReturns();

    assertThat(flagged)
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.requestsInLast30Days()).isEqualTo(4);
              assertThat(item.customerEmail()).isEqualTo(EMAIL);
              assertThat(item.estimatedRefundPaise()).isEqualTo(5_499_900);
            });
  }

  @Test
  void unknownRmaIsReportedAsNotFound() {
    assertThatThrownBy(() -> service.approveReturn("RMA-2026-99999", OPERATIONS))
        .isInstanceOf(ReturnNotFoundException.class);
  }

  private void awaitFinance() {
    returnRequest.moveTo(ReturnStatus.APPROVED, NOW, Role.SYSTEM, null);
    returnRequest.moveTo(ReturnStatus.RECEIVED, NOW, Role.WAREHOUSE_AGENT, null);
    returnRequest.getItems().get(0).recordInspection(InspectionGrade.A, 1, null, null);
    returnRequest.recordInspection(AGENT_ID, NOW, 5_499_900);
    returnRequest.moveTo(ReturnStatus.INSPECTED, NOW, Role.WAREHOUSE_AGENT, null);
    returnRequest.moveTo(ReturnStatus.AWAITING_FINANCE_APPROVAL, NOW, Role.SYSTEM, null);
  }

  private static ReturnRequest newReturn(long unitPricePaise) {
    CustomerOrder order =
        new CustomerOrder(UUID.randomUUID(), "RR-10780", EMAIL, "Meera Iyer", NOW);
    OrderItem laptop =
        new OrderItem(
            UUID.randomUUID(),
            1,
            "ELE-LAPTOP-14",
            "Laptop 14\"",
            null,
            ProductCategory.ELECTRONICS,
            unitPricePaise,
            1,
            NOW);
    order.addItem(laptop);
    ReturnRequest created =
        new ReturnRequest(UUID.randomUUID(), RMA, order, RefundMethod.ORIGINAL_PAYMENT, true, NOW);
    created.addItem(
        new ReturnItem(UUID.randomUUID(), laptop, 1, ReturnReason.CHANGED_MY_MIND, null));
    return created;
  }
}
