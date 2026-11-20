package com.returnright.returns;

import com.returnright.auth.Role;
import java.util.Set;

/** The single place that decides whether a return may move from one state to another. */
public class ReturnStateMachine {

  /**
   * Applies an event and returns the next state.
   *
   * @throws IllegalTransitionException if the event is not valid from {@code from}
   * @throws TransitionNotPermittedException if {@code actor} may not trigger the event
   */
  public ReturnStatus transition(ReturnStatus from, ReturnEvent event, Role actor) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  /** Events the actor may trigger from the given state, for building UI actions. */
  public Set<ReturnEvent> allowedEvents(ReturnStatus from, Role actor) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  /** Maps an internal state onto the customer-facing timeline. */
  public CustomerTimelineStep timelineStepFor(ReturnStatus status) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
