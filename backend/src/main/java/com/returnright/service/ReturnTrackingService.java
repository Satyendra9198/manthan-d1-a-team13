package com.returnright.service;

import com.returnright.model.dto.request.TrackReturnRequest;
import com.returnright.model.dto.response.ReturnTrackingResponse;

/** The customer's view of a return: timeline, label, refund and rejection. */
public interface ReturnTrackingService {

  /**
   * @throws com.returnright.exception.ReturnNotFoundException if the RMA is unknown or the email
   *     does not match
   */
  ReturnTrackingResponse track(TrackReturnRequest request);
}
