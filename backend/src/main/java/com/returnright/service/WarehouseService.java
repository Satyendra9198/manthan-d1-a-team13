package com.returnright.service;

import com.returnright.model.domain.Actor;
import com.returnright.model.dto.request.InspectionRequest;
import com.returnright.model.dto.response.ReturnResponse;
import java.util.UUID;

public interface WarehouseService {

  /** Marks the parcel received. Scanning the same label twice is rejected. */
  ReturnResponse receive(String labelCode, Actor actor);

  /**
   * Records grades and accepted quantities, computes the refund, then routes the return to finance
   * (above 10,000 INR) or straight to the refund.
   */
  ReturnResponse inspect(UUID returnId, InspectionRequest request, Actor actor);
}
