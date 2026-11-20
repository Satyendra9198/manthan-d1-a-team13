package com.returnright.controller;

import com.returnright.model.domain.PhotoContent;
import com.returnright.model.domain.StaffActor;
import com.returnright.model.dto.request.InspectionRequest;
import com.returnright.model.dto.request.RejectReturnRequest;
import com.returnright.model.dto.response.InspectionResultResponse;
import com.returnright.model.dto.response.InspectionSummaryResponse;
import com.returnright.model.dto.response.WarehouseQueueItemResponse;
import com.returnright.model.dto.response.WarehouseReturnResponse;
import com.returnright.service.WarehouseReturnService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Figma "Agent – Inspection" and "Agent – Inspection dialogs". Requires a {@code WAREHOUSE_AGENT}
 * token. Actions not valid in the return's current state answer 409 {@code ILLEGAL_TRANSITION}.
 */
@RestController
@RequestMapping("/api/v1/warehouse")
public class WarehouseReturnController {

  private final WarehouseReturnService warehouseReturnService;

  public WarehouseReturnController(WarehouseReturnService warehouseReturnService) {
    this.warehouseReturnService = warehouseReturnService;
  }

  /** Received parcels waiting for inspection. */
  @GetMapping("/returns/queue")
  public List<WarehouseQueueItemResponse> queue() {
    return warehouseReturnService.awaitingInspection();
  }

  /** "Find return": after scanning the label QR code or typing the RMA. */
  @GetMapping("/returns/{rmaNumber}")
  public WarehouseReturnResponse findReturn(
      @PathVariable String rmaNumber, @AuthenticationPrincipal Jwt token) {
    return warehouseReturnService.findReturn(rmaNumber, StaffActor.from(token));
  }

  /** Scans the parcel in at the warehouse. */
  @PostMapping("/returns/{rmaNumber}/receive")
  public WarehouseReturnResponse receive(
      @PathVariable String rmaNumber, @AuthenticationPrincipal Jwt token) {
    return warehouseReturnService.receive(rmaNumber, StaffActor.from(token));
  }

  /**
   * Grades every line and accepts all or part of it ("Partially accept").
   *
   * @return 200 with the outcome, 409 if not yet received or already inspected, 422 if a line is
   *     missing or inconsistent
   */
  @PostMapping("/returns/{rmaNumber}/inspection")
  public InspectionResultResponse completeInspection(
      @PathVariable String rmaNumber,
      @Valid @RequestBody InspectionRequest request,
      @AuthenticationPrincipal Jwt token) {
    return warehouseReturnService.completeInspection(rmaNumber, request, StaffActor.from(token));
  }

  /** The "Reject return" dialog. */
  @PostMapping("/returns/{rmaNumber}/reject")
  public InspectionResultResponse reject(
      @PathVariable String rmaNumber,
      @Valid @RequestBody RejectReturnRequest request,
      @AuthenticationPrincipal Jwt token) {
    return warehouseReturnService.reject(rmaNumber, request, StaffActor.from(token));
  }

  /** A customer photo, for the inspection screen. */
  @GetMapping("/returns/{rmaNumber}/photos/{photoId}")
  public ResponseEntity<byte[]> photo(@PathVariable String rmaNumber, @PathVariable UUID photoId) {
    PhotoContent photo = warehouseReturnService.photo(rmaNumber, photoId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(photo.contentType()))
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(photo.content());
  }

  /** The "Today: 27 inspected" counter. */
  @GetMapping("/inspections/today")
  public InspectionSummaryResponse today(@AuthenticationPrincipal Jwt token) {
    return warehouseReturnService.todaySummary(StaffActor.from(token));
  }
}
