package com.returnright.service;

import com.returnright.model.domain.PhotoContent;
import com.returnright.model.domain.StaffActor;
import com.returnright.model.dto.request.InspectionRequest;
import com.returnright.model.dto.request.RejectReturnRequest;
import com.returnright.model.dto.response.InspectionResultResponse;
import com.returnright.model.dto.response.InspectionSummaryResponse;
import com.returnright.model.dto.response.WarehouseQueueItemResponse;
import com.returnright.model.dto.response.WarehouseReturnResponse;
import java.util.List;
import java.util.UUID;

/**
 * The warehouse agent's work: receive parcels, inspect them, and reject or accept. RMA lookups
 * throw {@link com.returnright.exception.ReturnNotFoundException} for unknown numbers, and actions
 * not valid in the current state throw {@link
 * com.returnright.exception.IllegalTransitionException}.
 */
public interface WarehouseReturnService {

  /** Received parcels waiting for inspection, oldest request first. */
  List<WarehouseQueueItemResponse> awaitingInspection();

  /** "Find return" after scanning the QR code or typing the RMA. */
  WarehouseReturnResponse findReturn(String rmaNumber, StaffActor agent);

  /** Scans the parcel in, from {@code APPROVED} or {@code IN_TRANSIT} (A-17). */
  WarehouseReturnResponse receive(String rmaNumber, StaffActor agent);

  /**
   * Saves the grade and accepted quantity of every line, then decides the refund (A-06): finance
   * review above ₹10,000 (A-08), otherwise the refund is paid at once. If nothing is accepted the
   * return is rejected (A-14).
   *
   * @throws com.returnright.exception.InvalidInspectionException listing every problem found
   */
  InspectionResultResponse completeInspection(
      String rmaNumber, InspectionRequest request, StaffActor agent);

  /**
   * Rejects the whole return after inspection with a reason shown to the customer.
   *
   * @throws com.returnright.exception.InvalidInspectionException if the reason is not one the
   *     warehouse may use, or {@code OTHER} has no note
   */
  InspectionResultResponse reject(String rmaNumber, RejectReturnRequest request, StaffActor agent);

  /**
   * @throws com.returnright.exception.PhotoNotFoundException if the photo isn't on this return
   */
  PhotoContent photo(String rmaNumber, UUID photoId);

  InspectionSummaryResponse todaySummary(StaffActor agent);
}
