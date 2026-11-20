package com.returnright.service.impl;

import com.returnright.model.domain.Actor;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.service.AuditService;
import com.returnright.service.ReturnStateMachineService;
import com.returnright.service.ReturnTransitionService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReturnTransitionServiceImpl implements ReturnTransitionService {

  private static final String CREATE_EVENT = "CREATE";
  private static final Set<ReturnStatus> TERMINAL =
      EnumSet.of(ReturnStatus.REFUNDED, ReturnStatus.REJECTED, ReturnStatus.CANCELLED);

  private final ReturnStateMachineService stateMachine;
  private final AuditService auditService;
  private final ReferenceCodeGenerator codeGenerator;
  private final Clock clock;
  private final Duration labelValidity;

  public ReturnTransitionServiceImpl(
      ReturnStateMachineService stateMachine,
      AuditService auditService,
      ReferenceCodeGenerator codeGenerator,
      Clock clock,
      @Value("${returnright.label.validity:P14D}") Duration labelValidity) {
    this.stateMachine = stateMachine;
    this.auditService = auditService;
    this.codeGenerator = codeGenerator;
    this.clock = clock;
    this.labelValidity = labelValidity;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordCreation(ReturnRequest returnRequest, Actor actor, String details) {
    auditService.record(
        returnRequest, CREATE_EVENT, null, returnRequest.getStatus(), actor, details);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public ReturnStatus apply(
      ReturnRequest returnRequest, ReturnEvent event, Actor actor, String details) {
    ReturnStatus from = returnRequest.getStatus();
    ReturnStatus to = stateMachine.transition(from, event, actor.role());
    Instant now = clock.instant();

    returnRequest.moveTo(to, now, TERMINAL.contains(to));
    if (event == ReturnEvent.APPROVE) {
      returnRequest.issueLabel(codeGenerator.labelCode(), now.plus(labelValidity));
    }
    auditService.record(returnRequest, event.name(), from, to, actor, details);
    return to;
  }
}
