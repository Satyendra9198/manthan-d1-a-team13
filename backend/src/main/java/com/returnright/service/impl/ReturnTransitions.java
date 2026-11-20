package com.returnright.service.impl;

import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.Role;
import com.returnright.service.ReturnStateMachineService;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Applies events to returns through the state machine and records each change in the history. */
@Component
public class ReturnTransitions {

  private final ReturnStateMachineService returnStateMachineService;
  private final Clock clock;

  public ReturnTransitions(ReturnStateMachineService returnStateMachineService, Clock clock) {
    this.returnStateMachineService = returnStateMachineService;
    this.clock = clock;
  }

  /**
   * @param actorId the staff member, or {@code null} for the customer and the system
   * @throws com.returnright.exception.IllegalTransitionException if the event is not valid now
   * @throws com.returnright.exception.TransitionNotPermittedException if the role may not trigger
   *     it
   */
  public void apply(ReturnRequest returnRequest, ReturnEvent event, Role actorRole, UUID actorId) {
    returnRequest.moveTo(
        returnStateMachineService.transition(returnRequest.getStatus(), event, actorRole),
        clock.instant(),
        actorRole,
        actorId);
  }

  /** RMA numbers are matched upper-case without surrounding spaces. */
  public static String normaliseRmaNumber(String rmaNumber) {
    return rmaNumber.trim().toUpperCase(Locale.ROOT);
  }
}
