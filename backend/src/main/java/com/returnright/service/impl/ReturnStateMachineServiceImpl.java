package com.returnright.service.impl;

import com.returnright.exception.IllegalTransitionException;
import com.returnright.exception.TransitionNotPermittedException;
import com.returnright.model.enums.CustomerTimelineStep;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import com.returnright.service.ReturnStateMachineService;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The transition table. A state with no entries is terminal. Validity of the event is checked
 * before the actor's role, so a terminal state always reports an illegal transition.
 */
@Service
public class ReturnStateMachineServiceImpl implements ReturnStateMachineService {

  private record Transition(ReturnStatus target, Set<Role> allowedRoles) {}

  private static final Map<ReturnStatus, Map<ReturnEvent, Transition>> TRANSITIONS =
      new EnumMap<>(ReturnStatus.class);

  static {
    allow(
        ReturnStatus.REQUESTED,
        ReturnEvent.APPROVE,
        ReturnStatus.APPROVED,
        Role.SYSTEM,
        Role.OPERATIONS_MANAGER);
    allow(
        ReturnStatus.REQUESTED, ReturnEvent.REJECT, ReturnStatus.REJECTED, Role.OPERATIONS_MANAGER);
    allow(ReturnStatus.REQUESTED, ReturnEvent.CANCEL, ReturnStatus.CANCELLED, Role.CUSTOMER);

    allow(
        ReturnStatus.APPROVED,
        ReturnEvent.CANCEL,
        ReturnStatus.CANCELLED,
        Role.CUSTOMER,
        Role.SYSTEM);
    allow(
        ReturnStatus.APPROVED,
        ReturnEvent.MARK_IN_TRANSIT,
        ReturnStatus.IN_TRANSIT,
        Role.CUSTOMER,
        Role.SYSTEM);
    allow(ReturnStatus.APPROVED, ReturnEvent.RECEIVE, ReturnStatus.RECEIVED, Role.WAREHOUSE_AGENT);

    allow(
        ReturnStatus.IN_TRANSIT, ReturnEvent.RECEIVE, ReturnStatus.RECEIVED, Role.WAREHOUSE_AGENT);

    allow(
        ReturnStatus.RECEIVED,
        ReturnEvent.COMPLETE_INSPECTION,
        ReturnStatus.INSPECTED,
        Role.WAREHOUSE_AGENT);
    allow(
        ReturnStatus.RECEIVED,
        ReturnEvent.REJECT_AFTER_INSPECTION,
        ReturnStatus.REJECTED,
        Role.WAREHOUSE_AGENT);

    allow(
        ReturnStatus.INSPECTED, ReturnEvent.START_REFUND, ReturnStatus.REFUND_PENDING, Role.SYSTEM);
    allow(
        ReturnStatus.INSPECTED,
        ReturnEvent.SEND_TO_FINANCE,
        ReturnStatus.AWAITING_FINANCE_APPROVAL,
        Role.SYSTEM);

    allow(
        ReturnStatus.AWAITING_FINANCE_APPROVAL,
        ReturnEvent.FINANCE_APPROVE,
        ReturnStatus.REFUND_PENDING,
        Role.FINANCE_REVIEWER);
    allow(
        ReturnStatus.AWAITING_FINANCE_APPROVAL,
        ReturnEvent.FINANCE_REJECT,
        ReturnStatus.REJECTED,
        Role.FINANCE_REVIEWER);

    allow(
        ReturnStatus.REFUND_PENDING,
        ReturnEvent.CONFIRM_REFUND,
        ReturnStatus.REFUNDED,
        Role.SYSTEM);
  }

  private static void allow(
      ReturnStatus from, ReturnEvent event, ReturnStatus to, Role first, Role... rest) {
    TRANSITIONS
        .computeIfAbsent(from, status -> new EnumMap<>(ReturnEvent.class))
        .put(event, new Transition(to, EnumSet.of(first, rest)));
  }

  @Override
  public ReturnStatus transition(ReturnStatus from, ReturnEvent event, Role actor) {
    Transition transition = TRANSITIONS.getOrDefault(from, Map.of()).get(event);
    if (transition == null) {
      throw new IllegalTransitionException(from, event);
    }
    if (!transition.allowedRoles().contains(actor)) {
      throw new TransitionNotPermittedException(event, actor);
    }
    return transition.target();
  }

  @Override
  public Set<ReturnEvent> allowedEvents(ReturnStatus from, Role actor) {
    return TRANSITIONS.getOrDefault(from, Map.of()).entrySet().stream()
        .filter(entry -> entry.getValue().allowedRoles().contains(actor))
        .map(Map.Entry::getKey)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(ReturnEvent.class)));
  }

  @Override
  public CustomerTimelineStep timelineStepFor(ReturnStatus status) {
    return switch (status) {
      case REQUESTED -> CustomerTimelineStep.REQUESTED;
      case APPROVED -> CustomerTimelineStep.APPROVED;
      case IN_TRANSIT -> CustomerTimelineStep.IN_TRANSIT;
      case RECEIVED -> CustomerTimelineStep.RECEIVED;
      case INSPECTED, AWAITING_FINANCE_APPROVAL, REFUND_PENDING -> CustomerTimelineStep.INSPECTED;
      case REFUNDED -> CustomerTimelineStep.REFUNDED;
      case REJECTED -> CustomerTimelineStep.REJECTED;
      case CANCELLED -> CustomerTimelineStep.CANCELLED;
    };
  }
}
