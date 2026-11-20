package com.returnright.exception;

import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.Role;

/** The event is valid from the current state, but the actor's role may not trigger it. */
public class TransitionNotPermittedException extends RuntimeException {

  public TransitionNotPermittedException(ReturnEvent event, Role role) {
    super("Role " + role + " may not trigger " + event);
  }
}
