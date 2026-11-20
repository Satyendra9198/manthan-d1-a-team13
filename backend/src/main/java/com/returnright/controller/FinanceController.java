package com.returnright.controller;

import com.returnright.controller.support.CurrentActor;
import com.returnright.model.dto.request.DecisionRequest;
import com.returnright.model.dto.response.ReconciliationResponse;
import com.returnright.model.dto.response.RefundResponse;
import com.returnright.model.dto.response.ReturnResponse;
import com.returnright.model.enums.RefundStatus;
import com.returnright.service.FinanceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {

  private final FinanceService financeService;

  public FinanceController(FinanceService financeService) {
    this.financeService = financeService;
  }

  /**
   * Awaiting finance approval → Refund pending → Refunded. The client generates one {@code
   * Idempotency-Key} per approve click and reuses it on retries; repeated calls never pay twice.
   */
  @PostMapping("/returns/{returnId}/approve")
  public ReturnResponse approve(
      @PathVariable UUID returnId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      Authentication authentication) {
    return financeService.approve(returnId, idempotencyKey, CurrentActor.from(authentication));
  }

  @PostMapping("/returns/{returnId}/reject")
  public ReturnResponse reject(
      @PathVariable UUID returnId,
      @Valid @RequestBody DecisionRequest request,
      Authentication authentication) {
    return financeService.reject(returnId, request.reason(), CurrentActor.from(authentication));
  }

  /** {@code ?status=UNKNOWN} lists refunds whose gateway outcome is not yet known. */
  @GetMapping("/refunds")
  public List<RefundResponse> listRefunds(@RequestParam(required = false) RefundStatus status) {
    return financeService.listRefunds(status);
  }

  /** Re-sends unresolved refunds to the gateway with their original keys. Safe to repeat. */
  @PostMapping("/refunds/reconcile")
  public ReconciliationResponse reconcile() {
    return financeService.reconcile();
  }
}
