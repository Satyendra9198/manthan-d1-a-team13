package com.returnright.exception;

import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;

/** The event is not valid from the return's current state. */
public class IllegalTransitionException extends RuntimeException {

  public IllegalTransitionException(ReturnStatus from, ReturnEvent event) {
    super("Event " + event + " is not allowed from state " + from);
  }
}
