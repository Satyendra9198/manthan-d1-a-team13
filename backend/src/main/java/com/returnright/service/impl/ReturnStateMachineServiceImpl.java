package com.returnright.service.impl;

import static com.returnright.model.enums.ReturnEvent.APPROVE;
import static com.returnright.model.enums.ReturnEvent.CANCEL;
import static com.returnright.model.enums.ReturnEvent.COMPLETE_INSPECTION;
import static com.returnright.model.enums.ReturnEvent.CONFIRM_REFUND;
import static com.returnright.model.enums.ReturnEvent.FINANCE_APPROVE;
import static com.returnright.model.enums.ReturnEvent.FINANCE_REJECT;
import static com.returnright.model.enums.ReturnEvent.MARK_IN_TRANSIT;
import static com.returnright.model.enums.ReturnEvent.RECEIVE;
import static com.returnright.model.enums.ReturnEvent.REJECT;
import static com.returnright.model.enums.ReturnEvent.REJECT_AFTER_INSPECTION;
import static com.returnright.model.enums.ReturnEvent.SEND_TO_FINANCE;
import static com.returnright.model.enums.ReturnEvent.START_REFUND;
import static com.returnright.model.enums.ReturnStatus.APPROVED;
import static com.returnright.model.enums.ReturnStatus.AWAITING_FINANCE_APPROVAL;
import static com.returnright.model.enums.ReturnStatus.CANCELLED;
import static com.returnright.model.enums.ReturnStatus.INSPECTED;
import static com.returnright.model.enums.ReturnStatus.IN_TRANSIT;
import static com.returnright.model.enums.ReturnStatus.RECEIVED;
import static com.returnright.model.enums.ReturnStatus.REFUNDED;
import static com.returnright.model.enums.ReturnStatus.REFUND_PENDING;
import static com.returnright.model.enums.ReturnStatus.REJECTED;
import static com.returnright.model.enums.ReturnStatus.REQUESTED;
import static com.returnright.model.enums.Role.CUSTOMER;
import static com.returnright.model.enums.Role.FINANCE_REVIEWER;
import static com.returnright.model.enums.Role.OPERATIONS_MANAGER;
import static com.returnright.model.enums.Role.SYSTEM;
import static com.returnright.model.enums.Role.WAREHOUSE_AGENT;

import com.returnright.exception.IllegalTransitionException;
import com.returnright.exception.TransitionNotPermittedException;
import com.returnright.model.enums.CustomerTimelineStep;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import com.returnright.service.ReturnStateMachineService;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Transition table for returns. Any (state, event) pair not in the table is illegal. */
@Service
public class ReturnStateMachineServiceImpl implements ReturnStateMachineService {

  private record Key(ReturnStatus from, ReturnEvent event) {}

  private record Transition(ReturnStatus to, Set<Role> allowedRoles) {}

  private static final Map<Key, Transition> TRANSITIONS = new HashMap<>();

  static {
    allow(REQUESTED, APPROVE, APPROVED, SYSTEM, OPERATIONS_MANAGER);
    allow(REQUESTED, REJECT, REJECTED, SYSTEM, OPERATIONS_MANAGER);
    allow(REQUESTED, CANCEL, CANCELLED, CUSTOMER, SYSTEM);
    allow(APPROVED, CANCEL, CANCELLED, CUSTOMER, SYSTEM);
    allow(APPROVED, MARK_IN_TRANSIT, IN_TRANSIT, CUSTOMER, SYSTEM);
    allow(APPROVED, RECEIVE, RECEIVED, WAREHOUSE_AGENT);
    allow(IN_TRANSIT, RECEIVE, RECEIVED, WAREHOUSE_AGENT);
    allow(RECEIVED, COMPLETE_INSPECTION, INSPECTED, WAREHOUSE_AGENT);
    allow(RECEIVED, REJECT_AFTER_INSPECTION, REJECTED, WAREHOUSE_AGENT);
    allow(INSPECTED, START_REFUND, REFUND_PENDING, SYSTEM);
    allow(INSPECTED, SEND_TO_FINANCE, AWAITING_FINANCE_APPROVAL, SYSTEM);
    allow(AWAITING_FINANCE_APPROVAL, FINANCE_APPROVE, REFUND_PENDING, FINANCE_REVIEWER);
    allow(AWAITING_FINANCE_APPROVAL, FINANCE_REJECT, REJECTED, FINANCE_REVIEWER);
    allow(REFUND_PENDING, CONFIRM_REFUND, REFUNDED, SYSTEM);
  }

  private static void allow(ReturnStatus from, ReturnEvent event, ReturnStatus to, Role... roles) {
    TRANSITIONS.put(new Key(from, event), new Transition(to, EnumSet.copyOf(Set.of(roles))));
  }

  @Override
  public ReturnStatus transition(ReturnStatus from, ReturnEvent event, Role actor) {
    Transition transition = TRANSITIONS.get(new Key(from, event));
    if (transition == null) {
      throw new IllegalTransitionException(from, event);
    }
    if (!transition.allowedRoles().contains(actor)) {
      throw new TransitionNotPermittedException(event, actor);
    }
    return transition.to();
  }

  @Override
  public Set<ReturnEvent> allowedEvents(ReturnStatus from, Role actor) {
    return TRANSITIONS.entrySet().stream()
        .filter(entry -> entry.getKey().from() == from)
        .filter(entry -> entry.getValue().allowedRoles().contains(actor))
        .map(entry -> entry.getKey().event())
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
