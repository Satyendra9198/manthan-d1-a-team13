package com.returnright.service;

import com.returnright.model.domain.PhotoUpload;
import com.returnright.model.dto.request.SubmitReturnRequest;
import com.returnright.model.dto.response.ReturnCreatedResponse;
import java.util.List;

/** Creates a return when the customer submits the last wizard step. */
public interface ReturnSubmissionService {

  /**
   * Validates the items and photos, creates the return and stores its photos. Unflagged customers
   * are approved at once; flagged ones stay {@code REQUESTED} for review (assumption A-11).
   *
   * @throws com.returnright.exception.OrderNotFoundException if the order number and email do not
   *     match
   * @throws com.returnright.exception.ItemSelectionRejectedException if any item can't be returned
   *     as chosen
   * @throws com.returnright.exception.InvalidPhotosException if any photo breaks the upload rules
   */
  ReturnCreatedResponse submit(SubmitReturnRequest request, List<PhotoUpload> photos);
}
