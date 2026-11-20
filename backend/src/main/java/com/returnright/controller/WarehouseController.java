package com.returnright.controller;

import com.returnright.controller.support.CurrentActor;
import com.returnright.model.dto.request.InspectionRequest;
import com.returnright.model.dto.request.ScanLabelRequest;
import com.returnright.model.dto.response.ReturnResponse;
import com.returnright.service.WarehouseService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/warehouse")
public class WarehouseController {

  private final WarehouseService warehouseService;

  public WarehouseController(WarehouseService warehouseService) {
    this.warehouseService = warehouseService;
  }

  /** QR scan at the dock: Approved / In transit → Received. A second scan returns 409. */
  @PostMapping("/scan")
  public ReturnResponse scan(
      @Valid @RequestBody ScanLabelRequest request, Authentication authentication) {
    return warehouseService.receive(request.labelCode(), CurrentActor.from(authentication));
  }

  /**
   * Received → Inspected, then Awaiting finance approval (refund above 10,000 INR) or Refund
   * pending → Refunded. All lines rejected → Rejected.
   */
  @PostMapping("/returns/{returnId}/inspection")
  public ReturnResponse inspect(
      @PathVariable UUID returnId,
      @Valid @RequestBody InspectionRequest request,
      Authentication authentication) {
    return warehouseService.inspect(returnId, request, CurrentActor.from(authentication));
  }
}
