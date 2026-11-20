package com.returnright.controller;

import com.returnright.model.dto.response.ReturnResponse;
import com.returnright.model.dto.response.ReturnSummaryResponse;
import com.returnright.model.enums.ReturnStatus;
import com.returnright.service.ReturnQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only return views shared by all staff roles (work queues and detail screens). */
@RestController
@RequestMapping("/api/v1/returns")
public class ReturnQueryController {

  private final ReturnQueryService returnQueryService;

  public ReturnQueryController(ReturnQueryService returnQueryService) {
    this.returnQueryService = returnQueryService;
  }

  /**
   * Examples: {@code ?status=RECEIVED} (warehouse queue), {@code ?status=AWAITING_FINANCE_APPROVAL}
   * (finance queue), {@code ?status=REQUESTED&flagged=true} (manager review queue).
   */
  @GetMapping
  public List<ReturnSummaryResponse> list(
      @RequestParam(required = false) ReturnStatus status,
      @RequestParam(defaultValue = "false") boolean flagged) {
    return returnQueryService.list(status, flagged);
  }

  @GetMapping("/{returnId}")
  public ReturnResponse get(@PathVariable UUID returnId) {
    return returnQueryService.get(returnId);
  }
}
