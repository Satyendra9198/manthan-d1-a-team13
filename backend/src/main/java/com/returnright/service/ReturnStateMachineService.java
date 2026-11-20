package com.returnright.service;

import com.returnright.exception.IllegalTransitionException;
import com.returnright.exception.TransitionNotPermittedException;
import com.returnright.model.enums.CustomerTimelineStep;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import java.util.Set;

/** The single place that decides whether a return may move from one state to another. */
public interface ReturnStateMachineService {

  /**
   * Applies an event and returns the next state.
   *
   * @throws IllegalTransitionException if the event is not valid from {@code from}
   * @throws TransitionNotPermittedException if {@code actor} may not trigger the event
   */
  ReturnStatus transition(ReturnStatus from, ReturnEvent event, Role actor);

  /** Events the actor may trigger from the given state, for building UI actions. */
  Set<ReturnEvent> allowedEvents(ReturnStatus from, Role actor);

  /** Maps an internal state onto the customer-facing timeline. */
  CustomerTimelineStep timelineStepFor(ReturnStatus status);
}
