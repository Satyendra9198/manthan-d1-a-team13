package com.returnright.controller;

import com.returnright.controller.support.CurrentActor;
import com.returnright.model.dto.request.DecisionRequest;
import com.returnright.model.dto.response.DashboardResponse;
import com.returnright.model.dto.response.ReturnResponse;
import com.returnright.service.OperationsService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {

  private final OperationsService operationsService;

  public OperationsController(OperationsService operationsService) {
    this.operationsService = operationsService;
  }

  /** Flagged customer's request held in Requested → Approved (label issued). */
  @PostMapping("/returns/{returnId}/approve")
  public ReturnResponse approve(@PathVariable UUID returnId, Authentication authentication) {
    return operationsService.approve(returnId, CurrentActor.from(authentication));
  }

  @PostMapping("/returns/{returnId}/reject")
  public ReturnResponse reject(
      @PathVariable UUID returnId,
      @Valid @RequestBody DecisionRequest request,
      Authentication authentication) {
    return operationsService.reject(returnId, request.reason(), CurrentActor.from(authentication));
  }

  /** Return rate by category, top reasons, average cycle time and flagged customers. */
  @GetMapping("/dashboard")
  public DashboardResponse dashboard() {
    return operationsService.dashboard();
  }
}
