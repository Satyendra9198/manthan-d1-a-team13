package com.returnright.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.returnright.exception.IllegalTransitionException;
import com.returnright.exception.TransitionNotPermittedException;
import com.returnright.model.enums.CustomerTimelineStep;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ReturnStateMachineServiceImplTest {

  private final ReturnStateMachineServiceImpl stateMachine = new ReturnStateMachineServiceImpl();

  @ParameterizedTest(name = "{0} --{1} by {2}--> {3}")
  @CsvSource({
    "REQUESTED,                 APPROVE,                 SYSTEM,             APPROVED",
    "REQUESTED,                 APPROVE,                 OPERATIONS_MANAGER, APPROVED",
    "REQUESTED,                 REJECT,                  OPERATIONS_MANAGER, REJECTED",
    "REQUESTED,                 CANCEL,                  CUSTOMER,           CANCELLED",
    "APPROVED,                  CANCEL,                  CUSTOMER,           CANCELLED",
    "APPROVED,                  CANCEL,                  SYSTEM,             CANCELLED",
    "APPROVED,                  MARK_IN_TRANSIT,         CUSTOMER,           IN_TRANSIT",
    "APPROVED,                  RECEIVE,                 WAREHOUSE_AGENT,    RECEIVED",
    "IN_TRANSIT,                RECEIVE,                 WAREHOUSE_AGENT,    RECEIVED",
    "RECEIVED,                  COMPLETE_INSPECTION,     WAREHOUSE_AGENT,    INSPECTED",
    "RECEIVED,                  REJECT_AFTER_INSPECTION, WAREHOUSE_AGENT,    REJECTED",
    "INSPECTED,                 START_REFUND,            SYSTEM,             REFUND_PENDING",
    "INSPECTED,                 SEND_TO_FINANCE,         SYSTEM,             AWAITING_FINANCE_APPROVAL",
    "AWAITING_FINANCE_APPROVAL, FINANCE_APPROVE,         FINANCE_REVIEWER,   REFUND_PENDING",
    "AWAITING_FINANCE_APPROVAL, FINANCE_REJECT,          FINANCE_REVIEWER,   REJECTED",
    "REFUND_PENDING,            CONFIRM_REFUND,          SYSTEM,             REFUNDED",
  })
  void allowedTransitionMovesToTheExpectedState(
      ReturnStatus from, ReturnEvent event, Role actor, ReturnStatus expected) {
    assertThat(stateMachine.transition(from, event, actor)).isEqualTo(expected);
  }

  @ParameterizedTest
  @EnumSource(
      value = ReturnStatus.class,
      names = {"REFUNDED", "REJECTED", "CANCELLED"})
  void terminalStateRejectsEveryEvent(ReturnStatus terminal) {
    for (ReturnEvent event : ReturnEvent.values()) {
      assertThatThrownBy(() -> stateMachine.transition(terminal, event, Role.SYSTEM))
          .isInstanceOf(IllegalTransitionException.class);
    }
  }

  @Test
  void warehouseScanOfCancelledReturnIsRejected() {
    assertThatThrownBy(
            () ->
                stateMachine.transition(
                    ReturnStatus.CANCELLED, ReturnEvent.RECEIVE, Role.WAREHOUSE_AGENT))
        .isInstanceOf(IllegalTransitionException.class);
  }

  @Test
  void secondQrScanOfReceivedReturnIsRejected() {
    assertThatThrownBy(
            () ->
                stateMachine.transition(
                    ReturnStatus.RECEIVED, ReturnEvent.RECEIVE, Role.WAREHOUSE_AGENT))
        .isInstanceOf(IllegalTransitionException.class);
  }

  @Test
  void reinspectingAnInspectedReturnIsRejected() {
    assertThatThrownBy(
            () ->
                stateMachine.transition(
                    ReturnStatus.INSPECTED, ReturnEvent.COMPLETE_INSPECTION, Role.WAREHOUSE_AGENT))
        .isInstanceOf(IllegalTransitionException.class);
  }

  @Test
  void customerCannotCancelAfterTheParcelHasShipped() {
    assertThatThrownBy(
            () ->
                stateMachine.transition(ReturnStatus.IN_TRANSIT, ReturnEvent.CANCEL, Role.CUSTOMER))
        .isInstanceOf(IllegalTransitionException.class);
  }

  @Test
  void refundCannotBypassPendingFinanceApproval() {
    assertThatThrownBy(
            () ->
                stateMachine.transition(
                    ReturnStatus.AWAITING_FINANCE_APPROVAL, ReturnEvent.START_REFUND, Role.SYSTEM))
        .isInstanceOf(IllegalTransitionException.class);
  }

  @Test
  void secondFinanceApprovalOfTheSameReturnIsRejected() {
    assertThatThrownBy(
            () ->
                stateMachine.transition(
                    ReturnStatus.REFUND_PENDING,
                    ReturnEvent.FINANCE_APPROVE,
                    Role.FINANCE_REVIEWER))
        .isInstanceOf(IllegalTransitionException.class);
  }

  @Test
  void customerCannotApproveTheirOwnReturn() {
    assertThatThrownBy(
            () ->
                stateMachine.transition(ReturnStatus.REQUESTED, ReturnEvent.APPROVE, Role.CUSTOMER))
        .isInstanceOf(TransitionNotPermittedException.class);
  }

  @Test
  void warehouseAgentCannotGiveFinanceApproval() {
    assertThatThrownBy(
            () ->
                stateMachine.transition(
                    ReturnStatus.AWAITING_FINANCE_APPROVAL,
                    ReturnEvent.FINANCE_APPROVE,
                    Role.WAREHOUSE_AGENT))
        .isInstanceOf(TransitionNotPermittedException.class);
  }

  @Test
  void financeReviewerCannotInspectItems() {
    assertThatThrownBy(
            () ->
                stateMachine.transition(
                    ReturnStatus.RECEIVED, ReturnEvent.COMPLETE_INSPECTION, Role.FINANCE_REVIEWER))
        .isInstanceOf(TransitionNotPermittedException.class);
  }

  @Test
  void onlyTheSystemCanConfirmARefund() {
    assertThatThrownBy(
            () ->
                stateMachine.transition(
                    ReturnStatus.REFUND_PENDING, ReturnEvent.CONFIRM_REFUND, Role.FINANCE_REVIEWER))
        .isInstanceOf(TransitionNotPermittedException.class);
  }

  @Test
  void allowedEventsForWarehouseAgentOnReceivedReturnAreInspectionOutcomes() {
    assertThat(stateMachine.allowedEvents(ReturnStatus.RECEIVED, Role.WAREHOUSE_AGENT))
        .containsExactlyInAnyOrder(
            ReturnEvent.COMPLETE_INSPECTION, ReturnEvent.REJECT_AFTER_INSPECTION);
  }

  @Test
  void allowedEventsForCustomerOnApprovedReturnAreCancelAndShip() {
    assertThat(stateMachine.allowedEvents(ReturnStatus.APPROVED, Role.CUSTOMER))
        .containsExactlyInAnyOrder(ReturnEvent.CANCEL, ReturnEvent.MARK_IN_TRANSIT);
  }

  @Test
  void terminalStateHasNoAllowedEvents() {
    assertThat(stateMachine.allowedEvents(ReturnStatus.REFUNDED, Role.SYSTEM)).isEmpty();
  }

  @ParameterizedTest(name = "{0} is shown as {1}")
  @CsvSource({
    "REQUESTED,                 REQUESTED",
    "APPROVED,                  APPROVED",
    "IN_TRANSIT,                IN_TRANSIT",
    "RECEIVED,                  RECEIVED",
    "INSPECTED,                 INSPECTED",
    "AWAITING_FINANCE_APPROVAL, INSPECTED",
    "REFUND_PENDING,            INSPECTED",
    "REFUNDED,                  REFUNDED",
    "REJECTED,                  REJECTED",
    "CANCELLED,                 CANCELLED",
  })
  void internalStateMapsToCustomerTimelineStep(ReturnStatus status, CustomerTimelineStep step) {
    assertThat(stateMachine.timelineStepFor(status)).isEqualTo(step);
  }

  @Test
  void everyInternalStateHasATimelineStep() {
    for (ReturnStatus status : EnumSet.allOf(ReturnStatus.class)) {
      assertThat(stateMachine.timelineStepFor(status)).isNotNull();
    }
  }
}
