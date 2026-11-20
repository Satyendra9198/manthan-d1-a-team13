package com.returnright.service.impl;

import com.returnright.exception.ResourceNotFoundException;
import com.returnright.model.domain.Actor;
import com.returnright.model.dto.response.DashboardResponse;
import com.returnright.model.dto.response.ReturnResponse;
import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.ReturnEvent;
import com.returnright.repository.AnalyticsRepository;
import com.returnright.repository.ReturnRequestRepository;
import com.returnright.service.OperationsService;
import com.returnright.service.ReturnQueryService;
import com.returnright.service.ReturnTransitionService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OperationsServiceImpl implements OperationsService {

  private final ReturnRequestRepository returnRequestRepository;
  private final ReturnTransitionService transitionService;
  private final ReturnQueryService returnQueryService;
  private final AnalyticsRepository analyticsRepository;
  private final TransactionTemplate transactionTemplate;

  public OperationsServiceImpl(
      ReturnRequestRepository returnRequestRepository,
      ReturnTransitionService transitionService,
      ReturnQueryService returnQueryService,
      AnalyticsRepository analyticsRepository,
      TransactionTemplate transactionTemplate) {
    this.returnRequestRepository = returnRequestRepository;
    this.transitionService = transitionService;
    this.returnQueryService = returnQueryService;
    this.analyticsRepository = analyticsRepository;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public ReturnResponse approve(UUID returnId, Actor actor) {
    transactionTemplate.executeWithoutResult(
        status ->
            transitionService.apply(
                load(returnId), ReturnEvent.APPROVE, actor, "Approved after review"));
    return returnQueryService.get(returnId);
  }

  @Override
  public ReturnResponse reject(UUID returnId, String reason, Actor actor) {
    transactionTemplate.executeWithoutResult(
        status -> transitionService.apply(load(returnId), ReturnEvent.REJECT, actor, reason));
    return returnQueryService.get(returnId);
  }

  @Override
  public DashboardResponse dashboard() {
    return new DashboardResponse(
        analyticsRepository.returnRateByCategory(),
        analyticsRepository.topReasons(),
        analyticsRepository.averageCycleTimeHours(),
        analyticsRepository.flaggedCustomers());
  }

  private ReturnRequest load(UUID returnId) {
    return returnRequestRepository
        .findById(returnId)
        .orElseThrow(() -> new ResourceNotFoundException("Return " + returnId + " not found"));
  }
}
