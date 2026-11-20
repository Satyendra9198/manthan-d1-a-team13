package com.returnright.service;

import com.returnright.model.domain.Actor;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;

/**
 * The only way to change a return's status: validates the transition, applies side effects (label
 * on approval, closed time on terminal states) and writes the audit entry in the same transaction.
 */
public interface ReturnTransitionService {

  /** Audits the creation of a new, already saved return. */
  void recordCreation(ReturnRequest returnRequest, Actor actor, String details);

  ReturnStatus apply(ReturnRequest returnRequest, ReturnEvent event, Actor actor, String details);
}
