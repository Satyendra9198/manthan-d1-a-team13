package com.returnright.service.impl;

import com.returnright.model.enums.CustomerTimelineStep;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.model.enums.Role;
import com.returnright.service.ReturnStateMachineService;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ReturnStateMachineServiceImpl implements ReturnStateMachineService {

  @Override
  public ReturnStatus transition(ReturnStatus from, ReturnEvent event, Role actor) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Set<ReturnEvent> allowedEvents(ReturnStatus from, Role actor) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public CustomerTimelineStep timelineStepFor(ReturnStatus status) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
